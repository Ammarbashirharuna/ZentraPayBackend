package com.zentrapay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a seller's bank account
 *
 * Maps to: bank_accounts table
 *
 * Why this entity?
 * - Sellers enter their bank account to receive payments
 * - We use Paystack to verify the account name
 * - We create a Paystack subaccount for automatic fund splitting
 * - Every payment link uses this subaccount
 *
 * Flow:
 * 1. Seller enters account number + bank
 * 2. We verify with Paystack (get their name)
 * 3. We create Paystack subaccount (get subaccount_code)
 * 4. We save everything here
 * 5. Payment links use subaccount_code to send money to seller
 */
@Entity
@Table(name = "bank_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Which user this bank account belongs to
    // One seller = one bank account (for now)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The bank name (e.g., "Guarantee Trust Bank")
    @Column(nullable = false, length = 100)
    private String bankName;

    // Bank code used by Paystack (e.g., "058" for GTBank)
    // Paystack identifies banks by code, not name
    @Column(nullable = false, length = 10)
    private String bankCode;

    // Customer's account number (e.g., "0123456789")
    @Column(nullable = false, length = 10)
    private String accountNumber;

    // Account holder's name (verified by Paystack)
    // When we verify account, Paystack returns this name
    // We show it to seller: "Is this your account? [Name]"
    // Builds trust and prevents mistakes
    @Column(nullable = false, length = 200)
    private String accountName;

    // The subaccount code from Paystack
    // Example: "ACCT_1a2b3c4d5e6f7g8h"
    //
    // When customer pays payment link:
    // Paystack receives full amount
    // Then automatically splits using this code:
    //   - 100% goes to this subaccount
    //   - Which is linked to the bank account above
    @Column(length = 100)
    private String paystackSubaccountCode;

    // Is this bank account active?
    // Seller might deactivate it temporarily
    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}