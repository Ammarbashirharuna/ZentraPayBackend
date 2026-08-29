package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.checkout.InitiatePaymentRequest;
import com.zentrapay.dto.checkout.InitiatePaymentResponse;
import com.zentrapay.dto.checkout.PublicPaymentLinkResponse;
import com.zentrapay.service.CheckoutService;
import com.zentrapay.service.PaymentConfirmationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public, unauthenticated checkout endpoints under /api/v1/pay.
 * Registered as public in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/pay")
@RequiredArgsConstructor
@Tag(name = "Checkout", description = "Public payment pages — no login required")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final PaymentConfirmationService paymentConfirmationService;

    @GetMapping("/{shortCode}")
    @Operation(summary = "View payment link", description = "Public details for rendering the checkout page.")
    public ResponseEntity<ApiResponse<PublicPaymentLinkResponse>> view(@PathVariable String shortCode) {
        PublicPaymentLinkResponse response = checkoutService.getPublicPaymentLink(shortCode);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment link retrieved"));
    }

    @PostMapping("/{shortCode}")
    @Operation(summary = "Start payment", description = "Begin a payment and get the checkout URL to redirect to.")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> pay(
            @PathVariable String shortCode,
            @Valid @RequestBody InitiatePaymentRequest request) {
        InitiatePaymentResponse response = checkoutService.initiatePayment(shortCode, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment initiated"));
    }

    /**
     * Provider redirect target after the customer pays. We verify server-side
     * (never trust the redirect alone) and report the resulting status.
     */
    @GetMapping("/callback")
    @Operation(summary = "Payment callback",
            description = "Provider redirect target; verifies the payment server-side.")
    public ResponseEntity<ApiResponse<String>> callback(@RequestParam(required = false) String reference) {
        String status = paymentConfirmationService.confirmByReference(reference);
        return ResponseEntity.ok(ApiResponse.success(status, "Payment status: " + status));
    }
}
