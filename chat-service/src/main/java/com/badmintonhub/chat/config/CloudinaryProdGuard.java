package com.badmintonhub.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fail-fast guard: in the {@code prod} profile Cloudinary credentials are MANDATORY. Without them
 * {@code CloudinaryServiceImpl} silently degrades to a {@code local-fallback://} placeholder, which in
 * production would mean chat image attachments are never actually stored. Aborting startup is safer.
 *
 * <p>Active only under {@code prod}; dev/test keep the degrade path so the flow stays exercisable without keys.</p>
 */
@Slf4j
@Component
@Profile("prod")
public class CloudinaryProdGuard {

    public CloudinaryProdGuard(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        if (!StringUtils.hasText(cloudName) || !StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException(
                    "Cloudinary credentials are required in the 'prod' profile — chat image upload must not "
                    + "degrade to a local-fallback placeholder. Set CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY "
                    + "/ CLOUDINARY_API_SECRET.");
        }
        log.info("Cloudinary credentials present (prod profile) — chat image upload enabled.");
    }
}
