package com.badmintonhub.chat.dto.response;

/**
 * Wraps a create-or-return result so the controller can distinguish a fresh insert (201) from an
 * idempotent return of an existing record (200) — used by ensure-open and send (P0-5, §C).
 */
public record Persisted<T>(T value, boolean created) {

    public static <T> Persisted<T> created(T value) {
        return new Persisted<>(value, true);
    }

    public static <T> Persisted<T> existing(T value) {
        return new Persisted<>(value, false);
    }
}
