package com.zentrapay.provider;

/**
 * Thrown when a payment provider call fails (network error, non-success
 * response, malformed body). Carries a human-readable message safe to surface
 * to callers; sensitive detail stays in logs.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
