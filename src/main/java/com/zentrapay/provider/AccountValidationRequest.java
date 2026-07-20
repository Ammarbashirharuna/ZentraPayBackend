package com.zentrapay.provider;

import lombok.Builder;

/**
 * Request to resolve/validate a payout account before saving it.
 *
 * @param accountNumber the seller's account number (or mobile-money number)
 * @param bankCode      provider bank/institution code for the currency
 * @param currency      ISO-4217 code, tells the provider which rail to check
 */
@Builder
public record AccountValidationRequest(
        String accountNumber,
        String bankCode,
        String currency
) {
}
