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
 * A seller's payout destination — where their money is sent after a customer
 * pays one of their links.
 *
 * Maps to: payout_accounts (formerly bank_accounts; see migration V6).
 *
 * Pan-African by design: carries country + currency + method so the same model
 * serves a Nigerian bank account, a Kenyan M-Pesa wallet, or a South African
 * EFT account. Provider-neutral: {@code providerRecipientCode} holds whatever
 * reference the current provider (CashOnRails) needs, if any.
 *
 * Flow:
 * 1. Seller enters account number + bank/institution code + currency.
 * 2. We validate with the provider ({@code POST /account_name}) to resolve the
 *    account holder's name.
 * 3. Seller confirms; we persist with {@code accountValidated = true}.
 * 4. When a payment succeeds, we pay out to this account (minus platform fee).
 */
@Entity
@Table(name = "payout_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Owning seller. One payout account per seller for now. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ISO-3166 alpha-2 country code (e.g. NG, GH, KE, ZA). */
    @Column(nullable = false, length = 2)
    private String country;

    /** ISO-4217 currency code the seller settles in (e.g. NGN, GHS, KES, ZAR). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Payout rail. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PayoutMethod method;

    /** Human-readable bank/institution name for display. */
    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** Provider bank/institution code for the currency (from GET /bank_list/{currency}). */
    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    /** Account number or mobile-money number. Wider than 10 for intl/momo. */
    @Column(name = "account_number", nullable = false, length = 34)
    private String accountNumber;

    /** Resolved account holder name (from provider validation). */
    @Column(name = "account_name", nullable = false, length = 200)
    private String accountName;

    /**
     * Provider recipient/reference code, if the provider issues one.
     * Nullable: CashOnRails transfers by raw account details and does not
     * require a pre-created recipient, unlike Paystack subaccounts.
     */
    @Column(name = "provider_recipient_code", length = 100)
    private String providerRecipientCode;

    /** True once the provider has resolved and we've confirmed the account. */
    @Builder.Default
    @Column(name = "account_validated", nullable = false)
    private Boolean accountValidated = false;

    /** Seller can deactivate to stop receiving payments. */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
