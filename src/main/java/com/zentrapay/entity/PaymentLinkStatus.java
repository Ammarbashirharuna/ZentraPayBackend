package com.zentrapay.entity;

/**
 * Lifecycle of a payment link. Matches the DB CHECK constraint on
 * payment_links.status.
 */
public enum PaymentLinkStatus {
    /** Accepting payments. */
    ACTIVE,
    /** A single-use link that has been paid. */
    PAID,
    /** Past its expiry timestamp. */
    EXPIRED,
    /** Soft-deleted by the seller. */
    DELETED
}
