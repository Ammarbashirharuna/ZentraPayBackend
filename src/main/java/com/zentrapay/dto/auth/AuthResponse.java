package com.zentrapay.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for authentication response (register/login)
 * Contains: JWT token and user information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private LocalDateTime expiresAt;
    private UserDTO user;

    /**
     * Nested DTO for user information
     * Only includes safe fields (NO password hash!)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDTO {
        private UUID id;
        private String email;
        private String fullName;
        private Boolean emailVerified;
        private LocalDateTime createdAt;
    }
}