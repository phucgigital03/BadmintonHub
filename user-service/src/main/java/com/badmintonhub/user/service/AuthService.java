package com.badmintonhub.user.service;

import com.badmintonhub.user.dto.request.LoginRequest;
import com.badmintonhub.user.dto.request.RegisterRequest;
import com.badmintonhub.user.dto.response.AuthResponse;
import com.badmintonhub.user.dto.response.UserResponse;
import org.springframework.security.core.Authentication;

import java.time.Instant;

/**
 * Auth core contract — registration, email verification, login, refresh-token rotation, logout.
 * Implemented by {@code service.impl.AuthServiceImpl} (registered as bean {@code "authService"}
 * so {@code @PreAuthorize("@authService.isEmailVerified(authentication)")} resolves).
 */
public interface AuthService {

    UserResponse register(RegisterRequest req, String clientIp);

    void verifyEmail(String token);

    LoginResult login(LoginRequest req);

    RefreshResult refresh(String rawRefreshToken);

    void logout(String jti, Instant accessTokenExpiry);

    boolean isEmailVerified(Authentication authentication);

    /** Result of login — controller sets the raw refresh token in an HttpOnly cookie. */
    record LoginResult(AuthResponse auth, String rawRefreshToken) {}

    /** Result of refresh — controller resets the rotated refresh-token cookie. */
    record RefreshResult(AuthResponse auth, String newRawRefreshToken) {}
}
