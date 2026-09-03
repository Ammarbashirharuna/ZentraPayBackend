package com.zentrapay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.entity.WebhookEvent;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.ProviderStatus;
import com.zentrapay.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Processes inbound Paystack webhooks.
 *
 * Paystack sends:
 * - charge.success, charge.failed, charge.abandoned — payment events
 * - transfer.success, transfer.failed, transfer.pending — payout events
 * - transfer.reversed — payout reversal
 *
 * Signature: x-paystack-signature header = HMAC-SHA512 of raw body using
 * the Paystack secret key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final String PROVIDER_TYPE = "PAYSTACK";

    private final PaymentProvider paymentProvider;
    private final PaymentConfirmationService paymentConfirmationService;
    private final PayoutService payoutService;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    public boolean handlePaystack(String rawPayload, String signature) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("Empty webhook payload received");
            return false;
        }

        if (!paymentProvider.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Webhook signature verification failed");
            return false;
        }

        WebhookEvent event = persistEvent(rawPayload, signature);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.has("event") ? root.get("event").asText() : null;
            markEventType(event, eventType);

            JsonNode data = root.get("data");
            if (data == null) {
                log.warn("Webhook payload has no data field; acknowledging without action");
                markProcessed(event, "No data field in payload");
                return true;
            }

            String reference = text(data, "reference");
            if (reference == null) {
                log.warn("Webhook payload had no reference; acknowledging without action");
                markProcessed(event, "No reference in payload");
                return true;
            }

            // Transfer events update the payout, everything else confirms a payment
            if (isTransferEvent(eventType, reference)) {
                String rawStatus = text(data, "status");
                payoutService.applyTransferStatus(
                        reference, ProviderStatus.fromRaw(rawStatus), rawStatus);
                log.info("Transfer webhook processed for payout {} -> {}", reference, rawStatus);
            } else {
                String status = paymentConfirmationService.confirmByReference(reference);
                log.info("Webhook processed for reference {} -> {}", reference, status);
            }
            markProcessed(event, null);
        } catch (Exception ex) {
            log.error("Failed to process authentic webhook: {}", ex.getMessage(), ex);
            markFailed(event, ex);
        }
        return true;
    }

    private WebhookEvent persistEvent(String rawPayload, String signature) {
        try {
            return webhookEventRepository.save(WebhookEvent.builder()
                    .providerType(PROVIDER_TYPE)
                    .payload(rawPayload)
                    .signature(signature)
                    .processed(false)
                    .retryCount(0)
                    .build());
        } catch (Exception ex) {
            log.error("Could not persist webhook event: {}", ex.getMessage());
            return null;
        }
    }

    private void markEventType(WebhookEvent event, String eventType) {
        if (event == null) return;
        if (eventType != null) {
            event.setEventType(eventType);
        }
    }

    private void markProcessed(WebhookEvent event, String note) {
        if (event == null) return;
        try {
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            event.setErrorMessage(note);
            webhookEventRepository.save(event);
        } catch (Exception ex) {
            log.error("Could not update webhook event {}: {}", event.getId(), ex.getMessage());
        }
    }

    private void markFailed(WebhookEvent event, Exception cause) {
        if (event == null) return;
        try {
            event.setProcessed(false);
            event.setErrorMessage(cause.getMessage());
            webhookEventRepository.save(event);
        } catch (Exception ex) {
            log.error("Could not record webhook failure {}: {}", event.getId(), ex.getMessage());
        }
    }

    private boolean isTransferEvent(String eventType, String reference) {
        if (reference != null && reference.startsWith("PO-")) {
            return true;
        }
        return eventType != null && eventType.toLowerCase().startsWith("transfer");
    }

    private String text(JsonNode node, String key) {
        if (node == null) return null;
        JsonNode value = node.get(key);
        return value != null && !value.isNull() ? value.asText() : null;
    }
}
