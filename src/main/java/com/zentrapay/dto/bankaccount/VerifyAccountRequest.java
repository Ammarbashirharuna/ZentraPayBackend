package com.zentrapay.dto.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to verify a bank account with Paystack
 *
 * When seller enters their bank account:
 * 1. We send this request with account number + bank code
 * 2. Paystack verifies the account exists
 * 3. Paystack returns the account name
 * 4. We show it to seller: "Is this your account? [Name]"
 * 5. If seller confirms, we create a subaccount
 *
 * This is Step 1 of the flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAccountRequest {

    /**
     * Bank code (e.g., "058" for GTBank)
     *
     * Why code and not name?
     * - Paystack API requires code
     * - Multiple banks might have similar names
     * - Code is unambiguous
     *
     * Common codes:
     * GTBank: 058
     * Access Bank: 044
     * First Bank: 011
     * Zenith Bank: 057
     * UBA: 033
     * We'll provide a list of these on frontend
     */
    @NotBlank(message = "Bank code is required")
    private String bankCode;

    /**
     * 10-digit account number
     *
     * Nigerian bank accounts are 10 digits.
     * Examples:
     * 0123456789
     * 9876543210
     */
    @NotBlank(message = "Account number is required")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Account number must be exactly 10 digits"
    )
    private String accountNumber;
}