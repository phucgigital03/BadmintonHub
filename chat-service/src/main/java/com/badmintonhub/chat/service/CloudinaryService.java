package com.badmintonhub.chat.service;

import org.springframework.web.multipart.MultipartFile;

/** Uploads a chat image attachment and returns its URL (UC-CHAT-04). */
public interface CloudinaryService {

    /**
     * @return the uploaded image URL (Cloudinary secure URL, or a {@code local-fallback://} placeholder in
     *         dev when Cloudinary is unconfigured).
     * @throws com.badmintonhub.common.exception.ConflictException {@code IMAGE_UPLOAD_FAILED} (409) if the
     *         upload fails — the image is the message content, so no message is created.
     */
    String uploadImage(MultipartFile file);
}
