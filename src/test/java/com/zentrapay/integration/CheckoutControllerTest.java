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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CheckoutControllerTest extends AbstractControllerTest {

    @Autowired ObjectMapper objectMapper;
    @MockitoBean CheckoutService checkoutService;
    @MockitoBean PaymentConfirmationService paymentConfirmationService;

    @Test
    void viewReturns200() throws Exception {
        when(checkoutService.getPublicPaymentLink("ABC1234")).thenReturn(
                PublicPaymentLinkResponse.builder().title("Coffee").amount(5000L).currency("NGN").build());
        mockMvc.perform(get("/api/v1/pay/ABC1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Coffee"));
    }

    @Test
    void viewReturns404WhenNotFound() throws Exception {
        when(checkoutService.getPublicPaymentLink("FAKE"))
                .thenThrow(new ResourceNotFoundException("Not found"));
        mockMvc.perform(get("/api/v1/pay/FAKE")).andExpect(status().isNotFound());
    }

    @Test
    void viewReturns422WhenExpired() throws Exception {
        when(checkoutService.getPublicPaymentLink("EXPIRED"))
                .thenThrow(new BusinessRuleException("LINK_EXPIRED", "Expired."));
        mockMvc.perform(get("/api/v1/pay/EXPIRED"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("LINK_EXPIRED"));
    }

    @Test
    void payReturns200WithCheckoutUrl() throws Exception {
        when(checkoutService.initiatePayment(anyString(), any(InitiatePaymentRequest.class)))
                .thenReturn(InitiatePaymentResponse.builder()
                        .reference("ZP-1").checkoutUrl("https://checkout.paystack.com/abc").build());
        mockMvc.perform(post("/api/v1/pay/ABC1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerEmail\":\"buyer@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.paystack.com/abc"));
    }

    @Test
    void callbackReturns200() throws Exception {
        when(paymentConfirmationService.confirmByReference("ZP-1")).thenReturn("COMPLETED");
        mockMvc.perform(get("/api/v1/pay/callback").param("reference", "ZP-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("COMPLETED"));
    }
}
