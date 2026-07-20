package com.zentrapay.provider;

import lombok.Builder;

/**
 * Request to pay a seller from the platform wallet.
 *
 * @param accountNumber seller's payout account number
 * @param accountName   seller's resolved account name
 * @param bankCode      provider bank/institution code
 * @param amount        amount to send in minor units (after platform fee)
 * @param currency      ISO-4217 code
 * @param senderName    name shown as the sender on the transfer
 * @param narration     description shown on the transfer
 * @param reference     our unique payout reference (idempotency key)
 * @param type          rail hint required by some currencies (e.g. "EFT" for ZAR, "B2C" for KES); may be null
 */
@Builder
public record PayoutRequest(
        String accountNumber,
        String accountName,
        String bankCode,
        long amount,
        String currency,
        String senderName,
        String narration,
        String reference,
        String type
) {
}
