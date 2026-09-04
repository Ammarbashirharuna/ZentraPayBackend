package com.zentrapay.integration;

import com.zentrapay.controller.WebhookController;
import com.zentrapay.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebhookControllerTest extends AbstractControllerTest {

    @MockitoBean WebhookService webhookService;

    @Test
    void paystackReturns200OnValidSignature() throws Exception {
        when(webhookService.handlePaystack(anyString(), eq("valid-sig"))).thenReturn(true);
        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType("application/json")
                        .header("x-paystack-signature", "valid-sig")
                        .content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void paystackReturns401OnInvalidSignature() throws Exception {
        when(webhookService.handlePaystack(anyString(), eq("bad-sig"))).thenReturn(false);
        mockMvc.perform(post("/api/v1/webhooks/paystack")
                        .contentType("application/json")
                        .header("x-paystack-signature", "bad-sig")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("invalid signature"));
    }
}
