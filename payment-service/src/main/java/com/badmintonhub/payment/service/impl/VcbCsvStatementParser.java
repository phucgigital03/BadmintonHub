package com.badmintonhub.payment.service.impl;

import com.badmintonhub.payment.service.BankStatementParser;
import com.badmintonhub.payment.service.ParsedBankTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a Vietcombank statement export (CSV) into credit (money-in) rows.
 *
 * <p><strong>Layout</strong> is the common VCB CSV column order, kept in the {@code COL_*} constants below
 * so it can be adjusted in ONE place once a real export sample is available. The parser is tolerant: it
 * skips any preamble/header rows (rows whose date column isn't a valid date) and any malformed row, so a
 * slightly different export still yields the valid credit lines. Only rows with a positive <em>credit</em>
 * amount are kept — debit (money-out) and zero rows are ignored, since reconciliation only cares about
 * money the venue received.
 *
 * <p>Sender name / counterparty account aren't reliable VCB CSV columns (they live inside the description),
 * so they're left null; the {@code memo} carries the full content (including the {@code #orderCode}).
 */
@Slf4j
@Component
public class VcbCsvStatementParser implements BankStatementParser {

    // --- VCB CSV column mapping — adjust here if a real export differs ---
    private static final int COL_DATE = 0;    // Ngày giao dịch (dd/MM/yyyy[ HH:mm:ss])
    private static final int COL_REF = 1;     // Số tham chiếu / mã giao dịch — dedupe key
    private static final int COL_DEBIT = 2;   // Ghi nợ (money-out) — ignored
    private static final int COL_CREDIT = 3;  // Ghi có (money-in)
    private static final int COL_BALANCE = 4; // Số dư — unused
    private static final int COL_DESC = 5;    // Mô tả / nội dung — carries "#orderCode"
    private static final int MIN_COLUMNS = COL_DESC + 1;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    @Override
    public String bankCode() {
        return "VCB";
    }

    @Override
    public List<ParsedBankTransaction> parse(InputStream in) throws IOException {
        List<ParsedBankTransaction> result = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord rec : parser) {
                if (rec.size() < MIN_COLUMNS) {
                    continue; // preamble / short row
                }
                LocalDateTime transferredAt = parseDate(rec.get(COL_DATE));
                if (transferredAt == null) {
                    continue; // header / non-data row (date column isn't a date)
                }
                BigDecimal credit = parseAmount(rec.get(COL_CREDIT));
                if (credit == null || credit.signum() <= 0) {
                    continue; // debit-only or zero row — not a money-in line
                }
                String bankRef = trimToNull(rec.get(COL_REF));
                if (bankRef == null) {
                    log.warn("Skipping VCB statement row with credit but no reference: {}", rec);
                    continue; // can't dedupe without a reference
                }
                String memo = trimToNull(rec.get(COL_DESC));
                result.add(new ParsedBankTransaction(
                        bankRef, credit, transferredAt, memo, null, null, rawLine(rec)));
            }
        }
        return result;
    }

    private static String rawLine(CSVRecord rec) {
        return String.join(",", rec.toList());
    }

    /** Parse a VND amount, tolerating thousands separators (",", "."). VND has no decimals — digits only. */
    static BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDateTime parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(value, fmt); // formats carrying a time
            } catch (Exception withTime) {
                try {
                    return LocalDate.parse(value, fmt).atStartOfDay(); // date-only format
                } catch (Exception dateOnly) {
                    // try next format
                }
            }
        }
        return null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
