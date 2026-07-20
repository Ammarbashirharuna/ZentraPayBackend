package com.zentrapay.repository;

import com.zentrapay.entity.PaymentLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    /** Look up a link by its public short code (used on the checkout page). */
    Optional<PaymentLink> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /** A seller's own links, most recent first. */
    Page<PaymentLink> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** A single link scoped to its owner, so sellers can't touch others' links. */
    Optional<PaymentLink> findByIdAndUserId(UUID id, UUID userId);
}
