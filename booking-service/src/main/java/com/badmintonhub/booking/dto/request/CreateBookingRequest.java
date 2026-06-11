package com.badmintonhub.booking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create a booking order: a set of selected 30-min cells at one club on one date, plus the customer
 * contact form. Sport is implied by the selected slots' courts, so it is not part of the request —
 * the slots are validated against the club's live grid for {@code date}.
 */
public record CreateBookingRequest(
        @NotNull UUID clubId,
        @NotNull @FutureOrPresent LocalDate date,
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại không hợp lệ") String customerPhone,
        @Size(max = 500) String note,
        @NotEmpty @Valid List<Item> items
) {
    /** One selected cell: the court it belongs to + the 30-min slot id (both court_db UUIDs). */
    public record Item(
            @NotNull UUID courtId,
            @NotNull UUID slotId
    ) {}
}
