package com.zentrapay.repository;

import com.zentrapay.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    /** Find a seller's referral record by their user ID. */
    Optional<Referral> findByUserId(UUID userId);

    /** Look up a referral by its code (used during registration). */
    Optional<Referral> findByReferralCodeIgnoreCase(String referralCode);

    /** Check if a user already has a referral code. */
    boolean existsByUserId(UUID userId);
}
