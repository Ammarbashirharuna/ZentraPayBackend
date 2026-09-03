package com.zentrapay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.controller.ApiKeyController;
import com.zentrapay.dto.apikey.ApiKeyResponse;
import com.zentrapay.dto.apikey.CreateApiKeyRequest;
import com.zentrapay.dto.apikey.CreateApiKeyResponse;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ApiKeyController.class)
class ApiKeyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ApiKeyService apiKeyService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    @Test
    void createReturns201WithRawKey() throws Exception {
        when(apiKeyService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenReturn(CreateApiKeyResponse.builder()
                        .id(UUID.randomUUID())
                        .name("Production")
                        .rawKey("zp_abc123def456")
                        .keyPrefix("zp_abc")
                        .build());

        mockMvc.perform(post("/api/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Production\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rawKey").value("zp_abc123def456"))
                .andExpect(jsonPath("$.data.name").value("Production"));
    }

    @Test
    void listReturns200WithKeys() throws Exception {
        when(apiKeyService.listMyApiKeys(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(
                        ApiKeyResponse.builder()
                                .id(UUID.randomUUID()).name("Production")
                                .keyPrefix("zp_abc").isActive(true)
                                .createdAt(LocalDateTime.now()).build()
                ), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Production"));
    }

    @Test
    void revokeReturns200() throws Exception {
        doNothing().when(apiKeyService).revokeApiKey(any(UUID.class));

        mockMvc.perform(delete("/api/v1/api-keys/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void revokeReturns404WhenNotFound() throws Exception {
        UUID fakeId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("API key not found"))
                .when(apiKeyService).revokeApiKey(fakeId);

        mockMvc.perform(delete("/api/v1/api-keys/" + fakeId))
                .andExpect(status().isNotFound());
    }
}
