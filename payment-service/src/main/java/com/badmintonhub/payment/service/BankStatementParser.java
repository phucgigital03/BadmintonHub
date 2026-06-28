package com.badmintonhub.payment.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Pluggable bank-statement parser. One implementation per bank/format; the service resolves the right one
 * by {@link #bankCode()}. This is the extension point that lets a future SePay/Excel source plug in
 * without touching the entity, repository, import flow, or AI lookup.
 */
public interface BankStatementParser {

    /** Short bank code this parser handles, e.g. {@code "VCB"}. Matched against the import request's bank. */
    String bankCode();

    /** Parse the statement stream into credit (money-in) rows. Implementations skip debit/zero rows. */
    List<ParsedBankTransaction> parse(InputStream in) throws IOException;
}
