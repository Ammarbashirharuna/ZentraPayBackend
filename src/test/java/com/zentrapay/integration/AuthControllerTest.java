package com.zentrapay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.controller.AuthController;
import com.zentrapay.dto.auth.AuthResponse;
import com.zentrapay.dto.auth.LoginRequest;
import com.zentrapay.dto.auth.RegisterRequest;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.EmailNotVerifiedException;
import com.zentrapay.exception.InvalidCredentialsException;
import com.zentrapay.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    private AuthResponse authResponse() {
        return AuthResponse.builder()
                .token("jwt-token-123")
                .tokenType("Bearer")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .user(AuthResponse.UserDTO.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .fullName("Test User")
                        .emailVerified(true)
                        .createdAt(LocalDateTime.now())
                        .build())
                .build();
    }

    // ── Register ────────────────────────────────────────────────────────────

    @Test
    void registerReturns201WithToken() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("test@example.com", "Passw0rd!", "Test User"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token-123"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));
    }

    @Test
    void registerReturns409OnDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Email already registered"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("taken@example.com", "Passw0rd!", "Taken"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void registerReturns400OnInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Login ───────────────────────────────────────────────────────────────

    @Test
    void loginReturns200WithToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("jwt-token-123"));
    }

    @Test
    void loginReturns401OnBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void loginReturns403OnUnverifiedEmail() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new EmailNotVerifiedException("Please verify your email"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "Passw0rd!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("AUTH_EMAIL_NOT_VERIFIED"));
    }

    // ── Verify Email ────────────────────────────────────────────────────────

    @Test
    void verifyEmailRedirectsOnSuccess() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(get("/api/v1/auth/verify").param("token", "valid-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/verify-email?status=success"));
    }

    @Test
    void verifyEmailReturns400OnInvalidToken() throws Exception {
        doThrow(new RuntimeException("Invalid verification token"))
                .when(authService).verifyEmail("bad-token");

        mockMvc.perform(get("/api/v1/auth/verify").param("token", "bad-token"))
                .andExpect(status().isBadRequest());
    }
}
