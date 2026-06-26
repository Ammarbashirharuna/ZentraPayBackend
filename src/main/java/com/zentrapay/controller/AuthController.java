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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register new user",
            description = "Create a new user account. Returns JWT token immediately. Email verification required before login."
    )
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User registered successfully. Please verify your email."));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login user",
            description = "Authenticate with email and password. Email must be verified first."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Login successful"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid email or password"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Email not verified - check your inbox"
            )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
    /**
     * Verify email address
     *
     * Called when user clicks the link in their verification email.
     *
     * URL example:
     * GET /api/v1/auth/verify?token=a1b2c3d4e5f6...
     *
     * Success response:
     * {
     *   "success": true,
     *   "message": "Email verified successfully. You can now login."
     * }
     */
    @GetMapping("/verify")
    @Operation(
            summary = "Verify email address",
            description = "Verifies user email using the token sent to their inbox"
    )
    public ResponseEntity<ApiResponse<String>> verifyEmail(
            @RequestParam String token
    ) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Email verified successfully. You can now login."
                )
        );
    }
}