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

        boolean accepted = service.handleCashOnRails("{\"reference\":\"ZP-1\"}", "bad", null);

        assertThat(accepted).isFalse();
        verify(confirmationService, never()).confirmByReference(anyString());
    }

    @Test
    void rejectsEmptyPayload() {
        boolean accepted = service.handleCashOnRails("  ", "sig", null);
        assertThat(accepted).isFalse();
        verifyNoInteractions(paymentProvider);
    }

    @Test
    void processesAuthenticWebhookAndExtractsNestedReference() {
        ReflectionTestUtils.setField(service, "webhookKey", "");
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(true);
        when(confirmationService.confirmByReference("ZP-99")).thenReturn("COMPLETED");

        boolean accepted = service.handleCashOnRails(
                "{\"event\":\"charge.success\",\"data\":{\"reference\":\"ZP-99\"}}", "sig", null);

        assertThat(accepted).isTrue();
        verify(confirmationService).confirmByReference("ZP-99");
    }

    @Test
    void rejectsWhenBearerKeyConfiguredButMissing() {
        ReflectionTestUtils.setField(service, "webhookKey", "topsecret");
        when(paymentProvider.verifyWebhookSignature(anyString(), any())).thenReturn(true);

        boolean accepted = service.handleCashOnRails(
                "{\"reference\":\"ZP-1\"}", "sig", null);

        assertThat(accepted).isFalse();
        verify(confirmationService, never()).confirmByReference(anyString());
    }
}
