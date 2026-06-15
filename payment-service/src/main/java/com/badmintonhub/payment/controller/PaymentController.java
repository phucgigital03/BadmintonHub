package com.badmintonhub.payment.controller;

import com.badmintonhub.payment.dto.request.InitiatePaymentRequest;
import com.badmintonhub.payment.dto.request.RefundRequest;
import com.badmintonhub.payment.dto.request.RejectPaymentRequest;
import com.badmintonhub.payment.dto.response.PaymentResponse;
import com.badmintonhub.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.UUID;

/**
 * Bank-QR payments. {@code initiate} requires a verified email (Never-Violate #10) via the
 * {@code EMAIL_VERIFIED} authority. Proof upload / read are owner-or-STAFF/ADMIN (ownership checked in
 * the service once the payment is loaded). Confirm / reject / refund are STAFF/ADMIN only.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('USER','COACH') and hasAuthority('EMAIL_VERIFIED')")
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody InitiatePaymentRequest req,
                                                    Authentication auth) {
        PaymentResponse created = paymentService.initiate(req, UUID.fromString(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/proof")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentResponse> submitProof(@PathVariable UUID id,
                                                       @RequestParam("file") MultipartFile file,
                                                       Authentication auth) {
        return ResponseEntity.ok(
                paymentService.submitProof(id, file, UUID.fromString(auth.getName()), roles(auth)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<PaymentResponse> confirm(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(paymentService.confirm(id, UUID.fromString(auth.getName())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<PaymentResponse> reject(@PathVariable UUID id,
                                                  @RequestBody(required = false) @Valid RejectPaymentRequest req,
                                                  Authentication auth) {
        String reason = (req != null) ? req.reason() : null;
        return ResponseEntity.ok(paymentService.reject(id, reason, UUID.fromString(auth.getName())));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID id,
                                                  @Valid @RequestBody RefundRequest req,
                                                  Authentication auth) {
        return ResponseEntity.ok(paymentService.refund(id, req, UUID.fromString(auth.getName())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(paymentService.getById(id, UUID.fromString(auth.getName()), roles(auth)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<PaymentResponse>> listMine(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return ResponseEntity.ok(paymentService.listMine(UUID.fromString(auth.getName()), pageable));
    }

    /** Payments awaiting STAFF review (proof uploaded → PROOF_SUBMITTED), oldest first — the confirm/reject queue. */
    @GetMapping("/pending-review")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Page<PaymentResponse>> listPendingReview(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listPendingReview(pageable));
    }

    /** Confirmed payments awaiting a manual refund (booking cancelled after the money was confirmed). */
    @GetMapping("/refund-required")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Page<PaymentResponse>> listRefundRequired(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(paymentService.listRefundRequired(pageable));
    }

    private static Collection<String> roles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
