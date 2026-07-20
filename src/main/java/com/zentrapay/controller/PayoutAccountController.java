package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.payout.PayoutAccountResponse;
import com.zentrapay.dto.payout.SavePayoutAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountResponse;
import com.zentrapay.service.PayoutAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for managing a seller's payout account.
 *
 * - POST   /api/v1/payout-accounts/validate  — resolve account holder name
 * - POST   /api/v1/payout-accounts           — save validated account
 * - GET    /api/v1/payout-accounts           — get current account
 * - DELETE /api/v1/payout-accounts           — deactivate account
 *
 * All endpoints require authentication (JWT).
 */
@RestController
@RequestMapping("/api/v1/payout-accounts")
@RequiredArgsConstructor
@Tag(name = "Payout Accounts",
        description = "Manage where you receive settled funds (bank / mobile money) across Africa")
public class PayoutAccountController {

    private final PayoutAccountService payoutAccountService;

    @PostMapping("/validate")
    @Operation(summary = "Validate payout account",
            description = "Resolve the account holder name with the payment provider for confirmation.")
    public ResponseEntity<ApiResponse<ValidateAccountResponse>> validate(
            @Valid @RequestBody ValidateAccountRequest request) {
        ValidateAccountResponse response = payoutAccountService.validateAccount(request);
        return ResponseEntity.ok(ApiResponse.success(
                response, "Account validated. Please confirm this is your account."));
    }

    @PostMapping
    @Operation(summary = "Save payout account",
            description = "Save a validated payout account so you can receive settled funds.")
    public ResponseEntity<ApiResponse<PayoutAccountResponse>> save(
            @Valid @RequestBody SavePayoutAccountRequest request) {
        PayoutAccountResponse response = payoutAccountService.savePayoutAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                response, "Payout account saved. You can now receive payments."));
    }

    @GetMapping
    @Operation(summary = "Get payout account", description = "Retrieve your payout account details.")
    public ResponseEntity<ApiResponse<PayoutAccountResponse>> get() {
        PayoutAccountResponse response = payoutAccountService.getPayoutAccount();
        return ResponseEntity.ok(ApiResponse.success(response, "Payout account retrieved"));
    }

    @DeleteMapping
    @Operation(summary = "Delete payout account",
            description = "Deactivate your payout account. You won't receive payments until you add a new one.")
    public ResponseEntity<ApiResponse<String>> delete() {
        payoutAccountService.deletePayoutAccount();
        return ResponseEntity.ok(ApiResponse.success(
                "Payout account deleted", "Your payout account has been removed"));
    }
}
