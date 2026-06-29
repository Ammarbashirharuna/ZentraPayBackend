package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.bankaccount.BankAccountResponse;
import com.zentrapay.dto.bankaccount.SaveBankAccountRequest;
import com.zentrapay.dto.bankaccount.VerifyAccountRequest;
import com.zentrapay.dto.bankaccount.VerifyAccountResponse;
import com.zentrapay.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Bank Account Controller
 *
 * REST API endpoints for managing bank accounts:
 * - POST /api/v1/bank-accounts/verify     - Verify account with Paystack
 * - POST /api/v1/bank-accounts            - Save bank account
 * - GET  /api/v1/bank-accounts            - Get user's bank account
 * - DELETE /api/v1/bank-accounts          - Deactivate bank account
 *
 * Flow:
 * 1. User enters account number + bank code
 * 2. POST /verify → Paystack verifies → returns account name
 * 3. User confirms "Yes, that's my account"
 * 4. POST /bank-accounts → Creates subaccount → saves to DB
 * 5. Bank account ready for payments
 *
 * Authentication: All endpoints require JWT token (user must be logged in)
 */
@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "Bank Accounts",
        description = "Manage bank accounts for receiving payments")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    /**
     * Verify bank account with Paystack
     *
     * Step 1: User enters their account details
     * We verify with Paystack that the account exists
     * and return the account holder's name for confirmation.
     *
     * Request example:
     * {
     *   "bankCode": "058",
     *   "accountNumber": "0123456789"
     * }
     *
     * Response example (success):
     * {
     *   "success": true,
     *   "message": "Account verified successfully",
     *   "data": {
     *     "accountName": "Ammar Bashir Haruna",
     *     "accountNumber": "0123456789",
     *     "bankCode": "058",
     *     "bankName": "Guarantee Trust Bank"
     *   }
     * }
     *
     * Response example (failure):
     * {
     *   "success": false,
     *   "error": "Account verification failed. Please check your
     *            account number and bank code.",
     *   "timestamp": "2026-06-26T..."
     * }
     *
     * @param request Bank code + account number
     * @return Verified account details for user confirmation
     */
    @PostMapping("/verify")
    @Operation(
            summary = "Verify bank account",
            description = "Verify account number with Paystack. " +
                    "Returns account holder name for confirmation."
    )
    public ResponseEntity<ApiResponse<VerifyAccountResponse>> verifyAccount(
            @Valid @RequestBody VerifyAccountRequest request
    ) {
        VerifyAccountResponse response = bankAccountService
                .verifyBankAccount(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(
                        response,
                        "Account verified successfully. " +
                                "Please confirm this is your account."
                ));
    }

    /**
     * Save bank account after verification
     *
     * Step 2: After user confirms their verified account,
     * we create a Paystack subaccount and save everything.
     *
     * This is when the actual subaccount is created.
     * The subaccount is critical — it tells Paystack where to send payments.
     *
     * Request example:
     * {
     *   "bankCode": "058",
     *   "accountNumber": "0123456789",
     *   "accountName": "Ammar Bashir Haruna",
     *   "bankName": "Guarantee Trust Bank"
     * }
     *
     * Response example (success):
     * {
     *   "success": true,
     *   "message": "Bank account saved successfully",
     *   "data": {
     *     "id": "550e8400-e29b-41d4-a716-446655440000",
     *     "bankName": "Guarantee Trust Bank",
     *     "accountNumber": "0123456789",
     *     "accountName": "Ammar Bashir Haruna",
     *     "isActive": true,
     *     "createdAt": "2026-06-26T...",
     *     "updatedAt": "2026-06-26T...",
     *     "status": "Account verified and active"
     *   }
     * }
     *
     * @param request Verified account details
     * @return Saved bank account
     */
    @PostMapping
    @Operation(
            summary = "Save bank account",
            description = "Save verified bank account and create Paystack subaccount. " +
                    "This enables you to receive payments."
    )
    public ResponseEntity<ApiResponse<BankAccountResponse>> saveBankAccount(
            @Valid @RequestBody SaveBankAccountRequest request
    ) {
        BankAccountResponse response = bankAccountService
                .saveBankAccount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        response,
                        "Bank account saved successfully. " +
                                "You can now receive payments!"
                ));
    }

    /**
     * Get user's bank account details
     *
     * Retrieves the logged-in user's bank account information.
     * Used when:
     * - User views their settings/profile
     * - User wants to see their current bank account
     * - Controller needs to verify account exists
     *
     * Response example:
     * {
     *   "success": true,
     *   "message": "Bank account retrieved",
     *   "data": {
     *     "id": "550e8400-e29b-41d4-a716-446655440000",
     *     "bankName": "Guarantee Trust Bank",
     *     "accountNumber": "0123456789",
     *     "accountName": "Ammar Bashir Haruna",
     *     "isActive": true,
     *     "createdAt": "2026-06-26T...",
     *     "status": "Account verified and active"
     *   }
     * }
     *
     * Error response (no account):
     * {
     *   "success": false,
     *   "error": "No bank account found. Please set up a bank account
     *            to receive payments.",
     *   "timestamp": "2026-06-26T..."
     * }
     *
     * @return User's bank account
     */
    @GetMapping
    @Operation(
            summary = "Get bank account",
            description = "Retrieve your bank account details"
    )
    public ResponseEntity<ApiResponse<BankAccountResponse>> getBankAccount() {
        BankAccountResponse response = bankAccountService
                .getBankAccount();

        return ResponseEntity.ok(
                ApiResponse.success(
                        response,
                        "Bank account retrieved"
                )
        );
    }

    /**
     * Deactivate bank account
     *
     * Removes the user's bank account.
     * Used when:
     * - User wants to remove their account
     * - User wants to change bank account (delete old, add new)
     *
     * Important: We soft-delete (mark inactive) not hard-delete.
     * Why? Audit trail. We keep records for compliance.
     *
     * Response example:
     * {
     *   "success": true,
     *   "message": "Bank account deleted successfully",
     *   "timestamp": "2026-06-26T..."
     * }
     *
     * @return Success message
     */
    @DeleteMapping
    @Operation(
            summary = "Delete bank account",
            description = "Remove your bank account. " +
                    "You won't be able to receive payments until you add a new account."
    )
    public ResponseEntity<ApiResponse<String>> deleteBankAccount() {
        bankAccountService.deleteBankAccount();

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Bank account deleted successfully",
                        "Your bank account has been removed"
                )
        );
    }
}