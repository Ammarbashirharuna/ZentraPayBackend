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
 * A reusable (or single-use) link a seller shares so customers can pay them.
 *
 * Maps to: payment_links (see V1/V5/V6 migrations).
 *
 * Money is stored in minor units ({@code amount} = kobo/cents) to avoid
 * floating-point rounding. Each link points at the seller's
 * {@link PayoutAccount} so we know where settled funds are sent.
 */
@Entity
@Table(name = "payment_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Seller who owns this link. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Payout destination for funds collected through this link. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_account_id")
    private PayoutAccount payoutAccount;

    /** Short public code used in the shareable URL (/pay/{shortCode}). */
    @Column(name = "short_code", nullable = false, unique = true, length = 8)
    private String shortCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Amount in minor units (e.g. kobo, cents). Always positive. */
    @Column(nullable = false)
    private Long amount;

    /** ISO-4217 currency code. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentLinkStatus status;

    /** If true, the link moves to PAID after one successful payment. */
    @Builder.Default
    @Column(name = "single_use", nullable = false)
    private Boolean singleUse = false;

    /** Optional cap on how many times the link may be paid. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "current_uses", nullable = false)
    private Integer currentUses = 0;

    /** Optional expiry. Null means it never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Where to send the customer after a successful payment. */
    @Column(name = "redirect_url", columnDefinition = "TEXT")
    private String redirectUrl;

    // ---- Checkout branding ----

    /** Seller's logo URL displayed on the checkout page. */
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** Primary brand color (hex, e.g. #FF5733). */
    @Column(name = "brand_color", length = 7)
    private String brandColor;

    /** Accent color for buttons/highlights (hex). */
    @Column(name = "accent_color", length = 7)
    private String accentColor;

    /** Custom thank-you message shown after successful payment. */
    @Column(name = "thank_you_message", columnDefinition = "TEXT")
    private String thankYouMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Whether this link can currently accept a payment: active, not expired,
     * and under its usage cap.
     */
    @Transient
    public boolean isPayable() {
        if (status != PaymentLinkStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            return false;
        }
        if (maxUses != null && currentUses >= maxUses) {
            return false;
        }
        return true;
    }
}
