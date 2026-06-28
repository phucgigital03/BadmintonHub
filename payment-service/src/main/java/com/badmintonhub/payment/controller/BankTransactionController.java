package com.badmintonhub.payment.controller;

import com.badmintonhub.payment.dto.response.BankImportResultResponse;
import com.badmintonhub.payment.dto.response.BankTransactionResponse;
import com.badmintonhub.payment.service.BankTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bank-statement ground truth for AI reconciliation (Day 10.5). STAFF/ADMIN only — there is no
 * service-token in this system, so when ai-service calls {@code lookup} it forwards the STAFF's JWT
 * (same Feign auth-forwarding the other services use).
 */
@RestController
@RequestMapping("/api/bank-transactions")
@RequiredArgsConstructor
public class BankTransactionController {

    private final BankTransactionService bankTransactionService;

    /** Import a bank statement (CSV). Deduped by {@code bank_ref} → {inserted, skipped, totalRows}. */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<BankImportResultResponse> importStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bank", defaultValue = "VCB") String bank) {
        return ResponseEntity.ok(bankTransactionService.importStatement(file, bank));
    }

    /** Look a transaction up by the payment's order code + amount (for the AI tool). Empty list = no match. */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<List<BankTransactionResponse>> lookup(
            @RequestParam Long orderCode,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(bankTransactionService.lookup(orderCode, amount));
    }
}
