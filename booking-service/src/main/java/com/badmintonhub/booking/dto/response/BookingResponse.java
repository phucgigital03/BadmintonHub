package com.badmintonhub.booking.dto.response;

import com.badmintonhub.booking.entity.enums.BookingStatus;
import com.badmintonhub.booking.entity.enums.CustomerType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Booking order header + its line items. {@code items} is empty for a cancelled order (slots released). */
public record BookingResponse(
        UUID id,
        UUID userId,
        UUID clubId,
        String customerName,
        String customerPhone,
        String note,
        CustomerType customerType,
        LocalDate bookingDate,
        BigDecimal totalPrice,
        BigDecimal refundAmount,
        BookingStatus status,
        LocalDateTime earliestStartTime,
        LocalDateTime holdExpiresAt,
        String cancelReason,
        LocalDateTime createdAt,
        List<BookingItemResponse> items
) {}
