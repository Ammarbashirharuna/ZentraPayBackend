package com.zentrapay.dto.bankaccount;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response containing bank account details
 *
 * Used when:
 * - User saves a bank account (returns what was saved)
 * - User views their bank account settings
 * - User updates their bank account
 *
 * We DO NOT return the Paystack subaccount code
 * Why? It's sensitive internal information.
 * Users don't need to see it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {

    /**
     * Unique identifier
     */
    private UUID id;

    /**
     * The bank name (e.g., "Guarantee Trust Bank")
     */
    private String bankName;

    /**
     * Account number (e.g., "0123456789")
     */
    private String accountNumber;

    /**
     * Account holder name (what bank has on file)
     */
    private String accountName;

    /**
     * Is this account active?
     * User can deactivate it temporarily
     */
    private Boolean isActive;

    /**
     * When this account was added
     */
    private LocalDateTime createdAt;

    /**
     * When this account was last updated
     * (e.g., if seller changed account)
     */
    private LocalDateTime updatedAt;

    /**
     * Status message for UI
     * Examples:
     * "Account verified and active"
     * "Ready to receive payments"
     */
    private String status;
}
