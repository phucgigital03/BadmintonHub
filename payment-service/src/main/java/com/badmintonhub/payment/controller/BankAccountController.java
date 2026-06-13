package com.badmintonhub.payment.controller;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.payment.dto.response.BankAccountResponse;
import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The active bank account shown on the payment screen (single-club model). Read-only; the account is
 * managed via the seeder / admin tooling, not this controller.
 */
@RestController
@RequestMapping("/api/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountRepository bankAccountRepository;

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BankAccountResponse> active() {
        BankAccount bank = bankAccountRepository.findFirstByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("NO_BANK_ACCOUNT",
                        "Chưa cấu hình tài khoản nhận tiền"));
        return ResponseEntity.ok(new BankAccountResponse(
                bank.getId(), bank.getBankName(), bank.getAccountNumber(),
                bank.getAccountName(), bank.getQrImageUrl()));
    }
}
