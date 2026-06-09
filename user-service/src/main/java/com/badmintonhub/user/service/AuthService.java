package com.badmintonhub.user.service;

import com.badmintonhub.user.dto.request.GoogleLoginRequest;
import com.badmintonhub.user.dto.request.LoginRequest;
import com.badmintonhub.user.dto.request.RegisterRequest;
import com.badmintonhub.user.dto.request.ResetPasswordRequest;
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

    /** Sign in (or sign up) with a Google id_token; issues JWT + refresh exactly like local login. */
    LoginResult googleLogin(GoogleLoginRequest req);

    /** Issue a single-use password-reset token (Redis, 1h). Always returns silently (no user enumeration). */
    void forgotPassword(String email);

    /** Consume the reset token (single-use), set the new password, and invalidate existing sessions. */
    void resetPassword(ResetPasswordRequest req);

    RefreshResult refresh(String rawRefreshToken);

    void logout(String jti, Instant accessTokenExpiry);

    boolean isEmailVerified(Authentication authentication);

    /** Result of login — controller sets the raw refresh token in an HttpOnly cookie. */
    record LoginResult(AuthResponse auth, String rawRefreshToken) {}

    /** Result of refresh — controller resets the rotated refresh-token cookie. */
    record RefreshResult(AuthResponse auth, String newRawRefreshToken) {}
}
