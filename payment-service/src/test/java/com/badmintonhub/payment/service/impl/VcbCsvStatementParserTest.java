package com.badmintonhub.payment.service.impl;

import com.badmintonhub.payment.service.ParsedBankTransaction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the VCB CSV parser: keeps only credit rows, skips header/debit/malformed, parses fields. */
class VcbCsvStatementParserTest {

    private final VcbCsvStatementParser parser = new VcbCsvStatementParser();

    private List<ParsedBankTransaction> parse(String csv) throws IOException {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parse_keepsOnlyCreditRows_skippingHeaderAndDebit() throws IOException {
        String csv = """
                Ngay,So tham chieu,Ghi no,Ghi co,So du,Mo ta
                05/06/2026,REF001,0,"100,000",500000,CK 184 thanh toan san
                06/06/2026,REF002,"50,000",0,450000,Rut tien mat
                07/06/2026,REF003,0,"200,000",650000,CK 200 dat san
                """;

        List<ParsedBankTransaction> rows = parse(csv);

        assertThat(rows).hasSize(2); // header skipped (date col not a date), debit row skipped
        ParsedBankTransaction first = rows.get(0);
        assertThat(first.bankRef()).isEqualTo("REF001");
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(first.transferredAt()).isEqualTo(LocalDateTime.of(2026, 6, 5, 0, 0));
        assertThat(first.memo()).isEqualTo("CK 184 thanh toan san");
        assertThat(rows.get(1).bankRef()).isEqualTo("REF003");
    }

    @Test
    void parse_skipsCreditRowWithBlankReference() throws IOException {
        String csv = """
                Ngay,So tham chieu,Ghi no,Ghi co,So du,Mo ta
                05/06/2026,,0,"100,000",500000,CK no ref
                07/06/2026,REF003,0,"200,000",650000,CK 200 dat san
                """;

        List<ParsedBankTransaction> rows = parse(csv);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).bankRef()).isEqualTo("REF003");
    }

    @Test
    void parse_emptyOrHeaderOnly_returnsEmpty() throws IOException {
        assertThat(parse("")).isEmpty();
        assertThat(parse("Ngay,So tham chieu,Ghi no,Ghi co,So du,Mo ta\n")).isEmpty();
    }

    @Test
    void parseAmount_stripsThousandsSeparators() {
        assertThat(VcbCsvStatementParser.parseAmount("100,000")).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(VcbCsvStatementParser.parseAmount("1.000.000")).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(VcbCsvStatementParser.parseAmount("")).isNull();
        assertThat(VcbCsvStatementParser.parseAmount("abc")).isNull();
        assertThat(VcbCsvStatementParser.parseAmount(null)).isNull();
    }

    @Test
    void parseDate_handlesDateAndDateTimeAndRejectsGarbage() {
        assertThat(VcbCsvStatementParser.parseDate("05/06/2026")).isEqualTo(LocalDateTime.of(2026, 6, 5, 0, 0));
        assertThat(VcbCsvStatementParser.parseDate("05/06/2026 14:30:00"))
                .isEqualTo(LocalDateTime.of(2026, 6, 5, 14, 30, 0));
        assertThat(VcbCsvStatementParser.parseDate("not-a-date")).isNull();
        assertThat(VcbCsvStatementParser.parseDate(null)).isNull();
    }
}
