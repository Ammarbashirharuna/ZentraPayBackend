package com.zentrapay.repository;

import com.zentrapay.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Look up an active key by its SHA-256 hash (used on every API-key request). */
    Optional<ApiKey> findByKeyHashAndIsActiveTrue(String keyHash);

    /** List a seller's API keys, most recent first. */
    Page<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** A single API key scoped to its owner. */
    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    /** Count active keys for a seller. */
    long countByUserIdAndIsActiveTrue(UUID userId);
}
