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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest extends AbstractControllerTest {

    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthService authService;

    private AuthResponse authResponse() {
        return AuthResponse.builder()
                .token("jwt-token-123").tokenType("Bearer")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .user(AuthResponse.UserDTO.builder()
                        .id(UUID.randomUUID()).email("test@example.com")
                        .fullName("Test User").emailVerified(true)
                        .createdAt(LocalDateTime.now()).build())
                .build();
    }

    @Test
    void registerReturns201() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("test@example.com", "Passw0rd!", "Test User"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").value("jwt-token-123"));
    }

    @Test
    void registerReturns409OnDuplicate() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateResourceException("Email already registered"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("taken@example.com", "Passw0rd!", "Taken"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    void registerReturns400OnInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturns200() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "Passw0rd!"))))
                .andExpect(status().isOk())
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
                .andExpect(jsonPath("$.message").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void loginReturns403OnUnverifiedEmail() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new EmailNotVerifiedException("Please verify"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "Passw0rd!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("AUTH_EMAIL_NOT_VERIFIED"));
    }

    @Test
    void verifyEmailRedirectsOnSuccess() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");
        mockMvc.perform(get("/api/v1/auth/verify").param("token", "valid-token"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "http://localhost:3000/verify-email?status=success"));
    }

    @Test
    void verifyEmailReturns400OnInvalidToken() throws Exception {
        doThrow(new RuntimeException("Invalid token"))
                .when(authService).verifyEmail("bad-token");
        mockMvc.perform(get("/api/v1/auth/verify").param("token", "bad-token"))
                .andExpect(status().isInternalServerError());
    }
}
