package com.badmintonhub.user.dto.request;

import jakarta.validation.constraints.Size;

/** PATCH /api/users/{id} — only profile fields are mutable (email/roles are not changed here). */
public record UpdateUserRequest(
        @Size(max = 255) String fullName,
        @Size(max = 20) String phone
) {}
