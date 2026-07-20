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
 * An inbound provider webhook, persisted for audit and debugging.
 *
 * Maps to: webhooks (see V1 migration). We record every authentic webhook we
 * receive along with whether processing succeeded, so failed events can be
 * inspected and replayed out of band.
 */
@Entity
@Table(name = "webhooks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Which provider sent it, e.g. CASHONRAILS. */
    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    /** Event name from the payload, e.g. charge.success (nullable if absent). */
    @Column(name = "event_type", length = 100)
    private String eventType;

    /** Raw payload as received, stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    /** Signature header value as received. */
    @Column(columnDefinition = "text")
    private String signature;

    /** True once we successfully acted on the event. */
    @Column(nullable = false)
    private Boolean processed;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /** Populated when processing failed, for debugging/replay. */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
