package com.zentrapay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    // Success response with data
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                LocalDateTime.now()
        );
    }

    // Success response without data
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(
                true,
                message,
                null,
                null,
                LocalDateTime.now()
        );
    }

    // Error response
    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(
                false,
                null,
                null,
                error,
                LocalDateTime.now()
        );
    }

    // Error response with message
    public static <T> ApiResponse<T> error(String message, String error) {
        return new ApiResponse<>(
                false,
                message,
                null,
                error,
                LocalDateTime.now()
        );
    }
}