package com.zentrapay.dto.paymentlink;

import com.zentrapay.entity.PaymentLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment link as returned to its owning seller (full detail, including the
 * shareable URL and usage counters).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLinkResponse {

    private UUID id;
    private String shortCode;
    /** Full shareable URL, e.g. https://host/api/v1/pay/{shortCode}. */
    private String paymentUrl;
    private String title;
    private String description;
    private Long amount;
    private String currency;
    private String status;
    private Boolean singleUse;
    private Integer maxUses;
    private Integer currentUses;
    private LocalDateTime expiresAt;
    private String redirectUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentLinkResponse from(PaymentLink link, String paymentUrl) {
        return PaymentLinkResponse.builder()
                .id(link.getId())
                .shortCode(link.getShortCode())
                .paymentUrl(paymentUrl)
                .title(link.getTitle())
                .description(link.getDescription())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .status(link.getStatus() != null ? link.getStatus().name() : null)
                .singleUse(link.getSingleUse())
                .maxUses(link.getMaxUses())
                .currentUses(link.getCurrentUses())
                .expiresAt(link.getExpiresAt())
                .redirectUrl(link.getRedirectUrl())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }
}
