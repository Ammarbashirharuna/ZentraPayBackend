# Architecture Decisions

This document records the significant technical decisions behind Zetapay's
payment flow and why they were made.

## 1. A single `PaymentProvider` interface, not direct vendor calls

The codebase previously called Paystack directly, which meant a provider switch
touched services all over the app. Everything now talks to the outside payments
world through one interface (`com.zentrapay.provider.PaymentProvider`), with
`CashOnRailsClient` as the only implementation.

- Vendor specifics (endpoints, HMAC/RSA signing, encrypted payloads, access
  codes) stay inside the client.
- Adding a second provider — for a region CashOnRails doesn't cover — is a new
  class, not a rewrite.
- The domain and services deal only in neutral records
  (`InitializeRequest`, `VerificationResult`, `PayoutRequest`, ...).

## 2. Collect-then-payout, not split payments

CashOnRails has no subaccount/split concept. Funds are collected into the
platform wallet, then paid out to the seller via `payout()` after the payment
is confirmed. This is intentional: the spread between what the customer pays and
what the seller receives is where the platform fee lives (see REVENUE.md).

## 3. Money is stored in minor units

All amounts (`payment_links.amount`, `payments.amount`) are `BIGINT` in minor
units (kobo, cents). No floating point anywhere in the money path, so there is
no rounding drift.

## 4. Server-side confirmation is the only source of truth

We never trust the browser redirect or a raw webhook body to mark a payment
paid. Both the `/pay/callback` redirect and the provider webhook funnel into one
place — `PaymentConfirmationService.confirmByReference` — which:

1. Re-verifies the transaction with the provider (`verify`).
2. Checks the paid amount/currency matches what we asked for (defense in depth
   against a tampered redirect).
3. Marks the payment `COMPLETED` and bumps link usage counters.
4. Pays the seller their amount minus the platform fee.

## 5. Idempotency everywhere on the money path

- Each payment gets a unique reference (`ZP-<uuid>`) that doubles as the
  provider idempotency key.
- `confirmByReference` short-circuits if the payment is already `COMPLETED`, so
  a duplicate webhook or a callback racing a webhook can never double-pay a
  seller.
- Payouts use a derived reference (`PO-<paymentRef>`).

## 6. Payout failure does not roll back a confirmed payment

Settlement to the seller runs *after* the payment is persisted as `COMPLETED`.
If the payout call fails, we log it for out-of-band retry rather than throwing
away a payment we've already collected. The customer's money is in; the seller
transfer is a separate, retriable step.

## 7. Webhooks: authenticate before parsing

`WebhookService` verifies the signature (and an optional static bearer key,
compared in constant time) *before* reading anything out of the body. Reads use
the raw request bytes — a parsed-and-reserialized body would change the bytes
and break signature verification. Authentic-but-unprocessable webhooks are
acknowledged with 200 so the provider stops retrying; bad signatures get 401.

## 8. Pan-African payouts

Sellers receive money by more than bank transfer. `PayoutMethod` models
`BANK_ACCOUNT`, `MOBILE_MONEY`, and `EFT`, and the payout carries a rail hint
(`type`) some currencies require. This is what makes the product work across
NGN, GHS, KES, and ZAR rather than Nigeria only.

## 9. Short codes

Payment links expose a 7-character short code from an unambiguous alphabet
(no `0/O/1/I/l`), generated with a CSPRNG and retried on collision. This keeps
shareable URLs short and human-readable without leaking sequential IDs.
