package com.zentrapay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.entity.WebhookEvent;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.ProviderStatus;
import com.zentrapay.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Processes inbound CashOnRails webhooks.
 *
 * Order matters for security: verify authenticity (signature, then the optional
 * bearer key) BEFORE parsing or acting on anything in the body. Only then do we
 * extract our reference and hand off to {@link PaymentConfirmationService},
 * which is itself idempotent so repeated deliveries are harmless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private static final String PROVIDER_TYPE = "CASHONRAILS";

    private final PaymentProvider paymentProvider;
    private final PaymentConfirmationService paymentConfirmationService;
    private final PayoutService payoutService;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    /** Optional shared secret expected in the webhook Authorization header. */
    @Value("${cashonrails.webhook-key:}")
    private String webhookKey;

    /**
     * @return true if the webhook was authentic and accepted; false if the
     *         signature/auth was invalid (caller should return 401).
     */
    public boolean handleCashOnRails(String rawPayload, String signature, String authorization) {
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("Empty webhook payload received");
            return false;
        }

        if (!paymentProvider.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Webhook signature verification failed");
            return false;
        }

        // Optional second factor: a static bearer key, if configured.
        if (webhookKey != null && !webhookKey.isBlank()) {
            String expected = "Bearer " + webhookKey;
            if (authorization == null
                    || !constantTimeEquals(authorization, expected)) {
                log.warn("Webhook Authorization header did not match configured key");
                return false;
            }
        }

        // Authentic: record it before acting, so we have an audit trail even if
        // processing throws. Only authenticated events are persisted.
        WebhookEvent event = persistEvent(rawPayload, signature);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            markEventType(event, root);
            String reference = extractReference(root);
            if (reference == null) {
                log.warn("Webhook payload had no usable reference; acknowledging without action");
                markProcessed(event, "No usable reference in payload");
                return true;
            }

            // Our payout references are prefixed PO-; a transfer/payout event
            // updates the settlement, everything else confirms a payment.
            if (isTransferEvent(root, reference)) {
                String rawStatus = extractStatus(root);
                payoutService.applyTransferStatus(
                        reference, ProviderStatus.fromRaw(rawStatus), rawStatus);
                log.info("Transfer webhook processed for payout {} -> {}", reference, rawStatus);
            } else {
                String status = paymentConfirmationService.confirmByReference(reference);
                log.info("Webhook processed for reference {} -> {}", reference, status);
            }
            markProcessed(event, null);
        } catch (Exception ex) {
            // Authentic but unprocessable: log, record the failure for later
            // replay, and still acknowledge so the provider does not hammer us.
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
            // Never let an audit-write failure break webhook handling.
            log.error("Could not persist webhook event: {}", ex.getMessage());
            return null;
        }
    }

    private void markEventType(WebhookEvent event, JsonNode root) {
        if (event == null) {
            return;
        }
        JsonNode ev = root.get("event");
        if (ev != null && ev.isTextual()) {
            event.setEventType(ev.asText());
        }
    }

    private void markProcessed(WebhookEvent event, String note) {
        if (event == null) {
            return;
        }
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
        if (event == null) {
            return;
        }
        try {
            event.setProcessed(false);
            event.setErrorMessage(cause.getMessage());
            webhookEventRepository.save(event);
        } catch (Exception ex) {
            log.error("Could not record webhook failure {}: {}", event.getId(), ex.getMessage());
        }
    }

    /**
     * A transfer/payout event, identified by either the event name (transfer.*)
     * or our PO- payout reference prefix. The prefix check is the reliable
     * signal since we control it; the event name is a secondary hint.
     */
    private boolean isTransferEvent(JsonNode root, String reference) {
        if (reference != null && reference.startsWith("PO-")) {
            return true;
        }
        JsonNode ev = root.get("event");
        return ev != null && ev.isTextual() && ev.asText().toLowerCase().startsWith("transfer");
    }

    /** Pull the status string out of the payload (top-level or nested in data). */
    private String extractStatus(JsonNode root) {
        JsonNode status = root.get("status");
        if (status != null && status.isTextual()) {
            return status.asText();
        }
        JsonNode data = root.get("data");
        if (data != null) {
            JsonNode nested = data.get("status");
            if (nested != null && nested.isTextual()) {
                return nested.asText();
            }
        }
        return null;
    }

    /** Pull our transaction reference out of the (provider-shaped) payload. */
    private String extractReference(JsonNode root) {
        for (String path : new String[]{"reference", "data"}) {
            JsonNode node = root.get(path);
            if (node == null) {
                continue;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            JsonNode ref = node.get("reference");
            if (ref != null && ref.isTextual()) {
                return ref.asText();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
