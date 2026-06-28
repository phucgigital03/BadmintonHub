package com.badmintonhub.payment.service.impl;

import com.badmintonhub.common.exception.ResourceNotFoundException;
import com.badmintonhub.payment.dto.response.BankImportResultResponse;
import com.badmintonhub.payment.dto.response.BankTransactionResponse;
import com.badmintonhub.payment.entity.BankTransaction;
import com.badmintonhub.payment.entity.enums.BankTransactionSource;
import com.badmintonhub.payment.repository.BankTransactionRepository;
import com.badmintonhub.payment.service.BankStatementParser;
import com.badmintonhub.payment.service.ParsedBankTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for import dedupe (in-file + already-imported), unsupported bank, and lookup mapping. */
@ExtendWith(MockitoExtension.class)
class BankTransactionServiceImplTest {

    @Mock
    BankTransactionRepository repository;

    private final MultipartFile anyFile = new MockMultipartFile("file", "vcb.csv", "text/csv", "ignored".getBytes());

    /** A parser that ignores the stream and returns preset rows — isolates the service from CSV parsing. */
    private BankStatementParser vcbReturning(List<ParsedBankTransaction> rows) {
        return new BankStatementParser() {
            @Override
            public String bankCode() {
                return "VCB";
            }

            @Override
            public List<ParsedBankTransaction> parse(java.io.InputStream in) {
                return rows;
            }
        };
    }

    private ParsedBankTransaction row(String ref) {
        return new ParsedBankTransaction(ref, new BigDecimal("100000"),
                LocalDateTime.of(2026, 6, 5, 0, 0), "CK 184", null, null, "raw");
    }

    @Test
    void importStatement_dedupesInFileAndAlreadyImported() {
        // r1(A), r2(B), r3(A again) → A is an in-file duplicate. Neither A nor B already imported.
        var service = new BankTransactionServiceImpl(List.of(vcbReturning(
                List.of(row("A"), row("B"), row("A")))), repository);
        when(repository.existsByBankRef("A")).thenReturn(false);
        when(repository.existsByBankRef("B")).thenReturn(false);

        BankImportResultResponse result = service.importStatement(anyFile, "VCB");

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.totalRows()).isEqualTo(3);
        verify(repository, times(2)).save(any(BankTransaction.class));
    }

    @Test
    void importStatement_skipsRowAlreadyInDb() {
        var service = new BankTransactionServiceImpl(List.of(vcbReturning(List.of(row("C")))), repository);
        when(repository.existsByBankRef("C")).thenReturn(true);

        BankImportResultResponse result = service.importStatement(anyFile, "VCB");

        assertThat(result.inserted()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(repository, never()).save(any());
    }

    @Test
    void importStatement_unsupportedBank_throwsNotFound() {
        var service = new BankTransactionServiceImpl(List.of(vcbReturning(List.of())), repository);

        assertThatThrownBy(() -> service.importStatement(anyFile, "ACB"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ACB");
        verify(repository, never()).save(any());
    }

    @Test
    void lookup_passesOrderCodeAsStringAndMapsResponse() {
        var service = new BankTransactionServiceImpl(List.of(vcbReturning(List.of())), repository);
        BankTransaction tx = new BankTransaction();
        tx.setId(UUID.randomUUID());
        tx.setBankRef("REF001");
        tx.setAmount(new BigDecimal("100000"));
        tx.setTransferredAt(LocalDateTime.of(2026, 6, 5, 0, 0));
        tx.setMemo("CK 184 thanh toan");
        tx.setSource(BankTransactionSource.MANUAL_IMPORT);
        when(repository.findMatches(new BigDecimal("100000"), "184")).thenReturn(List.of(tx));

        List<BankTransactionResponse> result = service.lookup(184L, new BigDecimal("100000"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bankRef()).isEqualTo("REF001");
        assertThat(result.get(0).source()).isEqualTo(BankTransactionSource.MANUAL_IMPORT);
        verify(repository).findMatches(new BigDecimal("100000"), "184"); // String.valueOf(orderCode)
    }
}
