package com.zentrapay.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated earnings view for a seller's dashboard.
 *
 * Each row in {@code currencies} represents one currency the seller has
 * collected in. The totals at the top are the cross-currency aggregate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsSummaryResponse {

    /** Total gross amount collected across all currencies (minor units). */
    private Long totalGrossCollected;

    /** Total platform fees withheld (minor units). */
    private Long totalPlatformFees;

    /** Total net amount paid out to the seller (minor units). */
    private Long totalNetPaid;

    /** Total number of completed payments. */
    private Long totalPaymentsCount;

    /** Number of payments still awaiting confirmation. */
    private Long pendingPaymentsCount;

    /** Number of failed/abandoned payments. */
    private Long failedPaymentsCount;

    /** Per-currency breakdown. */
    private List<CurrencyBreakdown> currencies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyBreakdown {
        private String currency;
        private Long grossCollected;
        private Long platformFees;
        private Long netPaid;
        private Long paymentsCount;
        private Long pendingCount;
        private Long failedCount;
        private Long paidPayouts;
        private Long pendingPayouts;
        private Long failedPayouts;
    }
}
