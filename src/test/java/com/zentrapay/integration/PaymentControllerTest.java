package com.zentrapay.integration;

import com.zentrapay.controller.PaymentController;
import com.zentrapay.dto.payment.AnalyticsResponse;
import com.zentrapay.dto.payment.EarningsSummaryResponse;
import com.zentrapay.dto.payment.PaymentResponse;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.PaymentQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PaymentQueryService paymentQueryService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    private PaymentResponse paymentResponse() {
        return PaymentResponse.builder()
                .id(UUID.randomUUID())
                .providerReference("ZP-test-1")
                .amount(10_000L)
                .platformFee(100L)
                .netAmount(9_900L)
                .currency("NGN")
                .status("COMPLETED")
                .build();
    }

    @Test
    void listReturns200WithPage() throws Exception {
        when(paymentQueryService.listMyPayments(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(paymentResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].providerReference").value("ZP-test-1"));
    }

    @Test
    void getReturns200WithPayment() throws Exception {
        when(paymentQueryService.getMyPayment(any(UUID.class))).thenReturn(paymentResponse());

        mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(10_000));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(paymentQueryService.getMyPayment(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Payment not found"));

        mockMvc.perform(get("/api/v1/payments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void summaryReturns200() throws Exception {
        when(paymentQueryService.getMySummary()).thenReturn(
                EarningsSummaryResponse.builder()
                        .totalGrossCollected(100_000L)
                        .totalPlatformFees(1_000L)
                        .totalNetPaid(99_000L)
                        .totalPaymentsCount(10L)
                        .currencies(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/payments/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalGrossCollected").value(100_000));
    }

    @Test
    void analyticsReturns200() throws Exception {
        when(paymentQueryService.getMyAnalytics()).thenReturn(
                AnalyticsResponse.builder()
                        .overallConversionRate(85.5)
                        .averagePaymentAmount(15_000L)
                        .dailyRevenue(List.of())
                        .linkAnalytics(List.of())
                        .build());

        mockMvc.perform(get("/api/v1/payments/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overallConversionRate").value(85.5));
    }
}
