package com.zentrapay.repository;

import com.zentrapay.entity.Payout;
import com.zentrapay.entity.PayoutStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByReference(String reference);

    Optional<Payout> findByProviderReference(String providerReference);

    Optional<Payout> findByPaymentId(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);

    /**
     * Retryable payouts: still PENDING or FAILED, not attempted since the given
     * cutoff (so we back off between retries and don't hammer a live attempt),
     * oldest first.
     */
    List<Payout> findByStatusInAndAttemptsLessThanAndLastAttemptAtBeforeOrderByCreatedAtAsc(
            List<PayoutStatus> statuses, int maxAttempts, LocalDateTime cutoff, Pageable pageable);

    /** Retryable payouts that have never been attempted (lastAttemptAt is null). */
    List<Payout> findByStatusInAndAttemptsLessThanAndLastAttemptAtIsNullOrderByCreatedAtAsc(
            List<PayoutStatus> statuses, int maxAttempts, Pageable pageable);

    /** A seller's payouts, scoped through the settled payment's link owner. */
    org.springframework.data.domain.Page<Payout>
            findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(
                    UUID userId, Pageable pageable);

    /** A single payout scoped to the seller who owns the underlying payment. */
    Optional<Payout> findByIdAndPaymentPaymentLinkUserId(UUID id, UUID userId);
}
