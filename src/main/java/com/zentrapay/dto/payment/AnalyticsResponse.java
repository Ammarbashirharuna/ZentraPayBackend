package com.zentrapay.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Analytics data for the seller dashboard. Includes daily revenue trends,
 * conversion metrics, and top-performing links.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    /** Daily revenue for the last 30 days. */
    private List<DailyRevenue> dailyRevenue;

    /** Conversion metrics per payment link. */
    private List<LinkAnalytics> linkAnalytics;

    /** Overall conversion rate (completed / total initiated). */
    private Double overallConversionRate;

    /** Average payment amount across all completed payments. */
    private Long averagePaymentAmount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenue {
        private String date;
        private Long revenue;
        private Integer paymentCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkAnalytics {
        private String shortCode;
        private String title;
        private Long totalRevenue;
        private Integer totalPayments;
        private Integer viewCount;
        private Double conversionRate;
    }
}
