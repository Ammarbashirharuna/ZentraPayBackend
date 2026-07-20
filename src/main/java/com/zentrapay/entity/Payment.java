package com.zentrapay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single customer payment against a {@link PaymentLink}.
 *
 * Maps to: payments (see V1 migration).
 *
 * {@code providerReference} is our unique idempotency key sent to the provider
 * at initialize time and echoed back on verify/webhook, so a duplicate webhook
 * never double-credits a seller.
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The link that was paid. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_link_id", nullable = false)
    private PaymentLink paymentLink;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    /** Amount in minor units. */
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Our unique reference / idempotency key for this transaction. */
    @Column(name = "provider_reference", nullable = false, unique = true, length = 255)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** Arbitrary provider/customer metadata, stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
