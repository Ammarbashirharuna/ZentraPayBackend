package com.zentrapay.provider;

import lombok.Builder;

/**
 * Result of initializing a checkout.
 *
 * @param checkoutUrl hosted page URL to redirect the customer to
 * @param accessCode  provider access code (for inline/SDK flows)
 * @param reference   the transaction reference echoed back by the provider
 */
@Builder
public record InitializeResult(
        String checkoutUrl,
        String accessCode,
        String reference
) {
}
