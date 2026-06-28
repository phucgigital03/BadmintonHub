package com.badmintonhub.payment.service.impl;

import com.badmintonhub.common.exception.ConflictException;
import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.payment.dto.response.BankImportResultResponse;
import com.badmintonhub.payment.dto.response.BankTransactionResponse;
import com.badmintonhub.payment.entity.BankTransaction;
import com.badmintonhub.payment.entity.enums.BankTransactionSource;
import com.badmintonhub.payment.repository.BankTransactionRepository;
import com.badmintonhub.payment.service.BankStatementParser;
import com.badmintonhub.payment.service.BankTransactionService;
import com.badmintonhub.payment.service.ParsedBankTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankTransactionServiceImpl implements BankTransactionService {

    private final List<BankStatementParser> parsers; // one per bank/format — extension point
    private final BankTransactionRepository bankTransactionRepository;

    @Override
    @Transactional
    public BankImportResultResponse importStatement(MultipartFile file, String bankCode) {
        BankStatementParser parser = resolveParser(bankCode);

        List<ParsedBankTransaction> parsed;
        try {
            parsed = parser.parse(file.getInputStream());
        } catch (IOException | RuntimeException e) {
            throw new ConflictException("INVALID_STATEMENT_FORMAT",
                    "Không đọc được file sao kê — kiểm tra định dạng " + bankCode + " CSV");
        }

        // Dedupe by bank_ref: within this file (HashSet) and against already-imported rows (existsByBankRef).
        // The UNIQUE(bank_ref) constraint is the hard backstop for a rare concurrent-import race (the
        // transaction rolls back → 409); the normal "re-import the same statement" case is caught here.
        int inserted = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();
        for (ParsedBankTransaction p : parsed) {
            if (!seen.add(p.bankRef()) || bankTransactionRepository.existsByBankRef(p.bankRef())) {
                skipped++;
                continue;
            }
            bankTransactionRepository.save(toEntity(p));
            inserted++;
        }
        log.info("Imported bank statement [{}]: {} inserted, {} skipped of {} credit rows",
                bankCode, inserted, skipped, parsed.size());
        return new BankImportResultResponse(inserted, skipped, parsed.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BankTransactionResponse> lookup(Long orderCode, BigDecimal amount) {
        return bankTransactionRepository.findMatches(amount, String.valueOf(orderCode)).stream()
                .map(this::toResponse)
                .toList();
    }

    private BankStatementParser resolveParser(String bankCode) {
        return parsers.stream()
                .filter(p -> p.bankCode().equalsIgnoreCase(bankCode))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("UNSUPPORTED_BANK",
                        "Chưa hỗ trợ import sao kê cho ngân hàng: " + bankCode));
    }

    private BankTransaction toEntity(ParsedBankTransaction p) {
        BankTransaction t = new BankTransaction();
        t.setBankRef(p.bankRef());
        t.setAmount(p.amount());
        t.setTransferredAt(p.transferredAt());
        t.setMemo(p.memo());
        t.setSenderName(p.senderName());
        t.setAccountNumber(p.accountNumber());
        t.setRawLine(p.rawLine());
        t.setSource(BankTransactionSource.MANUAL_IMPORT);
        return t;
    }

    private BankTransactionResponse toResponse(BankTransaction t) {
        return new BankTransactionResponse(
                t.getId(),
                t.getBankRef(),
                t.getAmount(),
                t.getTransferredAt(),
                t.getMemo(),
                t.getSenderName(),
                t.getAccountNumber(),
                t.getSource());
    }
}
