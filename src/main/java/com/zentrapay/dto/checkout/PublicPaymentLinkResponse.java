package com.zentrapay.dto.checkout;

import com.zentrapay.entity.PaymentLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public, safe-to-expose view of a payment link shown on the checkout page.
 * Deliberately omits seller identity, payout account, and internal counters.
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

    public static PublicPaymentLinkResponse from(PaymentLink link) {
        return PublicPaymentLinkResponse.builder()
                .shortCode(link.getShortCode())
                .title(link.getTitle())
                .description(link.getDescription())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .payable(link.isPayable())
                .build();
    }
}
