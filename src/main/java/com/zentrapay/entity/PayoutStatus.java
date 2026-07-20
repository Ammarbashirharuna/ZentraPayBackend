package com.zentrapay.entity;

/**
 * Status of a settlement (payout) to a seller. Matches the DB CHECK constraint
 * on payouts.status.
 */
public enum PayoutStatus {
    /** Created, not yet accepted by the provider (or awaiting a retry). */
    PENDING,
    /** Accepted by the provider, awaiting final confirmation via webhook. */
    PROCESSING,
    /** Provider confirmed the money reached the seller. */
    PAID,
    /** The transfer failed; eligible for retry. */
    FAILED
}
