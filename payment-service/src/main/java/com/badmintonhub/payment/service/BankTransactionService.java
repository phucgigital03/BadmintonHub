package com.badmintonhub.payment.service;

import com.badmintonhub.payment.dto.response.BankImportResultResponse;
import com.badmintonhub.payment.dto.response.BankTransactionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bank-statement ground truth for AI reconciliation (Day 10.5): import statements (deduped by
 * {@code bank_ref}) and look a transaction up by the payment's {@code order_code} + amount.
 */
public interface BankTransactionService {

    /** STAFF imports a bank statement (parsed by the parser for {@code bankCode}); deduped by bank_ref. */
    BankImportResultResponse importStatement(MultipartFile file, String bankCode);

    /** Candidate credit rows whose memo contains {@code orderCode} and whose amount matches; newest first. */
    List<BankTransactionResponse> lookup(Long orderCode, BigDecimal amount);
}
