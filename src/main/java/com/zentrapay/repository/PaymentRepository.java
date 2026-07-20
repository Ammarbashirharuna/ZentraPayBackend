package com.zentrapay.repository;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Look up by our idempotency key — used on verify and webhook handling. */
    Optional<Payment> findByProviderReference(String providerReference);

    boolean existsByProviderReference(String providerReference);

    /**
     * A seller's payments across all of their links, most recent first.
     * Scoped through the link's owner so sellers only see their own payments.
     */
    Page<Payment> findByPaymentLinkUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** Same, filtered by status. */
    Page<Payment> findByPaymentLinkUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, PaymentStatus status, Pageable pageable);

    /** Payments against a single link owned by the seller, most recent first. */
    Page<Payment> findByPaymentLinkIdAndPaymentLinkUserIdOrderByCreatedAtDesc(
            UUID paymentLinkId, UUID userId, Pageable pageable);

    /** A single payment scoped to the seller who owns its link. */
    Optional<Payment> findByIdAndPaymentLinkUserId(UUID id, UUID userId);

    /**
     * The most recent open (PENDING) payment for a given link and customer.
     * Used to dedupe checkout: a customer double-submitting the pay form reuses
     * this instead of creating a second PENDING payment for the same intent.
     */
    Optional<Payment> findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            UUID paymentLinkId, String customerEmail, PaymentStatus status);
}
