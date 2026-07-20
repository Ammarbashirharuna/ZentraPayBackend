package com.zentrapay.provider.cashonrails;

import com.zentrapay.provider.PaymentProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Produces and verifies the signatures CashOnRails requires.
 *
 * CashOnRails uses two independent signing schemes:
 * <ul>
 *   <li><b>HMAC-512</b> over the raw request/webhook body using your account
 *       <i>encryption key</i>. Sent as the {@code Signature} header on payouts,
 *       and received as the {@code payloadsignature} header on webhooks.</li>
 *   <li><b>RSA-SHA256</b> over the exact request body using your RSA
 *       <i>private key</i>, Base64-encoded. Sent as the {@code X-Signature}
 *       header on payouts. CashOnRails verifies it with the public key you
 *       upload in the dashboard.</li>
 * </ul>
 *
 * All secrets come from environment/config; none are hard-coded.
 */
@Component
@Slf4j
public class
CashOnRailsSigner {

    private final String encryptionKey;
    private final String rsaPrivateKeyPem;

    public CashOnRailsSigner(
            @Value("${cashonrails.encryption-key:}") String encryptionKey,
            @Value("${cashonrails.rsa-private-key:}") String rsaPrivateKeyPem
    ) {
        this.encryptionKey = encryptionKey;
        this.rsaPrivateKeyPem = rsaPrivateKeyPem;
    }

    /**
     * HMAC-SHA512 of {@code payload} using the encryption key, returned as a
     * lowercase hex string. Used for the payout {@code Signature} header.
     */
    public String hmacSha512Hex(String payload) {
        requireEncryptionKey();
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    encryptionKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new PaymentProviderException("Failed to compute HMAC-512 signature", e);
        }
    }

    /**
     * Base64 RSA-SHA256 signature of the exact request body, for the payout
     * {@code X-Signature} header.
     */
    public String rsaSha256Base64(String payload) {
        if (rsaPrivateKeyPem == null || rsaPrivateKeyPem.isBlank()) {
            throw new PaymentProviderException(
                    "CashOnRails RSA private key is not configured (cashonrails.rsa-private-key)");
        }
        try {
            PrivateKey privateKey = loadPrivateKey(rsaPrivateKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (PaymentProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentProviderException("Failed to compute RSA-SHA256 signature", e);
        }
    }

    /**
     * Verify an inbound webhook's {@code payloadsignature} header (HMAC-512).
     * Uses constant-time comparison to avoid timing side channels.
     *
     * @return true only if a key is configured and the signature matches
     */
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Webhook rejected: missing payloadsignature header");
            return false;
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            // Fail closed: never accept a webhook we cannot authenticate.
            log.error("Webhook rejected: encryption key not configured, cannot verify signature");
            return false;
        }
        String expected = hmacSha512Hex(rawPayload);
        boolean matches = constantTimeEquals(expected, signatureHeader.trim());
        if (!matches) {
            log.warn("Webhook rejected: signature mismatch");
        }
        return matches;
    }

    private void requireEncryptionKey() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new PaymentProviderException(
                    "CashOnRails encryption key is not configured (cashonrails.encryption-key)");
        }
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    /** Length-constant string comparison. */
    private boolean constantTimeEquals(String a, String b) {
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }
}
