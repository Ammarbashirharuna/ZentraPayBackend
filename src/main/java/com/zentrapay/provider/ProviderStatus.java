package com.zentrapay.provider;

/**
 * Normalized transaction/transfer status, mapped from provider-specific
 * strings. Paystack reports: success, pending, failed, abandoned.
 */
public enum
ProviderStatus {
    SUCCESS,
    PENDING,
    FAILED,
    ABANDONED,
    UNKNOWN;

    /**
     * Map a raw provider status string onto our enum. Defensive: anything
     * unrecognized becomes UNKNOWN rather than throwing, so a new provider
     * status never crashes webhook processing.
     */
    public static ProviderStatus fromRaw(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.trim().toLowerCase()) {
            case "success", "successful", "completed", "paid" -> SUCCESS;
            case "pending", "processing" -> PENDING;
            case "failed", "failure", "declined" -> FAILED;
            case "abandoned", "cancelled", "canceled" -> ABANDONED;
            default -> UNKNOWN;
        };
    }
}
