package com.badmintonhub.user.service;

/**
 * Verifies a Google-issued OIDC {@code id_token} via Google's tokeninfo endpoint.
 * Implemented by {@code service.impl.GoogleTokenVerifierImpl}.
 */
public interface GoogleTokenVerifier {

    /**
     * Verify the id_token and return the verified identity claims.
     *
     * @throws com.badmintonhub.common.exception.UnauthorizedException if the token is invalid,
     *         expired, or its audience does not match the configured client-id.
     */
    GoogleUserInfo verify(String idToken);

    /** Verified subset of Google id_token claims. */
    record GoogleUserInfo(String sub, String email, boolean emailVerified, String name) {}
}
