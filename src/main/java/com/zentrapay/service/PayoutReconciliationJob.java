package com.zentrapay.service;

import com.zentrapay.entity.Payout;
import com.zentrapay.entity.PayoutStatus;
import com.zentrapay.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Periodically retries settlements that did not complete on the first attempt.
 *
 * Two classes of work are picked up:
 *   - payouts stuck PENDING that were never successfully attempted (e.g. the
 *     provider was down when the payment confirmed), and
 *   - payouts that FAILED and are past the retry backoff window.
 *
 * Each is retried until {@code maxAttempts} is reached, after which it is left
 * FAILED for manual intervention (and surfaced by the record's lastError).
 * PROCESSING payouts are left alone — those await a transfer webhook.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutReconciliationJob {

    private final PayoutRepository payoutRepository;
    private final PayoutService payoutService;

    /** Give up automatic retries after this many attempts. */
    @Value("${payout.max-attempts:6}")
    private int maxAttempts;

    /** Wait this long after a failed attempt before retrying (minutes). */
    @Value("${payout.retry-backoff-minutes:10}")
    private long retryBackoffMinutes;

    /** How many payouts to process per run. */
    @Value("${payout.reconcile-batch-size:50}")
    private int batchSize;

    /**
     * Runs on a fixed delay (default every 5 min; first run after 2 min).
     * fixedDelay means the next run only starts after the previous one finishes,
     * so runs never overlap.
     */
    @Scheduled(fixedDelayString = "${payout.reconcile-interval-ms:300000}",
            initialDelayString = "${payout.reconcile-initial-delay-ms:120000}")
    public void reconcile() {
        List<Payout> due = findDue();
        if (due.isEmpty()) {
            return;
        }
        log.info("Payout reconciliation: retrying {} payout(s)", due.size());
        for (Payout payout : due) {
            try {
                payoutService.attempt(payout);
            } catch (RuntimeException ex) {
                // attempt() already records failures; guard the loop regardless.
                log.error("Reconciliation attempt threw for payout {}: {}",
                        payout.getReference(), ex.getMessage());
            }
        }
    }

    private List<Payout> findDue() {
        List<PayoutStatus> retryable = List.of(PayoutStatus.PENDING, PayoutStatus.FAILED);
        PageRequest page = PageRequest.of(0, batchSize);

        List<Payout> due = new ArrayList<>(
                payoutRepository
                        .findByStatusInAndAttemptsLessThanAndLastAttemptAtIsNullOrderByCreatedAtAsc(
                                retryable, maxAttempts, page));

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(retryBackoffMinutes);
        due.addAll(payoutRepository
                .findByStatusInAndAttemptsLessThanAndLastAttemptAtBeforeOrderByCreatedAtAsc(
                        retryable, maxAttempts, cutoff, page));
        return due;
    }
}
