package com.zentrapay.provider;

import lombok.Builder;

/**
 * Outcome of validating a payout account.
 *
 * @param valid       whether the account resolved successfully
 * @param accountName the resolved account holder name (shown to seller to confirm)
 */
@Builder
public record AccountValidationResult(
        boolean valid,
        String accountName
) {
}
