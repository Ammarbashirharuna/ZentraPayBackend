package com.zentrapay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock PaymentProvider paymentProvider;
    @Mock PaymentConfirmationService confirmationService;
    @Mock PayoutService payoutService;
    @Mock com.zentrapay.repository.WebhookEventRepository webhookEventRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks WebhookService service;

    @Test
    void rejectsInvalidSignatureWithoutProcessing() {
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(false);

        boolean accepted = service.handlePaystack("{\"reference\":\"ZP-1\"}", "bad");

        assertThat(accepted).isFalse();
        verify(confirmationService, never()).confirmByReference(anyString());
    }

    @Test
    void rejectsEmptyPayload() {
        boolean accepted = service.handlePaystack("  ", "sig");
        assertThat(accepted).isFalse();
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void processesAuthenticWebhookAndExtractsNestedReference() {
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(true);
        when(confirmationService.confirmByReference("ZP-99")).thenReturn("COMPLETED");

        boolean accepted = service.handlePaystack(
                "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ZP-99\"}}", "sig");

        assertThat(accepted).isTrue();
        verify(confirmationService).confirmByReference("ZP-99");
    }

    @Test
    void processesTransferWebhook() {
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(true);

        boolean accepted = service.handlePaystack(
                "{\"event\":\"transfer.success\",\"data\":{\"reference\":\"PO-ZP-1\",\"status\":\"success\"}}", "sig");

        assertThat(accepted).isTrue();
        verify(payoutService).applyTransferStatus("PO-ZP-1",
                com.zentrapay.provider.ProviderStatus.SUCCESS, "success");
    }

    @Test
    void acknowledgesWebhookEvenOnProcessingFailure() {
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(true);
        when(confirmationService.confirmByReference("ZP-err"))
                .thenThrow(new RuntimeException("db error"));

        boolean accepted = service.handlePaystack(
                "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ZP-err\"}}", "sig");

        // Still returns true (200) so Paystack doesn't retry, but error is logged
        assertThat(accepted).isTrue();
    }
}
