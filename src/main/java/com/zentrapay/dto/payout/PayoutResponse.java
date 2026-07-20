package com.zentrapay.dto.payout;

import com.zentrapay.entity.Payout;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A settlement to the seller, as shown to the seller who earned it. Lets a
 * seller see whether their money has been paid out and, if not, why.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {

    private UUID id;
    private UUID paymentId;
    private String reference;
    private String providerReference;
    /** Seller net, in minor units. */
    private Long amount;
    private String currency;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public static PayoutResponse from(Payout payout) {
        return PayoutResponse.builder()
                .id(payout.getId())
                .paymentId(payout.getPayment() != null ? payout.getPayment().getId() : null)
                .reference(payout.getReference())
                .providerReference(payout.getProviderReference())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(payout.getStatus() != null ? payout.getStatus().name() : null)
                .attempts(payout.getAttempts())
                .lastError(payout.getLastError())
                .completedAt(payout.getCompletedAt())
                .createdAt(payout.getCreatedAt())
                .build();
    }
}
