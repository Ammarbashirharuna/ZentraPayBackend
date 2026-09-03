package com.zentrapay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.controller.CheckoutController;
import com.zentrapay.dto.checkout.InitiatePaymentRequest;
import com.zentrapay.dto.checkout.InitiatePaymentResponse;
import com.zentrapay.dto.checkout.PublicPaymentLinkResponse;
import com.zentrapay.exception.BusinessRuleException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.CheckoutService;
import com.zentrapay.service.PaymentConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CheckoutService checkoutService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;
    @MockitoBean PaymentConfirmationService paymentConfirmationService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    @Test
    void viewReturns200WithPublicLink() throws Exception {
        when(checkoutService.getPublicPaymentLink("ABC1234")).thenReturn(
                PublicPaymentLinkResponse.builder()
                        .title("Coffee").amount(5000L).currency("NGN").build());

        mockMvc.perform(get("/api/v1/pay/ABC1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Coffee"));
    }

    @Test
    void viewReturns404WhenLinkNotFound() throws Exception {
        when(checkoutService.getPublicPaymentLink("FAKE"))
                .thenThrow(new ResourceNotFoundException("Payment link not found"));

        mockMvc.perform(get("/api/v1/pay/FAKE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewReturns422WhenLinkExpired() throws Exception {
        when(checkoutService.getPublicPaymentLink("EXPIRED"))
                .thenThrow(new BusinessRuleException("LINK_EXPIRED", "Link expired."));

        mockMvc.perform(get("/api/v1/pay/EXPIRED"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("LINK_EXPIRED"));
    }

    @Test
    void payReturns200WithCheckoutUrl() throws Exception {
        when(checkoutService.initiatePayment(anyString(), any(InitiatePaymentRequest.class)))
                .thenReturn(InitiatePaymentResponse.builder()
                        .reference("ZP-ref-1")
                        .checkoutUrl("https://checkout.paystack.com/abc")
                        .accessCode("acc-1")
                        .build());

        mockMvc.perform(post("/api/v1/pay/ABC1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerEmail\":\"buyer@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.paystack.com/abc"))
                .andExpect(jsonPath("$.data.reference").value("ZP-ref-1"));
    }

    @Test
    void callbackReturns200WithStatus() throws Exception {
        when(paymentConfirmationService.confirmByReference("ZP-ref-1")).thenReturn("COMPLETED");

        mockMvc.perform(get("/api/v1/pay/callback").param("reference", "ZP-ref-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("COMPLETED"));
    }
}
