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
 * A seller's referral code and tracking record.
 *
 * Maps to: referrals (see V9 migration).
 *
 * Each seller gets a unique referral code. When a new seller registers using
 * that code, the referrer's {@code usedCount} is incremented. Referrers can
 * earn fee reductions or other incentives based on referred seller activity.
 */
@Entity
@Table(name = "referrals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The seller who owns this referral code. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /** Unique referral code (uppercase alphanumeric, 6–12 chars). */
    @Column(name = "referral_code", nullable = false, unique = true, length = 20)
    private String referralCode;

    /** The user whose referral code was used during this seller's registration. */
    @Column(name = "referred_by_user_id")
    private UUID referredByUserId;

    /** How many sellers signed up using this code. */
    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    /** Cumulative earnings from the referral program (in minor units). */
    @Builder.Default
    @Column(name = "total_earnings", nullable = false)
    private Long totalEarnings = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
