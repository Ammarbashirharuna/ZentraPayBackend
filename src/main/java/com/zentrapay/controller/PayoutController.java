package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.payout.PayoutResponse;
import com.zentrapay.service.PayoutQueryService;
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
 * Seller-facing read API over settlements (payouts) of the seller's confirmed
 * payments. All endpoints require JWT auth and are scoped to the seller.
 */
@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
@Tag(name = "Payouts", description = "Track settlements of your confirmed payments")
public class PayoutController {

    private final PayoutQueryService payoutQueryService;

    @GetMapping
    @Operation(summary = "List payouts",
            description = "List settlements of your confirmed payments (paginated).")
    public ResponseEntity<ApiResponse<Page<PayoutResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PayoutResponse> page = payoutQueryService.listMyPayouts(pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Payouts retrieved"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payout", description = "Get one of your settlements by id.")
    public ResponseEntity<ApiResponse<PayoutResponse>> get(@PathVariable UUID id) {
        PayoutResponse response = payoutQueryService.getMyPayout(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Payout retrieved"));
    }
}
