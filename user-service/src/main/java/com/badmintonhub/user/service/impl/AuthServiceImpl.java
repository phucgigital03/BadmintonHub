package com.badmintonhub.user.service.impl;

import com.badmintonhub.common.exception.ApiException;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.InvalidTokenException;
import com.badmintonhub.common.exception.UnauthorizedException;
import com.badmintonhub.user.dto.request.GoogleLoginRequest;
import com.badmintonhub.user.dto.request.LoginRequest;
import com.badmintonhub.user.dto.request.RegisterRequest;
import com.badmintonhub.user.dto.request.ResetPasswordRequest;
import com.badmintonhub.user.dto.response.AuthResponse;
import com.badmintonhub.user.dto.response.UserResponse;
import com.badmintonhub.user.entity.Role;
import com.badmintonhub.user.entity.User;
import com.badmintonhub.user.entity.enums.AuthProvider;
import com.badmintonhub.user.entity.enums.RoleName;
import com.badmintonhub.user.repository.RoleRepository;
import com.badmintonhub.user.repository.UserRepository;
import com.badmintonhub.user.security.JwtTokenProvider;
import com.badmintonhub.user.service.AuthService;
import com.badmintonhub.user.service.EmailService;
import com.badmintonhub.user.service.GoogleTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service("authService")
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_REGISTRATIONS_PER_IP = 5;
    private static final Duration REGISTER_RATE_WINDOW = Duration.ofHours(1);
    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest req, String clientIp) {
        enforceRegisterRateLimit(clientIp);

        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("EMAIL_EXISTS", "Email đã được đăng ký");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(RoleName.USER);
                    return roleRepository.save(r);
                });

        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("EMAIL_EXISTS", "Email đã được đăng ký");
        }

        String verifyToken = UUID.randomUUID().toString();
        storeEmailVerifyToken(verifyToken, user.getId());
        emailService.sendVerificationEmail(user.getEmail(), verifyToken);

        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String key = "email:verify:" + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new InvalidTokenException("INVALID_VERIFY_TOKEN",
                    "Token xác thực không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidTokenException("INVALID_VERIFY_TOKEN",
                        "Token xác thực không hợp lệ hoặc đã hết hạn"));
        user.setEmailVerified(true);
        userRepository.save(user);

        redisTemplate.delete(key); // single-use
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email()).orElse(null);
        if (user == null
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }

        Set<String> authorities = toAuthorities(user);
        JwtTokenProvider.AccessToken accessToken =
                jwtTokenProvider.generateAccessToken(user.getId(), authorities, user.isEmailVerified());

        String rawRefresh = jwtTokenProvider.generateRefreshTokenValue(user.getId());
        user.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        userRepository.save(user);

        AuthResponse auth = buildAuthResponse(accessToken, user, authorities);
        return new LoginResult(auth, rawRefresh);
    }

    @Override
    @Transactional
    public LoginResult googleLogin(GoogleLoginRequest req) {
        GoogleTokenVerifier.GoogleUserInfo info = googleTokenVerifier.verify(req.idToken());

        User user = upsertGoogleUser(info);

        Set<String> authorities = toAuthorities(user);
        JwtTokenProvider.AccessToken accessToken =
                jwtTokenProvider.generateAccessToken(user.getId(), authorities, user.isEmailVerified());

        String rawRefresh = jwtTokenProvider.generateRefreshTokenValue(user.getId());
        user.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        userRepository.save(user);

        AuthResponse auth = buildAuthResponse(accessToken, user, authorities);
        return new LoginResult(auth, rawRefresh);
    }

    @Override
    @Transactional(readOnly = true)
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        // Always behave identically to avoid user enumeration. No password to reset for Google-only
        // accounts (passwordHash == null) — silently no-op.
        if (user == null || user.getPasswordHash() == null) {
            return;
        }

        String token = UUID.randomUUID().toString();
        try {
            redisTemplate.opsForValue()
                    .set("password:reset:" + token, user.getId().toString(), PASSWORD_RESET_TTL);
        } catch (Exception e) {
            // Redis down → token can't be stored; nothing to email. Don't surface to the caller.
            log.warn("Redis unavailable while storing password-reset token for user={}: {}",
                    user.getId(), e.getMessage());
            return;
        }
        emailService.sendPasswordResetEmail(user.getEmail(), token);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String key = "password:reset:" + req.token();
        String userId;
        try {
            userId = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Fail-closed: cannot validate the reset token → reject (security-sensitive, unlike rate-limit).
            log.warn("Redis unavailable while validating password-reset token: {}", e.getMessage());
            throw new InvalidTokenException("INVALID_RESET_TOKEN",
                    "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }
        if (userId == null) {
            throw new InvalidTokenException("INVALID_RESET_TOKEN",
                    "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new InvalidTokenException("INVALID_RESET_TOKEN",
                        "Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn"));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setRefreshTokenHash(null); // invalidate existing sessions on password change
        userRepository.save(user);

        redisTemplate.delete(key); // single-use
    }

    @Override
    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        int sep = rawRefreshToken.indexOf(':');
        if (sep <= 0) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        UUID userId;
        try {
            userId = UUID.fromString(rawRefreshToken.substring(0, sep));
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null
                || user.getRefreshTokenHash() == null
                || !passwordEncoder.matches(rawRefreshToken, user.getRefreshTokenHash())) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }

        Set<String> authorities = toAuthorities(user);
        JwtTokenProvider.AccessToken accessToken =
                jwtTokenProvider.generateAccessToken(user.getId(), authorities, user.isEmailVerified());

        // Rotate the refresh token.
        String newRawRefresh = jwtTokenProvider.generateRefreshTokenValue(user.getId());
        user.setRefreshTokenHash(passwordEncoder.encode(newRawRefresh));
        userRepository.save(user);

        AuthResponse auth = buildAuthResponse(accessToken, user, authorities);
        return new RefreshResult(auth, newRawRefresh);
    }

    @Override
    public void logout(String jti, Instant accessTokenExpiry) {
        if (!StringUtils.hasText(jti) || accessTokenExpiry == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), accessTokenExpiry);
        if (ttl.isNegative() || ttl.isZero()) {
            return; // already expired — nothing to blacklist
        }
        try {
            redisTemplate.opsForValue().set("session:blacklist:" + jti, "1", ttl);
        } catch (Exception e) {
            // Fail-open: a logout best-effort blacklist failure must not break the response.
            log.warn("Redis unavailable while blacklisting jti={}: {}", jti, e.getMessage());
        }
    }

    @Override
    public boolean isEmailVerified(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getName();
        if (principal == null) {
            return false;
        }
        try {
            UUID userId = UUID.fromString(principal.toString());
            return userRepository.findById(userId)
                    .map(User::isEmailVerified)
                    .orElse(false);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ----- helpers -----

    private User upsertGoogleUser(GoogleTokenVerifier.GoogleUserInfo info) {
        return userRepository.findByEmail(info.email())
                .map(existing -> {
                    // Account linking: attach the Google identity to a pre-existing account.
                    if (existing.getGoogleId() == null) {
                        existing.setGoogleId(info.sub());
                    }
                    if (info.emailVerified() && !existing.isEmailVerified()) {
                        existing.setEmailVerified(true);
                    }
                    return existing;
                })
                .orElseGet(() -> createGoogleUser(info));
    }

    private User createGoogleUser(GoogleTokenVerifier.GoogleUserInfo info) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName(RoleName.USER);
                    return roleRepository.save(r);
                });

        User user = new User();
        user.setEmail(info.email());
        user.setPasswordHash(null);
        user.setFullName(StringUtils.hasText(info.name()) ? info.name() : info.email());
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setGoogleId(info.sub());
        user.setEmailVerified(info.emailVerified());
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Race: a concurrent Google login already created this email — re-fetch the winner.
            return userRepository.findByEmail(info.email())
                    .orElseThrow(() -> new ConflictException("EMAIL_EXISTS", "Email đã được đăng ký"));
        }
    }

    private void enforceRegisterRateLimit(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        String key = "rate_limit:register:" + clientIp;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, REGISTER_RATE_WINDOW);
            }
            if (count != null && count > MAX_REGISTRATIONS_PER_IP) {
                throw new ApiException("RATE_LIMITED",
                        "Quá nhiều lượt đăng ký, thử lại sau", HttpStatus.TOO_MANY_REQUESTS);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Fail-open if Redis is down — registration must still work.
            log.warn("Redis unavailable while rate-limiting register for ip={}: {}", clientIp, e.getMessage());
        }
    }

    private void storeEmailVerifyToken(String token, UUID userId) {
        try {
            redisTemplate.opsForValue().set("email:verify:" + token, userId.toString(), EMAIL_VERIFY_TTL);
        } catch (Exception e) {
            log.warn("Redis unavailable while storing email-verify token for user={}: {}", userId, e.getMessage());
        }
    }

    private Set<String> toAuthorities(User user) {
        return user.getRoles().stream()
                .map(r -> "ROLE_" + r.getName().name())
                .collect(Collectors.toSet());
    }

    private AuthResponse buildAuthResponse(JwtTokenProvider.AccessToken accessToken,
                                           User user, Set<String> authorities) {
        long expiresInSeconds = jwtTokenProvider.getAccessExpirationMs() / 1000;
        return new AuthResponse(
                accessToken.token(),
                "Bearer",
                expiresInSeconds,
                toUserResponse(user, authorities));
    }

    private UserResponse toUserResponse(User user) {
        return toUserResponse(user, toAuthorities(user));
    }

    private UserResponse toUserResponse(User user, Set<String> authorities) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                authorities,
                user.isEmailVerified());
    }
}
