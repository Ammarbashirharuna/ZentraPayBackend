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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentLinkControllerTest extends AbstractControllerTest {

    @Autowired ObjectMapper objectMapper;
    @MockitoBean PaymentLinkService paymentLinkService;

    private PaymentLinkResponse linkResponse() {
        return PaymentLinkResponse.builder()
                .id(UUID.randomUUID()).shortCode("ABC1234")
                .paymentUrl("http://localhost:8080/api/v1/pay/ABC1234")
                .title("Coffee").amount(5000L).currency("NGN")
                .status("ACTIVE").singleUse(false).currentUses(0)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void createReturns201() throws Exception {
        when(paymentLinkService.createPaymentLink(any(CreatePaymentLinkRequest.class))).thenReturn(linkResponse());
        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentLinkRequest.builder().title("Coffee").amount(5000L).currency("NGN").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shortCode").value("ABC1234"));
    }

    @Test
    void createReturns422OnBusinessRuleViolation() throws Exception {
        when(paymentLinkService.createPaymentLink(any()))
                .thenThrow(new BusinessRuleException("NO_PAYOUT_ACCOUNT", "Set up payout account."));
        mockMvc.perform(post("/api/v1/payment-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentLinkRequest.builder().title("Coffee").amount(5000L).currency("NGN").build())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("NO_PAYOUT_ACCOUNT"));
    }

    @Test
    void listReturns200() throws Exception {
        when(paymentLinkService.listMyLinks(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(linkResponse()), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/payment-links").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].shortCode").value("ABC1234"));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(paymentLinkService.getMyLink(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Not found"));
        mockMvc.perform(get("/api/v1/payment-links/" + UUID.randomUUID()))
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
