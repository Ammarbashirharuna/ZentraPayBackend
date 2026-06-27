package com.zentrapay.dto.bankaccount;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response after verifying bank account with Paystack
 *
 * Paystack returns the account name.
 * We show this to the seller to confirm.
 *
 * Example:
 * {
 *   "accountName": "Ammar Bashir Haruna",
 *   "accountNumber": "0123456789",
 *   "bankName": "Guarantee Trust Bank"
 * }
 *
 * Seller sees: "Is this your account? Ammar Bashir Haruna"
 * If yes → they confirm and we save it
 * If no → they can try another account number
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyAccountResponse {

    /**
     * Account holder name (verified by Paystack)
     * This is what the bank has on file.
     * If seller's name doesn't match, they entered wrong account.
     */
    private String accountName;

    /**
     * The account number they provided (for confirmation)
     */
    private String accountNumber;

    /**
     * Bank name (human-readable)
     * We looked up the bank code and got this name.
     */
    private String bankName;

    /**
     * The bank code (for saving)
     * Frontend will pass this back when confirming.
     */
    private String bankCode;
}