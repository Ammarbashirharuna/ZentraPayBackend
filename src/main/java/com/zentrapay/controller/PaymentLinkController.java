package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.paymentlink.CreatePaymentLinkRequest;
import com.zentrapay.dto.paymentlink.PaymentLinkResponse;
import com.zentrapay.service.PaymentLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Seller-facing endpoints for managing payment links. All require JWT auth.
 * The public checkout endpoint lives in {@code CheckoutController} under /pay.
 */
@RestController
@RequestMapping("/api/v1/payment-links")
@RequiredArgsConstructor
@Tag(name = "Payment Links", description = "Create and manage your payment links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    @PostMapping
    @Operation(summary = "Create payment link",
            description = "Create a shareable link customers can use to pay you.")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> create(
            @Valid @RequestBody CreatePaymentLinkRequest request) {
        PaymentLinkResponse response = paymentLinkService.createPaymentLink(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Payment link created"));
    }

    @GetMapping
    @Operation(summary = "List payment links", description = "List your payment links (paginated).")
    public ResponseEntity<ApiResponse<Page<PaymentLinkResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentLinkResponse> page = paymentLinkService.listMyLinks(pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Payment links retrieved"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment link", description = "Get one of your payment links by id.")
    public ResponseEntity<ApiResponse<PaymentLinkResponse>> get(@PathVariable UUID id) {
        PaymentLinkResponse response = paymentLinkService.getMyLink(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment link retrieved"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete payment link",
            description = "Deactivate a payment link so it can no longer be paid.")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable UUID id) {
        paymentLinkService.deleteMyLink(id);
        return ResponseEntity.ok(ApiResponse.success("Payment link deleted", "The link is no longer active"));
    }
}
