package com.badmintonhub.payment.service.impl;

import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.payment.service.CloudinaryService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Cloudinary upload with a dev degrade path (mirrors {@code EmailServiceImpl}): when the cloud-name /
 * api-key are absent it skips the real upload and returns a {@code local-fallback://} placeholder URL +
 * a warn log, so the payment flow is fully exercisable without Cloudinary credentials.
 */
@Slf4j
@Service
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary; // null when not configured (degrade mode)

    public CloudinaryServiceImpl(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        if (StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true));
        } else {
            this.cloudinary = null;
        }
    }

    @Override
    public String uploadProof(MultipartFile file) {
        if (cloudinary == null) {
            String placeholder = "local-fallback://proof/" + UUID.randomUUID();
            log.warn("[DEV] Cloudinary not configured — payment proof '{}' not uploaded, using {}",
                    file.getOriginalFilename(), placeholder);
            return placeholder;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader()
                    .upload(file.getBytes(), ObjectUtils.asMap("folder", "payment-proofs"));
            return (String) result.get("secure_url");
        } catch (Exception e) {
            log.warn("Cloudinary upload failed for '{}': {}", file.getOriginalFilename(), e.getMessage());
            throw new ConflictException("PROOF_UPLOAD_FAILED", "Tải ảnh chứng từ thất bại, vui lòng thử lại");
        }
    }
}
