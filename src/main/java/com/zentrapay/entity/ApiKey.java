package com.zentrapay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An API key for programmatic access to Zetapay.
 *
 * Maps to: api_keys (see V10 migration).
 *
 * Keys are stored as SHA-256 hashes — the raw key is only shown once at
 * creation time. Each key has an optional permissions scope (JSONB array of
 * strings like {@code ["payments:read", "links:write"]}), a human-readable
 * name, and usage metadata.
 */
@Entity
@Table(name = "api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The seller who owns this key. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Human-readable name for the key (e.g. "Production server"). */
    @Column(nullable = false, length = 100)
    private String name;

    /** SHA-256 hash of the raw key — never store the raw key. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** First 8 characters of the raw key, for display (e.g. "zp_a1b2..."). */
    @Column(name = "key_prefix", nullable = false, length = 8)
    private String keyPrefix;

    /** JSONB array of permission strings (e.g. ["payments:read"]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String permissions;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
