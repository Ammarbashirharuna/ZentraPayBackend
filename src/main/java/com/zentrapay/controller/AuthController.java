package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.auth.AuthResponse;
import com.zentrapay.dto.auth.LoginRequest;
import com.zentrapay.dto.auth.RegisterRequest;
import com.zentrapay.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication Controller
 *
 * REST API endpoints for authentication:
 * - POST /api/v1/auth/register - Register new user
 * - POST /api/v1/auth/login - Login existing user
 *
 * Controller Layer Responsibilities:
 * - Receive HTTP requests
 * - Validate input (using @Valid)
 * - Call service layer
 * - Return HTTP responses
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Register new user
     *
     * Request Body:
     * {
     *   "email": "ammar@gmail.com",
     *   "password": "MyP@ssw0rd",
     *   "fullName": "Ammar Haruna"
     * }
     *
     * Response (201 Created):
     * {
     *   "success": true,
     *   "message": "User registered successfully",
     *   "data": {
     *     "token": "eyJhbGci...",
     *     "tokenType": "Bearer",
     *     "expiresAt": "2026-01-29T10:30:00",
     *     "user": {
     *       "id": "123e4567-...",
     *       "email": "ammar@gmail.com",
     *       "fullName": "Ammar Haruna",
     *       "emailVerified": false
     *     }
     *   }
     * }
     *
     * @param request Registration data
     * @return AuthResponse wrapped in ApiResponse
     */
    @PostMapping("/register")
    @Operation(
            summary = "Register new user",
            description = "Create a new user account with email and password"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response,
                        "User registered successfully"
                ));
    }

    /**
     * Login existing user
     *
     * Request Body:
     * {
     *   "email": "ammar@gmail.com",
     *   "password": "MyP@ssw0rd"
     * }
     *
     * Response (200 OK):
     * {
     *   "success": true,
     *   "message": "Login successful",
     *   "data": {
     *     "token": "eyJhbGci...",
     *     "tokenType": "Bearer",
     *     "expiresAt": "2026-01-29T10:30:00",
     *     "user": { ... }
     *   }
     * }
     *
     * @param request Login credentials
     * @return AuthResponse wrapped in ApiResponse
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticate user and return JWT token"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);

        return ResponseEntity
                .ok(ApiResponse.success(
                        response,
                        "Login successful"
                ));
    }
}
