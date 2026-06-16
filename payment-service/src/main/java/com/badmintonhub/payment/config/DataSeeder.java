package com.badmintonhub.payment.config;

import com.badmintonhub.payment.entity.BankAccount;
import com.badmintonhub.payment.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the single active bank account (single-club model) so {@code POST /api/payments/initiate}
 * always has an account + QR to show. Idempotent: skips if the demo account already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_ACCOUNT_NUMBER = "0962728894";

    private final BankAccountRepository bankAccountRepository;

    @Override
    public void run(String... args) {
        if (bankAccountRepository.existsByAccountNumber(DEMO_ACCOUNT_NUMBER)) {
            log.info("Demo bank account '{}' already present — skipping seed", DEMO_ACCOUNT_NUMBER);
            return;
        }

        BankAccount account = new BankAccount();
        account.setBankName("MB Bank");
        account.setAccountNumber(DEMO_ACCOUNT_NUMBER);
        account.setAccountName("TRAN QUOC PHU");
        // Placeholder QR — replace with a real Cloudinary bank-QR URL when configured.
        account.setQrImageUrl("https://img.vietqr.io/image/MB-" + DEMO_ACCOUNT_NUMBER + "-compact.png");
        account.setActive(true);
        bankAccountRepository.save(account);

        log.info("Seeded active bank account: {} ({})", DEMO_ACCOUNT_NUMBER, account.getBankName());
    }
}
