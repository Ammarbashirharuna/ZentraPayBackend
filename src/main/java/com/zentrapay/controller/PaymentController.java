package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.payment.AnalyticsResponse;
import com.zentrapay.dto.payment.EarningsSummaryResponse;
import com.zentrapay.dto.payment.PaymentResponse;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Seller-facing read API over payments made against the seller's links.
 * All endpoints require JWT auth and are scoped to the authenticated seller.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "View payments made against your links")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    @GetMapping
    @Operation(summary = "List payments",
            description = "List payments made against your links (paginated). "
                    + "Optionally filter by status (PENDING, COMPLETED, FAILED, REFUNDED).")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> list(
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponse> page = paymentQueryService.listMyPayments(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Payments retrieved"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment", description = "Get one of your payments by id.")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable UUID id) {
        PaymentResponse response = paymentQueryService.getMyPayment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payment retrieved"));
    }

    @GetMapping("/by-link/{linkId}")
    @Operation(summary = "List payments for a link",
            description = "List payments made against one of your payment links.")
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> listForLink(
            @PathVariable UUID linkId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PaymentResponse> page = paymentQueryService.listPaymentsForLink(linkId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Payments retrieved"));
    }

    @GetMapping("/summary")
    @Operation(summary = "Earnings summary",
            description = "Aggregated earnings across all currencies: gross collected, fees, net paid, and per-currency breakdown.")
    public ResponseEntity<ApiResponse<EarningsSummaryResponse>> summary() {
        EarningsSummaryResponse response = paymentQueryService.getMySummary();
        return ResponseEntity.ok(ApiResponse.success(response, "Earnings summary retrieved"));
    }

    @GetMapping("/analytics")
    @Operation(summary = "Seller analytics",
            description = "Daily revenue trends for the last 30 days and per-link performance metrics.")
    public ResponseEntity<ApiResponse<AnalyticsResponse>> analytics() {
        AnalyticsResponse response = paymentQueryService.getMyAnalytics();
        return ResponseEntity.ok(ApiResponse.success(response, "Analytics retrieved"));
    }
}
