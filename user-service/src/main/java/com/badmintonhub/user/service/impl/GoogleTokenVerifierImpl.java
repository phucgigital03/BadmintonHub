package com.badmintonhub.user.service.impl;

import com.badmintonhub.common.exception.UnauthorizedException;
import com.badmintonhub.user.service.GoogleTokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Verifies Google id_tokens by calling Google's tokeninfo endpoint:
 * {@code GET https://oauth2.googleapis.com/tokeninfo?id_token=...}. Google validates the signature
 * and expiry server-side; we additionally enforce the {@code aud} claim matches our client-id when
 * configured. If {@code google.client-id} is empty (not yet configured in dev), the audience check
 * is skipped with a warning — production must set {@code GOOGLE_CLIENT_ID}.
 */
@Slf4j
@Service
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final String clientId;
    private final RestClient restClient;

    public GoogleTokenVerifierImpl(@Value("${google.client-id:}") String clientId) {
        this.clientId = clientId;
        this.restClient = RestClient.create();
    }

    @Override
    public GoogleUserInfo verify(String idToken) {
        Map<String, Object> claims;
        try {
            claims = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={t}", idToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            // 4xx from Google (invalid/expired token) or a network error.
            log.debug("Google tokeninfo rejected the id_token: {}", e.getMessage());
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google token không hợp lệ");
        }

        if (claims == null || claims.get("sub") == null || claims.get("email") == null) {
            throw new UnauthorizedException("INVALID_GOOGLE_TOKEN", "Google token không hợp lệ");
        }

        String aud = asString(claims.get("aud"));
        if (StringUtils.hasText(clientId)) {
            if (!clientId.equals(aud)) {
                throw new UnauthorizedException("INVALID_GOOGLE_TOKEN",
                        "Google token không dành cho ứng dụng này");
            }
        } else {
            log.warn("google.client-id chưa cấu hình — bỏ qua kiểm tra audience của Google id_token");
        }

        return new GoogleUserInfo(
                asString(claims.get("sub")),
                asString(claims.get("email")),
                Boolean.parseBoolean(asString(claims.get("email_verified"))),
                asString(claims.get("name")));
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
