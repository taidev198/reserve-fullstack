package com.cmc.warehouse.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Unified HTTP JSON envelope for API controllers.
 *
 * @param statusCode HTTP status (mirrors the response status line)
 * @param message    short human-readable summary (e.g. HTTP reason phrase on success)
 * @param data       payload on success, or structured error payload on failure
 * @param url        request path (including query string when present); only set on errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int statusCode, String message, T data, String url) {

    public static <T> ApiResponse<T> success(int statusCode, String message, T data) {
        return new ApiResponse<>(statusCode, message, data, null);
    }

    public static <T> ApiResponse<T> error(int statusCode, String message, T data, String url) {
        return new ApiResponse<>(statusCode, message, data, url);
    }
}
