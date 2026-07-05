package com.badmintonhub.chat.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Hand a thread to another staff member (§G.7). */
public record TransferRequest(@NotNull UUID toStaffId) {
}
