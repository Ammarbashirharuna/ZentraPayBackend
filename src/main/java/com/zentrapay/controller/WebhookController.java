package com.zentrapay.controller;

import com.zentrapay.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound provider webhooks under /api/v1/webhooks (public — authenticated by
 * signature, not JWT).
 *
 * We read the raw request body (not a parsed DTO) because the signature is
 * computed over the exact bytes; re-serializing a parsed object would change
 * them and break verification.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Provider webhook callbacks")
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/paystack")
    @Operation(summary = "Paystack webhook",
            description = "Receives payment/transfer events. Verified by HMAC-SHA512 signature.")
    public ResponseEntity<String> paystack(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "x-paystack-signature", required = false) String signature) {

        boolean accepted = webhookService.handlePaystack(rawPayload, signature);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        return ResponseEntity.ok("ok");
    }
}
