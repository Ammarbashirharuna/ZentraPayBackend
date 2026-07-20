package com.zentrapay.provider;

import lombok.Builder;

/**
 * Provider-neutral outcome of verifying a transaction.
 *
 * @param status   normalized status (see {@link ProviderStatus})
 * @param amount   amount actually paid, in minor units
 * @param currency ISO-4217 currency code
 * @param reference our transaction reference
 * @param rawStatus the provider's original status string (for logging/audit)
 */
@Builder
public record VerificationResult(
        ProviderStatus status,
        long amount,
        String currency,
        String reference,
        String rawStatus
) {
    public boolean isSuccess() {
        return status == ProviderStatus.SUCCESS;
    }
}
