package com.zentrapay.integration;

import com.zentrapay.controller.PayoutController;
import com.zentrapay.dto.payout.PayoutResponse;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.PayoutQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PayoutController.class)
class PayoutControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PayoutQueryService payoutQueryService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    private PayoutResponse payoutResponse() {
        return PayoutResponse.builder()
                .id(UUID.randomUUID())
                .reference("PO-ZP-test-1")
                .amount(9_900L)
                .currency("NGN")
                .status("PAID")
                .completedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void listReturns200WithPage() throws Exception {
        when(payoutQueryService.listMyPayouts(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(payoutResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payouts").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reference").value("PO-ZP-test-1"));
    }

    @Test
    void getReturns200WithPayout() throws Exception {
        when(payoutQueryService.getMyPayout(any(UUID.class))).thenReturn(payoutResponse());

        mockMvc.perform(get("/api/v1/payouts/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(9_900));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(payoutQueryService.getMyPayout(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Payout not found"));

        mockMvc.perform(get("/api/v1/payouts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
