package com.zentrapay.provider.cashonrails;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CashOnRails implementation of {@link PaymentProvider}.
 *
 * Base URL and endpoints (from https://developer.cashonrails.com/):
 * <ul>
 *   <li>{@code POST /transaction/initialize} — start a hosted checkout</li>
 *   <li>{@code GET  /s2s/transaction/verify/{reference}} — verify a transaction</li>
 *   <li>{@code POST /account_name} — resolve/validate a payout account</li>
 *   <li>{@code POST /bank_transfer} — pay out to a seller (signed)</li>
 * </ul>
 *
 * Auth: {@code Authorization: Bearer <secret key>} on every call. Payouts add
 * {@code Signature} (HMAC-512) and {@code X-Signature} (RSA-SHA256) headers via
 * {@link CashOnRailsSigner}.
 *
 * All CashOnRails responses share the envelope {@code {status, message, data}}.
 */
@Component
@Slf4j
public class
CashOnRailsClient implements PaymentProvider {

    private final String baseUrl;
    private final String secretKey;
    private final String senderName;
    private final CashOnRailsSigner signer;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CashOnRailsClient(
            @Value("${cashonrails.base-url:https://mainapi.cashonrails.com/api/v1}") String baseUrl,
            @Value("${cashonrails.secret-key:}") String secretKey,
            @Value("${cashonrails.sender-name:ZentraPay}") String senderName,
            CashOnRailsSigner signer
    ) {
        this.baseUrl = baseUrl;
        this.secretKey = secretKey;
        this.senderName = senderName;
        this.signer = signer;
    }

    @Override
    public InitializeResult initialize(InitializeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", request.amount());
        body.put("currency", request.currency());
        body.put("email", request.email());
        body.put("reference", request.reference());
        body.put("redirectUrl", request.redirectUrl());

        JsonNode data = postJson("/transaction/initialize", toJson(body), false);
        return InitializeResult.builder()
                .checkoutUrl(text(data, "checkout_link", "authorization_url", "checkout_url"))
                .accessCode(text(data, "access_code"))
                .reference(text(data, "reference"))
                .build();
    }

    @Override
    public VerificationResult verify(String reference) {
        JsonNode data = getJson("/s2s/transaction/verify/" + reference);
        String rawStatus = text(data, "status");
        return VerificationResult.builder()
                .status(ProviderStatus.fromRaw(rawStatus))
                .amount(asLong(data, "amount"))
                .currency(text(data, "currency"))
                .reference(firstNonBlank(text(data, "reference"), reference))
                .rawStatus(rawStatus)
                .build();
    }

    @Override
    public AccountValidationResult validateAccount(AccountValidationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_number", request.accountNumber());
        body.put("bank_code", request.bankCode());
        body.put("currency", request.currency());

        try {
            JsonNode data = postJson("/account_name", toJson(body), false);
            String name = text(data, "account_name");
            boolean valid = name != null && !name.isBlank();
            return AccountValidationResult.builder()
                    .valid(valid)
                    .accountName(name)
                    .build();
        } catch (PaymentProviderException e) {
            log.warn("Account validation failed for currency {}: {}", request.currency(), e.getMessage());
            return AccountValidationResult.builder().valid(false).accountName(null).build();
        }
    }

    @Override
    public PayoutResult payout(PayoutRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("account_number", request.accountNumber());
        body.put("account_name", request.accountName());
        body.put("bank_code", request.bankCode());
        body.put("amount", String.valueOf(request.amount()));
        body.put("currency", request.currency());
        body.put("sender_name", request.senderName() != null ? request.senderName() : senderName);
        body.put("narration", request.narration());
        body.put("reference", request.reference());
        if (request.type() != null && !request.type().isBlank()) {
            body.put("type", request.type());
        }

        // The signatures are computed over the EXACT serialized body we send.
        String json = toJson(body);
        JsonNode data = postJson("/bank_transfer", json, true);
        String rawStatus = text(data, "status");
        return PayoutResult.builder()
                .status(ProviderStatus.fromRaw(rawStatus))
                .providerReference(firstNonBlank(text(data, "reference"), request.reference()))
                .rawStatus(rawStatus)
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        return signer.verifyWebhookSignature(rawPayload, signatureHeader);
    }

    // ---------------------------------------------------------------------
    // HTTP plumbing
    // ---------------------------------------------------------------------

    private JsonNode postJson(String path, String jsonBody, boolean signed) {
        requireSecretKey();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (signed) {
                // Payouts require both signatures over the exact body.
                builder.header("Signature", signer.hmacSha512Hex(jsonBody));
                builder.header("X-Signature", signer.rsaSha256Base64(jsonBody));
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            return parseEnvelope(path, response);
        } catch (PaymentProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("CashOnRails request interrupted: " + path, e);
        } catch (Exception e) {
            throw new PaymentProviderException("CashOnRails request failed: " + path, e);
        }
    }

    private JsonNode getJson(String path) {
        requireSecretKey();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + secretKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            return parseEnvelope(path, response);
        } catch (PaymentProviderException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("CashOnRails request interrupted: " + path, e);
        } catch (Exception e) {
            throw new PaymentProviderException("CashOnRails request failed: " + path, e);
        }
    }

    /**
     * Parse the {@code {status, message, data}} envelope. Treats a non-2xx HTTP
     * status or a falsey {@code status} field as a failure.
     */
    private JsonNode parseEnvelope(String path, HttpResponse<String> response) throws Exception {
        JsonNode root = objectMapper.readTree(response.body());
        boolean httpOk = response.statusCode() >= 200 && response.statusCode() < 300;
        boolean bodyOk = isTruthyStatus(root.get("status"));

        if (!httpOk || !bodyOk) {
            String message = root.has("message") ? root.get("message").asText() : "unknown error";
            log.warn("CashOnRails {} returned HTTP {} status={} message={}",
                    path, response.statusCode(),
                    root.has("status") ? root.get("status").asText() : "?", message);
            throw new PaymentProviderException("CashOnRails error: " + message);
        }
        JsonNode data = root.get("data");
        return data != null && !data.isNull() ? data : root;
    }

    private boolean isTruthyStatus(JsonNode statusNode) {
        if (statusNode == null || statusNode.isNull()) {
            return true; // some endpoints omit status on success
        }
        if (statusNode.isBoolean()) {
            return statusNode.asBoolean();
        }
        String s = statusNode.asText();
        return "true".equalsIgnoreCase(s) || "success".equalsIgnoreCase(s)
                || "successful".equalsIgnoreCase(s) || "200".equals(s) || "00".equals(s);
    }

    private void requireSecretKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new PaymentProviderException(
                    "CashOnRails secret key is not configured (cashonrails.secret-key)");
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new PaymentProviderException("Failed to serialize request body", e);
        }
    }

    /** Return the first present, non-blank field among {@code keys}. */
    private String text(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
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
