package com.badmintonhub.payment.config;

import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the single active bank account (single-club model) so {@code POST /api/payments/initiate}
 * always has an account + QR to show. Upsert + single-active: the configured account is created or
 * updated, and every OTHER active account is deactivated — so {@code findFirstByIsActiveTrue()} is
 * deterministic even if an older demo account is still sitting in payment_db.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String BANK_NAME = "Vietcombank";
    private static final String ACCOUNT_NUMBER = "1022044984";
    private static final String ACCOUNT_NAME = "NGUYEN QUOC PHUC";
    private static final String QR_IMAGE_URL =
            "https://res.cloudinary.com/deflnsi6u/image/upload/v1781770661/"
            + "A%CC%89nh_ma%CC%80n_hi%CC%80nh_2026-06-18_lu%CC%81c_15.16.59_kdensn.png";

    private final BankAccountRepository bankAccountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        BankAccount account = bankAccountRepository.findByAccountNumber(ACCOUNT_NUMBER)
                .orElseGet(BankAccount::new);
        account.setBankName(BANK_NAME);
        account.setAccountNumber(ACCOUNT_NUMBER);
        account.setAccountName(ACCOUNT_NAME);
        account.setQrImageUrl(QR_IMAGE_URL);
        account.setActive(true);
        bankAccountRepository.save(account);

        // Deactivate any other active account (e.g. the old demo) so exactly one stays active.
        var stragglers = bankAccountRepository.findByIsActiveTrueAndAccountNumberNot(ACCOUNT_NUMBER);
        stragglers.forEach(other -> other.setActive(false));
        if (!stragglers.isEmpty()) {
            bankAccountRepository.saveAll(stragglers);
            log.info("Deactivated {} other active bank account(s)", stragglers.size());
        }

        log.info("Active bank account: {} ({}) — {}", ACCOUNT_NUMBER, BANK_NAME, ACCOUNT_NAME);
    }
}
