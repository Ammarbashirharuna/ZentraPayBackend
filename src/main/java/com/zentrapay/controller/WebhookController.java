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

    @PostMapping("/cashonrails")
    @Operation(summary = "CashOnRails webhook",
            description = "Receives payment/transfer events. Verified by signature.")
    public ResponseEntity<String> cashOnRails(
            @RequestBody(required = false) String rawPayload,
            @RequestHeader(value = "payloadsignature", required = false) String signature,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        boolean accepted = webhookService.handleCashOnRails(rawPayload, signature, authorization);
        if (!accepted) {
            // 401 on bad signature so the provider knows to retry / we can alert.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }
        // Always 200 once authentic so the provider stops retrying.
        return ResponseEntity.ok("ok");
    }
}
