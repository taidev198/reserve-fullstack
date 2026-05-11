package com.cmc.warehouse.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Machine-readable error details nested under {@link ApiResponse#data()} on failures.
 */
public record ApiErrorData(
        String code,
        List<FieldError> details,
        Instant timestamp
) {
    public record FieldError(String field, String message) {}

    public static ApiErrorData of(String code) {
        return new ApiErrorData(code, List.of(), Instant.now());
    }

    public static ApiErrorData of(String code, List<FieldError> details) {
        return new ApiErrorData(code, details, Instant.now());
    }
}
