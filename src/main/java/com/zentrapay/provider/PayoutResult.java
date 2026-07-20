package com.zentrapay.provider;

import lombok.Builder;

/**
 * Outcome of initiating a payout to a seller.
 *
 * @param status            normalized status (payouts often start PENDING and
 *                          confirm later via a transfer webhook)
 * @param providerReference the provider's transfer reference
 * @param rawStatus         the provider's original status string
 */
@Builder
public record PayoutResult(
        ProviderStatus status,
        String providerReference,
        String rawStatus
) {
}
