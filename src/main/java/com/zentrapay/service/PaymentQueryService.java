package com.zentrapay.service;

import com.zentrapay.dto.payment.AnalyticsResponse;
import com.zentrapay.dto.payment.EarningsSummaryResponse;
import com.zentrapay.dto.payment.PaymentResponse;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.PayoutStatus;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PaymentRepository;
import com.zentrapay.repository.PayoutRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Read-only queries over a seller's payments. Every query is scoped through the
 * link's owner, so a seller can only ever see payments made against their own
 * links.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;
    private final UserRepository userRepository;

    /** Same fee rate used at settlement, so the net shown matches the payout. */
    @Value("${platform.fee-basis-points:100}")
    private long feeBasisPoints;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** List the seller's payments, optionally filtered by status. */
    public Page<PaymentResponse> listMyPayments(PaymentStatus status, Pageable pageable) {
        UUID userId = getCurrentUser().getId();
        Page<Payment> page = (status == null)
                ? paymentRepository.findByPaymentLinkUserIdOrderByCreatedAtDesc(userId, pageable)
                : paymentRepository.findByPaymentLinkUserIdAndStatusOrderByCreatedAtDesc(
                        userId, status, pageable);
        return page.map(p -> PaymentResponse.from(p, feeBasisPoints));
    }

    /** List payments against one of the seller's links. */
    public Page<PaymentResponse> listPaymentsForLink(UUID linkId, Pageable pageable) {
        UUID userId = getCurrentUser().getId();
        return paymentRepository
                .findByPaymentLinkIdAndPaymentLinkUserIdOrderByCreatedAtDesc(linkId, userId, pageable)
                .map(p -> PaymentResponse.from(p, feeBasisPoints));
    }

    /** Get one of the seller's payments by id. */
    public PaymentResponse getMyPayment(UUID id) {
        UUID userId = getCurrentUser().getId();
        Payment payment = paymentRepository.findByIdAndPaymentLinkUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return PaymentResponse.from(payment, feeBasisPoints);
    }

    /** Aggregated earnings summary across all currencies. */
    public EarningsSummaryResponse getMySummary() {
        UUID userId = getCurrentUser().getId();

        // Per-currency completed aggregates
        List<Object[]> completedAgg = paymentRepository.aggregateCompletedByCurrency(userId);
        List<Object[]> pendingCounts = paymentRepository.countPendingByCurrency(userId);
        List<Object[]> failedCounts = paymentRepository.countFailedByCurrency(userId);
        List<Object[]> payoutCounts = payoutRepository.countPayoutsByCurrencyAndStatus(userId);

        // Build currency map
        Map<String, EarningsSummaryResponse.CurrencyBreakdown> currencyMap = new LinkedHashMap<>();

        for (Object[] row : completedAgg) {
            String currency = (String) row[0];
            long gross = (Long) row[1];
            long count = (Long) row[2];
            long fee = gross * feeBasisPoints / 10_000L;
            currencyMap.put(currency, EarningsSummaryResponse.CurrencyBreakdown.builder()
                    .currency(currency)
                    .grossCollected(gross)
                    .platformFees(fee)
                    .netPaid(gross - fee)
                    .paymentsCount(count)
                    .pendingCount(0L)
                    .failedCount(0L)
                    .paidPayouts(0L)
                    .pendingPayouts(0L)
                    .failedPayouts(0L)
                    .build());
        }

        for (Object[] row : pendingCounts) {
            String currency = (String) row[0];
            long count = (Long) row[1];
            currencyMap.computeIfAbsent(currency, c -> emptyBreakdown(c))
                    .setPendingCount(count);
        }

        for (Object[] row : failedCounts) {
            String currency = (String) row[0];
            long count = (Long) row[1];
            currencyMap.computeIfAbsent(currency, c -> emptyBreakdown(c))
                    .setFailedCount(count);
        }

        for (Object[] row : payoutCounts) {
            String currency = (String) row[0];
            PayoutStatus status = (PayoutStatus) row[1];
            long count = (Long) row[2];
            EarningsSummaryResponse.CurrencyBreakdown cb = currencyMap
                    .computeIfAbsent(currency, c -> emptyBreakdown(c));
            switch (status) {
                case PAID -> cb.setPaidPayouts(cb.getPaidPayouts() + count);
                case PENDING, PROCESSING -> cb.setPendingPayouts(cb.getPendingPayouts() + count);
                case FAILED -> cb.setFailedPayouts(cb.getFailedPayouts() + count);
            }
        }

        List<EarningsSummaryResponse.CurrencyBreakdown> currencies = new ArrayList<>(currencyMap.values());

        long totalGross = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getGrossCollected).sum();
        long totalFees = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getPlatformFees).sum();
        long totalNet = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getNetPaid).sum();
        long totalCount = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getPaymentsCount).sum();
        long totalPending = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getPendingCount).sum();
        long totalFailed = currencies.stream().mapToLong(EarningsSummaryResponse.CurrencyBreakdown::getFailedCount).sum();

        return EarningsSummaryResponse.builder()
                .totalGrossCollected(totalGross)
                .totalPlatformFees(totalFees)
                .totalNetPaid(totalNet)
                .totalPaymentsCount(totalCount)
                .pendingPaymentsCount(totalPending)
                .failedPaymentsCount(totalFailed)
                .currencies(currencies)
                .build();
    }

    /** Seller analytics: daily revenue trends and per-link stats. */
    public AnalyticsResponse getMyAnalytics() {
        UUID userId = getCurrentUser().getId();

        // Daily revenue for last 30 days
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Object[]> dailyRows = paymentRepository.dailyRevenueSince(userId, since);
        List<AnalyticsResponse.DailyRevenue> dailyRevenue = dailyRows.stream()
                .map(row -> AnalyticsResponse.DailyRevenue.builder()
                        .date((String) row[0])
                        .revenue((Long) row[1])
                        .paymentCount(((Long) row[2]).intValue())
                        .build())
                .toList();

        // Per-link analytics
        List<Object[]> linkRows = paymentRepository.perLinkStats(userId);
        List<AnalyticsResponse.LinkAnalytics> linkAnalytics = linkRows.stream()
                .map(row -> AnalyticsResponse.LinkAnalytics.builder()
                        .shortCode((String) row[0])
                        .title((String) row[1])
                        .totalRevenue((Long) row[2])
                        .totalPayments(((Long) row[3]).intValue())
                        .viewCount(0) // TODO: add page view tracking
                        .conversionRate(0.0) // TODO: compute from views
                        .build())
                .toList();

        // Overall metrics
        long completedCount = paymentRepository.countByPaymentLinkUserIdAndStatus(userId, PaymentStatus.COMPLETED);
        long totalCount = paymentRepository.countByPaymentLinkUserId(userId);
        Double avgAmount = paymentRepository.averagePaymentAmount(userId);

        double conversionRate = totalCount > 0 ? (double) completedCount / totalCount * 100 : 0.0;

        return AnalyticsResponse.builder()
                .dailyRevenue(dailyRevenue)
                .linkAnalytics(linkAnalytics)
                .overallConversionRate(Math.round(conversionRate * 100.0) / 100.0)
                .averagePaymentAmount(avgAmount != null ? avgAmount.longValue() : 0L)
                .build();
    }

    private EarningsSummaryResponse.CurrencyBreakdown emptyBreakdown(String currency) {
        return EarningsSummaryResponse.CurrencyBreakdown.builder()
                .currency(currency)
                .grossCollected(0L)
                .platformFees(0L)
                .netPaid(0L)
                .paymentsCount(0L)
                .pendingCount(0L)
                .failedCount(0L)
                .paidPayouts(0L)
                .pendingPayouts(0L)
                .failedPayouts(0L)
                .build();
    }
}
