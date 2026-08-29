package com.zentrapay.dto.checkout;

import com.zentrapay.entity.PaymentLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public, safe-to-expose view of a payment link shown on the checkout page.
 * Deliberately omits seller identity, payout account, and internal counters.
 * Includes branding so the frontend can render a customized checkout page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicPaymentLinkResponse {

    private String shortCode;
    private String title;
    private String description;
    private Long amount;
    private String currency;
    /** Whether the link can currently accept a payment. */
    private boolean payable;

    // ---- Checkout branding ----
    /** Seller's logo URL (nullable if not set). */
    private String logoUrl;
    /** Primary brand color hex (nullable). */
    private String brandColor;
    /** Accent color hex (nullable). */
    private String accentColor;
    /** Custom thank-you message (nullable). */
    private String thankYouMessage;

    public static PublicPaymentLinkResponse from(PaymentLink link) {
        return PublicPaymentLinkResponse.builder()
                .shortCode(link.getShortCode())
                .title(link.getTitle())
                .description(link.getDescription())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .payable(link.isPayable())
                .logoUrl(link.getLogoUrl())
                .brandColor(link.getBrandColor())
                .accentColor(link.getAccentColor())
                .thankYouMessage(link.getThankYouMessage())
                .build();
    }
}
