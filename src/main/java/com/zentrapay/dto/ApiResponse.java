package com.zentrapay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API response envelope.
 *
 * Every endpoint returns this shape:
 * {
 *   "success": true/false,
 *   "message": "optional message",
 *   "data": "optional payload",
 *   "error": "error code when success=false",
 *   "timestamp": "when the response was created"
 * }
 *
 * Error codes follow the pattern CATEGORY_DETAIL:
 * - AUTH_INVALID_CREDENTIALS
 * - RESOURCE_NOT_FOUND
 * - VALIDATION_FAILED
 * - PROVIDER_ERROR
 * - etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private Boolean success;
    private String message;
    private T data;
    private String error;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, null, error, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String message, String error) {
        return new ApiResponse<>(false, message, null, error, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, message, data, code, LocalDateTime.now());
    }
}
