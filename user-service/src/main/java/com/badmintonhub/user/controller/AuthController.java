package com.badmintonhub.user.controller;

import com.badmintonhub.common.exception.UnauthorizedException;
import com.badmintonhub.security.JwtUtil;
import com.badmintonhub.user.dto.request.LoginRequest;
import com.badmintonhub.user.dto.request.RegisterRequest;
import com.badmintonhub.user.dto.response.AuthResponse;
import com.badmintonhub.user.dto.response.UserResponse;
import com.badmintonhub.user.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(30);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletRequest request) {
        UserResponse user = authService.register(req, clientIp(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", "Email đã được xác thực"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(req);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken()).toString());
        return ResponseEntity.ok(result.auth());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new UnauthorizedException("MISSING_REFRESH_TOKEN", "Thiếu refresh token");
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(result.newRawRefreshToken()).toString());
        return ResponseEntity.ok(result.auth());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtUtil.parseAndValidate(authHeader.substring(BEARER_PREFIX.length()));
                authService.logout(jwtUtil.getJti(claims), claims.getExpiration().toInstant());
            } catch (Exception e) {
                // Idempotent logout — a missing/invalid token still clears the cookie and returns 200.
                log.debug("Logout with missing/invalid token, treating as idempotent: {}", e.getMessage());
            }
        }
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        return ResponseEntity.ok(Map.of("message", "Đã đăng xuất"));
    }

    // ----- helpers -----

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_COOKIE_MAX_AGE)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
