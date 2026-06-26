package com.zentrapay.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an email verification token
 *
 * Maps to: email_verification_tokens table
 *
 * Flow:
 * 1. User registers → we create this token
 * 2. We email the token to the user
 * 3. User clicks link → we find this token → verify user
 */
@Entity
@Table(name = "email_verification_tokens")
@Data
@NoArgsConstructor
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Which user this token belongs to
    // FetchType.LAZY = only load user data when we actually need it
    // (better performance - don't always load user just to check token)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The random string we send in the email link
    @Column(nullable = false, unique = true, length = 128)
    private String token;

    // Token expires after 24 hours
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Once used, can't be used again
    @Column(nullable = false)
    private Boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if this token has expired
     *
     * We check this when user clicks the verification link.
     * If expired, we reject it and ask them to request a new one.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}