package com.zentrapay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.controller.PaymentLinkController;
import com.zentrapay.dto.paymentlink.CreatePaymentLinkRequest;
import com.zentrapay.dto.paymentlink.PaymentLinkResponse;
import com.zentrapay.exception.BusinessRuleException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.PaymentLinkService;
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
@WebMvcTest(PaymentLinkController.class)
class PaymentLinkControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PaymentLinkService paymentLinkService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    private PaymentLinkResponse linkResponse() {
        return PaymentLinkResponse.builder()
                .id(UUID.randomUUID())
                .shortCode("ABC1234")
                .paymentUrl("http://localhost:8080/api/v1/pay/ABC1234")
                .title("Coffee")
                .amount(5000L)
                .currency("NGN")
                .status("ACTIVE")
                .singleUse(false)
                .currentUses(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createReturns201WithLink() throws Exception {
        when(paymentLinkService.createPaymentLink(any(CreatePaymentLinkRequest.class)))
                .thenReturn(linkResponse());

        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentLinkRequest.builder()
                                        .title("Coffee").amount(5000L).currency("NGN").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shortCode").value("ABC1234"))
                .andExpect(jsonPath("$.data.title").value("Coffee"));
    }

    @Test
    void createReturns422OnBusinessRuleViolation() throws Exception {
        when(paymentLinkService.createPaymentLink(any(CreatePaymentLinkRequest.class)))
                .thenThrow(new BusinessRuleException("NO_PAYOUT_ACCOUNT", "Set up a payout account first."));

        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentLinkRequest.builder()
                                        .title("Coffee").amount(5000L).currency("NGN").build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NO_PAYOUT_ACCOUNT"));
    }

    @Test
    void listReturns200WithPage() throws Exception {
        when(paymentLinkService.listMyLinks(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(linkResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payment-links")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].shortCode").value("ABC1234"));
    }

    @Test
    void getReturns200WithLink() throws Exception {
        when(paymentLinkService.getMyLink(any(UUID.class))).thenReturn(linkResponse());

        mockMvc.perform(get("/api/v1/payment-links/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Coffee"));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        UUID fakeId = UUID.randomUUID();
        when(paymentLinkService.getMyLink(fakeId))
                .thenThrow(new ResourceNotFoundException("Payment link not found"));

        mockMvc.perform(get("/api/v1/payment-links/" + fakeId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns200() throws Exception {
        doNothing().when(paymentLinkService).deleteMyLink(any(UUID.class));

        mockMvc.perform(delete("/api/v1/payment-links/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
