package com.zentrapay.integration;

import com.zentrapay.controller.WebhookController;
import com.zentrapay.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean WebhookService webhookService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    @Test
    void paystackReturns200OnValidSignature() throws Exception {
        when(webhookService.handlePaystack(
                "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ZP-1\"}}",
                "valid-signature"))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType("application/json")
                        .header("x-paystack-signature", "valid-signature")
                        .content("{\"event\":\"charge.success\",\"data\":{\"reference\":\"ZP-1\"}}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void paystackReturns401OnInvalidSignature() throws Exception {
        when(webhookService.handlePaystack(any(), eq("bad-sig")))
                .thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType("application/json")
                        .header("x-paystack-signature", "bad-sig")
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid signature"));
    }

    @Test
    void paystackReturns200EvenWithNullPayload() throws Exception {
        when(webhookService.handlePaystack(null, "sig")).thenReturn(false);

        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .header("x-paystack-signature", "sig"))
                .andExpect(status().isUnauthorized());
    }
}
