package com.zentrapay.provider;

import lombok.Builder;

/**
 * Provider-neutral request to start a checkout.
 *
 * @param amount      amount in the currency's minor units (e.g. kobo, cents)
 * @param currency    ISO-4217 code (NGN, GHS, USD, KES, ZAR, ...)
 * @param email       customer email; provider sends a receipt here
 * @param reference   our unique transaction reference (idempotency key)
 * @param redirectUrl where the provider returns the customer after payment
 */
@Builder
public record InitializeRequest(
        long amount,
        String currency,
        String email,
        String reference,
        String redirectUrl
) {
}
