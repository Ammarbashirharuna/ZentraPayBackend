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
 * A settlement of a confirmed payment's net amount to the seller's payout
 * account.
 *
 * Maps to: payouts (see V7 migration). One payout per payment. The
 * {@code reference} (PO-&lt;paymentReference&gt;) is our idempotency key sent to
 * the provider, so retries and duplicate transfer webhooks never double-pay.
 */
@Entity
@Table(name = "payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The payment being settled. One payout per payment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    /** Where the money is being sent. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_account_id", nullable = false)
    private PayoutAccount payoutAccount;

    /** Our idempotency key / transfer reference. */
    @Column(nullable = false, unique = true, length = 255)
    private String reference;

    /** The provider's transfer reference, once known. */
    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    /** Seller net, in minor units. */
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    /** How many times we've attempted the transfer with the provider. */
    @Column(nullable = false)
    private Integer attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
