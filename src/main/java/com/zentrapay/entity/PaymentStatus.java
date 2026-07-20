package com.zentrapay.entity;

/**
 * Status of a customer payment. Matches the DB CHECK constraint on
 * payments.status.
 */
public enum PaymentStatus {
    /** Initialized, awaiting confirmation from the provider. */
    PENDING,
    /** Provider confirmed the money was received. */
    COMPLETED,
    /** Provider reported the payment failed or was abandoned. */
    FAILED,
    /** Money was returned to the customer. */
    REFUNDED
}
