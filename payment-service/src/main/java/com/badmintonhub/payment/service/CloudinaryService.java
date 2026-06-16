package com.badmintonhub.payment.service;

import org.springframework.web.multipart.MultipartFile;

/** Uploads a payment-proof screenshot and returns its hosted URL. */
public interface CloudinaryService {

    /**
     * Upload the proof image and return its URL. When Cloudinary keys are absent the implementation
     * degrades to a local-fallback placeholder URL so the flow still works in dev.
     */
    String uploadProof(MultipartFile file);
}
