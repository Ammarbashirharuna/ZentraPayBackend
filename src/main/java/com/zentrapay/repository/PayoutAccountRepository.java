package com.zentrapay.repository;

import com.zentrapay.entity.PayoutAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for seller payout accounts.
 */
@Repository
public interface PayoutAccountRepository extends JpaRepository<PayoutAccount, UUID> {

    /** The seller's payout account (one per seller for now). */
    Optional<PayoutAccount> findByUserId(UUID userId);

    /** Whether the seller already has a payout account set up. */
    boolean existsByUserId(UUID userId);

    /** Only the account if it is currently active. */
    Optional<PayoutAccount> findByUserIdAndIsActiveTrue(UUID userId);
}
