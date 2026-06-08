package com.badmintonhub.user.service;

import com.badmintonhub.common.exception.ApiException;
import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.InvalidTokenException;
import com.badmintonhub.common.exception.UnauthorizedException;
import com.badmintonhub.user.dto.request.LoginRequest;
import com.badmintonhub.user.dto.request.RegisterRequest;
import com.badmintonhub.user.dto.response.AuthResponse;
import com.badmintonhub.user.dto.response.UserResponse;
import com.badmintonhub.user.entity.Role;
import com.badmintonhub.user.entity.User;
import com.badmintonhub.user.entity.enums.AuthProvider;
import com.badmintonhub.user.entity.enums.RoleName;
import com.badmintonhub.user.repository.RoleRepository;
import com.badmintonhub.user.repository.UserRepository;
import com.badmintonhub.user.security.JwtTokenProvider;
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
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_REGISTRATIONS_PER_IP = 5;
    private static final Duration REGISTER_RATE_WINDOW = Duration.ofHours(1);
    private static final Duration EMAIL_VERIFY_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

    /** Result of login — controller sets the raw refresh token in an HttpOnly cookie. */
    public record LoginResult(AuthResponse auth, String rawRefreshToken) {}

    /** Result of refresh — controller resets the rotated refresh-token cookie. */
    public record RefreshResult(AuthResponse auth, String newRawRefreshToken) {}

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
                jwtTokenProvider.generateAccessToken(user.getId(), authorities);

        String rawRefresh = jwtTokenProvider.generateRefreshTokenValue(user.getId());
        user.setRefreshTokenHash(passwordEncoder.encode(rawRefresh));
        userRepository.save(user);

        AuthResponse auth = buildAuthResponse(accessToken, user, authorities);
        return new LoginResult(auth, rawRefresh);
    }

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
                jwtTokenProvider.generateAccessToken(user.getId(), authorities);

        // Rotate the refresh token.
        String newRawRefresh = jwtTokenProvider.generateRefreshTokenValue(user.getId());
        user.setRefreshTokenHash(passwordEncoder.encode(newRawRefresh));
        userRepository.save(user);

        AuthResponse auth = buildAuthResponse(accessToken, user, authorities);
        return new RefreshResult(auth, newRawRefresh);
    }

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
