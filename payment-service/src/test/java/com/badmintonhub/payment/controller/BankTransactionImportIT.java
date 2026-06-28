package com.badmintonhub.payment.controller;

import com.badmintonhub.payment.client.BookingServiceClient;
import com.badmintonhub.payment.entity.BankTransaction;
import com.badmintonhub.payment.entity.enums.BankTransactionSource;
import com.badmintonhub.payment.repository.BankTransactionRepository;
import com.badmintonhub.payment.service.CloudinaryService;
import com.badmintonhub.test.AbstractKafkaIntegrationTest;
import com.badmintonhub.test.JwtTestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end import/lookup against real Postgres (Testcontainers): proves the multipart CSV import only
 * counts credit rows, deduping a re-import via the real {@code UNIQUE(bank_ref)} backstop, plus the lookup
 * query semantics and the STAFF/ADMIN authorization gate.
 */
@AutoConfigureMockMvc
class BankTransactionImportIT extends AbstractKafkaIntegrationTest {

    private static final String CSV = """
            Ngay,So tham chieu,Ghi no,Ghi co,So du,Mo ta
            05/06/2026,REF001,0,"100,000",500000,CK 184 thanh toan san
            06/06/2026,REF002,"50,000",0,450000,Rut tien mat
            07/06/2026,REF003,0,"200,000",650000,CK 200 dat san
            """;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("jwt.secret", () -> "test-jwt-secret-at-least-32-bytes-long-0123456789");
        r.add("eureka.client.enabled", () -> "false");
        r.add("eureka.client.register-with-eureka", () -> "false");
        r.add("eureka.client.fetch-registry", () -> "false");
    }

    @MockBean BookingServiceClient bookingServiceClient; // avoid resolving lb://booking-service via Eureka
    @MockBean CloudinaryService cloudinaryService;

    @Autowired MockMvc mockMvc;
    @Autowired BankTransactionRepository bankTransactionRepository;
    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void clean() {
        bankTransactionRepository.deleteAll();
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "vcb.csv", "text/csv", CSV.getBytes(StandardCharsets.UTF_8));
    }

    private String staff() {
        return JwtTestTokens.bearer(jwtSecret, UUID.randomUUID().toString(), "ROLE_STAFF");
    }

    @Test
    void import_thenReimport_countsCreditRowsAndDedupes() throws Exception {
        // First import: 2 credit rows inserted (header + debit row skipped by the parser).
        mockMvc.perform(multipart("/api/bank-transactions/import")
                        .file(csvFile()).param("bank", "VCB").header("Authorization", staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.totalRows").value(2));

        // Re-import the same statement: all skipped via UNIQUE(bank_ref) dedupe.
        mockMvc.perform(multipart("/api/bank-transactions/import")
                        .file(csvFile()).param("bank", "VCB").header("Authorization", staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void lookup_returnsMatchByOrderCodeAndAmount() throws Exception {
        BankTransaction tx = new BankTransaction();
        tx.setBankRef("REF-" + UUID.randomUUID());
        tx.setAmount(new BigDecimal("100000"));
        tx.setTransferredAt(LocalDateTime.of(2026, 6, 5, 0, 0));
        tx.setMemo("CK 184 thanh toan san");
        tx.setSource(BankTransactionSource.MANUAL_IMPORT);
        bankTransactionRepository.save(tx);

        mockMvc.perform(get("/api/bank-transactions/lookup")
                        .param("orderCode", "184").param("amount", "100000")
                        .header("Authorization", staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memo").value("CK 184 thanh toan san"));
    }

    @Test
    void import_asUser_isForbidden() throws Exception {
        String user = JwtTestTokens.bearer(jwtSecret, UUID.randomUUID().toString(), "ROLE_USER");
        mockMvc.perform(multipart("/api/bank-transactions/import")
                        .file(csvFile()).param("bank", "VCB").header("Authorization", user))
                .andExpect(status().isForbidden());
    }
}
