package com.badmintonhub.user.service;

/**
 * Sends transactional emails. Implemented by {@code service.impl.EmailServiceImpl}, which sends via
 * SendGrid when {@code sendgrid.api-key} is set, else logs the link to the console (dev fallback).
 */
public interface EmailService {

    void sendVerificationEmail(String toEmail, String token);

    void sendPasswordResetEmail(String toEmail, String token);
}
