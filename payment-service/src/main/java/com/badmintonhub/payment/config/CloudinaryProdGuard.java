package com.badmintonhub.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fail-fast guard: in the {@code prod} profile Cloudinary credentials are MANDATORY. Without them
 * {@link com.badmintonhub.payment.service.impl.CloudinaryServiceImpl} silently degrades to a
 * {@code local-fallback://} placeholder URL — which in production would mean STAFF confirming real-money
 * payments with no actual proof screenshot to verify the bank transfer against. Aborting startup is far
 * safer than serving that.
 *
 * <p>Active only under {@code prod} (e.g. {@code SPRING_PROFILES_ACTIVE=prod}); dev / test keep the
 * degrade path so the flow stays exercisable without keys. The throw happens during bean construction,
 * so an unconfigured prod deploy never finishes booting.</p>
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
                    "Cloudinary credentials are required in the 'prod' profile — payment proof upload must not "
                    + "degrade to a local-fallback placeholder when handling real money. Set "
                    + "CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET.");
        }
        log.info("Cloudinary credentials present (prod profile) — payment proof upload enabled.");
    }
}
