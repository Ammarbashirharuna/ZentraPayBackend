package com.zentrapay.provider;

/**
 * Payment Provider abstraction.
 *
 * ZentraPay talks to the outside payments world ONLY through this interface.
 * The concrete implementation ({@link CashOnRailsClient}) knows the vendor
 * specifics (endpoints, signing, field names); the rest of the application
 * depends only on this contract.
 *
 * Why an interface (and not direct calls to CashOnRails)?
 * - The project was previously wired directly to Paystack and switching cost
 *   real churn. A thin abstraction makes the next switch — or adding a second
 *   provider for a region CashOnRails does not cover — a new class, not a
 *   rewrite.
 * - It keeps vendor concepts (access codes, HMAC/RSA signatures, encrypted
 *
 *   payloads) out of the domain and services.
 *
 * Money model note:
 * CashOnRails has no "split/subaccount" concept. Funds are collected into the
 * platform wallet and later paid out to the seller via {@link #payout}. That
 * collect-then-payout shape is intentional — it is what lets ZentraPay deduct
 * a platform fee on the spread.
 */
public interface
PaymentProvider {

    /**
     * Initialize a checkout so a customer can pay.
     *
     * @param request amount (minor units), currency, customer email, our unique reference, redirect URL
     * @return the hosted checkout URL and provider reference/access code
     */
    InitializeResult initialize(InitializeRequest request);

    /**
     * Fetch the authoritative status of a transaction from the provider.
     * Always call this server-side before trusting a payment as complete —
     * never rely on the browser redirect alone.
     *
     * @param reference our unique transaction reference
     */
    VerificationResult verify(String reference);

    /**
     * Validate that a payout destination (bank/mobile-money account) exists and
     * resolve the account holder's name, so the seller can confirm during
     * onboarding before we ever send money there.
     */
    AccountValidationResult validateAccount(AccountValidationRequest request);

    /**
     * Send money from the platform wallet to a seller's payout account.
     * Used after a payment is confirmed, minus the platform fee.
     *
     * @return the provider's transfer reference and status
     */
    PayoutResult payout(PayoutRequest request);

    /**
     * Verify that an inbound webhook payload genuinely came from the provider
     * and was not tampered with in transit.
     *
     * @param rawPayload the exact raw request body bytes as received
     * @param signatureHeader the provider-supplied signature header value
     * @return true if the signature is valid
     */
    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);
}
