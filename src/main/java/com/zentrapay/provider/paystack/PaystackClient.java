package com.zentrapay.provider.paystack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.provider.AccountValidationRequest;
import com.zentrapay.provider.AccountValidationResult;
import com.zentrapay.provider.InitializeRequest;
import com.zentrapay.provider.InitializeResult;
import com.zentrapay.provider.PayoutRequest;
import com.zentrapay.provider.PayoutResult;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.PaymentProviderException;
import com.zentrapay.provider.ProviderStatus;
import com.zentrapay.provider.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paystack implementation of {@link PaymentProvider}.
 *
 * Endpoints used (from https://paystack.com/docs/api):
 * <ul>
 *   <li>{@code POST /transaction/initialize} — start a checkout</li>
 *   <li>{@code GET  /transaction/verify/{reference}} — verify a transaction</li>
 *   <li>{@code GET  /bank/resolve} — resolve/validate a bank account</li>
 *   <li>{@code POST /transferrecipient} — create a transfer recipient for payouts</li>
 *   <li>{@code POST /transfer} — initiate a transfer/payout</li>
 * </ul>
 *
 * Auth: {@code Authorization: Bearer <secret key>} on every call.
 * Webhooks are verified by HMAC-SHA512 of the raw body using the secret key.
 */
@Component
@Slf4j
public class PaystackClient implements PaymentProvider {

    private static final String BASE_URL = "https://api.paystack.co";

    private final String secretKey;
    private final String callbackBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaystackClient(
            @Value("${paystack.secret-key:}") String secretKey,
            @Value("${app.base-url:http://localhost:8080}") String callbackBaseUrl
    ) {
        this.secretKey = secretKey;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    // ── Initialize ──────────────────────────────────────────────────────────

    @Override
    public InitializeResult initialize(InitializeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", request.email());
        body.put("amount", request.amount());   // Paystack expects amount in kobo/cents
        body.put("reference", request.reference());
        body.put("callback_url", callbackBaseUrl + "/api/v1/pay/callback");
        // Optional metadata
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("currency", request.currency());
        if (request.redirectUrl() != null) {
            metadata.put("redirect_url", request.redirectUrl());
        }
        body.put("metadata", metadata);
        // Set currency if not NGN (default)
        if (request.currency() != null && !"NGN".equalsIgnoreCase(request.currency())) {
            body.put("currency", request.currency());
        }

        JsonNode data = postJson("/transaction/initialize", toJson(body));
        return InitializeResult.builder()
                .checkoutUrl(text(data, "authorization_url", "access_code"))
                .accessCode(text(data, "access_code"))
                .reference(firstNonBlank(text(data, "reference"), request.reference()))
                .build();
    }

    // ── Verify ──────────────────────────────────────────────────────────────

    @Override
    public VerificationResult verify(String reference) {
        JsonNode data = getJson("/transaction/verify/" + reference);
        JsonNode transNode = data.get("transaction") != null ? data.get("transaction") : data;
        String rawStatus = text(transNode, "status");

        // Paystack statuses: success, failed, abandoned, pending
        ProviderStatus status;
        if ("success".equalsIgnoreCase(rawStatus)) {
            status = ProviderStatus.SUCCESS;
        } else if ("failed".equalsIgnoreCase(rawStatus)) {
            status = ProviderStatus.FAILED;
        } else if ("abandoned".equalsIgnoreCase(rawStatus)) {
            status = ProviderStatus.ABANDONED;
        } else if ("pending".equalsIgnoreCase(rawStatus) || "otp".equalsIgnoreCase(rawStatus)
                || "send_otp".equalsIgnoreCase(rawStatus) || "requery".equalsIgnoreCase(rawStatus)) {
            status = ProviderStatus.PENDING;
        } else {
            status = ProviderStatus.UNKNOWN;
        }

        // Paystack amount is in kobo — convert back if needed
        long amount = asLong(transNode, "amount");
        String currency = text(transNode, "currency");

        return VerificationResult.builder()
                .status(status)
                .amount(amount)
                .currency(currency)
                .reference(firstNonBlank(text(transNode, "reference"), reference))
                .rawStatus(rawStatus)
                .build();
    }

    // ── Account Validation ──────────────────────────────────────────────────

    @Override
    public AccountValidationResult validateAccount(AccountValidationRequest request) {
        try {
            String path = "/bank/resolve?account_number=" + request.accountNumber()
                    + "&bank_code=" + request.bankCode();
            JsonNode data = getJson(path);
            String name = text(data, "account_name");
            boolean valid = name != null && !name.isBlank();
            return AccountValidationResult.builder()
                    .valid(valid)
                    .accountName(name)
                    .build();
        } catch (PaymentProviderException e) {
            log.warn("Account validation failed for bank_code {}: {}", request.bankCode(), e.getMessage());
            return AccountValidationResult.builder().valid(false).accountName(null).build();
        }
    }

    // ── Payout (Transfer) ───────────────────────────────────────────────────

    @Override
    public PayoutResult payout(PayoutRequest request) {
        // Step 1: Create a transfer recipient
        String recipientCode = createTransferRecipient(request);

        // Step 2: Initiate the transfer
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "balance");
        body.put("amount", request.amount());
        body.put("recipient", recipientCode);
        body.put("reason", request.narration() != null ? request.narration() : "Zetapay payout");
        if (request.reference() != null) {
            body.put("reference", request.reference());
        }

        JsonNode data = postJson("/transfer", toJson(body));
        String rawStatus = text(data, "status");

        ProviderStatus status;
        if ("success".equalsIgnoreCase(rawStatus) || "pending".equalsIgnoreCase(rawStatus)
                || "otp".equalsIgnoreCase(rawStatus) || "otp_pending".equalsIgnoreCase(rawStatus)) {
            status = rawStatus.contains("success") ? ProviderStatus.SUCCESS : ProviderStatus.PENDING;
        } else if ("failed".equalsIgnoreCase(rawStatus) || "reversed".equalsIgnoreCase(rawStatus)) {
            status = ProviderStatus.FAILED;
        } else {
            status = ProviderStatus.UNKNOWN;
        }

        return PayoutResult.builder()
                .status(status)
                .providerReference(firstNonBlank(text(data, "transfer_code"), request.reference()))
                .rawStatus(rawStatus)
                .build();
    }

    private String createTransferRecipient(PayoutRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "nuban");
        body.put("name", request.accountName());
        body.put("account_number", request.accountNumber());
        body.put("bank_code", request.bankCode());
        body.put("currency", request.currency());

        JsonNode data = postJson("/transferrecipient", toJson(body));
        String code = text(data, "recipient_code");
        if (code == null || code.isBlank()) {
            throw new PaymentProviderException("Failed to create Paystack transfer recipient");
        }
        return code;
    }

    // ── Webhook Signature ───────────────────────────────────────────────────

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Webhook rejected: missing x-paystack-signature header");
            return false;
        }
        if (secretKey == null || secretKey.isBlank()) {
            log.error("Webhook rejected: Paystack secret key not configured");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ── HTTP Plumbing ───────────────────────────────────────────────────────

    private JsonNode postJson(String path, String jsonBody) {
        requireSecretKey();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(path, response);
        } catch (PaymentProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Paystack request interrupted: " + path, e);
        } catch (Exception e) {
            throw new PaymentProviderException("Paystack request failed: " + path, e);
        }
    }

    private JsonNode getJson(String path) {
        requireSecretKey();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + secretKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(path, response);
        } catch (PaymentProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Paystack request interrupted: " + path, e);
        } catch (Exception e) {
            throw new PaymentProviderException("Paystack request failed: " + path, e);
        }
    }

    private JsonNode parseResponse(String path, HttpResponse<String> response) throws Exception {
        JsonNode root = objectMapper.readTree(response.body());
        boolean httpOk = response.statusCode() >= 200 && response.statusCode() < 300;
        boolean apiOk = root.has("status") && root.get("status").asBoolean();

        if (!httpOk || !apiOk) {
            String message = root.has("message") ? root.get("message").asText() : "unknown error";
            log.warn("Paystack {} returned HTTP {} status={} message={}",
                    path, response.statusCode(),
                    root.has("status") ? root.get("status") : "?", message);
            throw new PaymentProviderException("Paystack error: " + message);
        }
        JsonNode data = root.get("data");
        return data != null && !data.isNull() ? data : root;
    }

    private void requireSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentProviderException("Paystack secret key not configured (paystack.secret-key)");
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new PaymentProviderException("Failed to serialize request body", e);
        }
    }

    private String text(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private long asLong(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isNumber() ? value.asLong()
                : value != null && value.asText().matches("-?\\d+") ? Long.parseLong(value.asText())
                : 0L;
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
