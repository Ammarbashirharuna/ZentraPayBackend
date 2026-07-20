# Development Plan

Status of the payment-flow build. Phases were delivered in order; each was
compiled and (where logic warranted) unit-tested before moving on.

## Delivered

### Phase 0 — Build baseline
Fixed the `JwtAuthenticationFilter` package so the project compiled cleanly.

### Phase 1 — Provider abstraction
`PaymentProvider` interface + neutral request/result records
(`InitializeRequest/Result`, `VerificationResult`, `AccountValidationRequest/
Result`, `PayoutRequest/Result`, `ProviderStatus`) and the `CashOnRailsClient`
implementation with HMAC/RSA signing. See DECISIONS.md #1.

### Phase 2 — Pan-African payout accounts
`PayoutAccount` entity, `PayoutMethod` (`BANK_ACCOUNT`, `MOBILE_MONEY`, `EFT`),
account validation before save, and the `V6` migration renaming bank-account
concepts to the provider-neutral payout model.

### Phase 3 — Payment links
`PaymentLink` entity + `PaymentLinkStatus`, repository, DTOs, `PaymentLinkService`
(CSPRNG short codes, requires an active validated payout account), and the
authenticated `PaymentLinkController` (`/api/v1/payment-links`).

### Phase 4 — Public checkout
`Payment` entity + `PaymentStatus`, repository, `CheckoutService`, and the
public `CheckoutController` (`/api/v1/pay`). Amount/currency are always read
from the stored link, never the client. Creates a `PENDING` payment with a
unique reference and returns the provider checkout URL.

### Phase 5 — Confirmation, webhooks & settlement
`PaymentConfirmationService` (the single, idempotent source of truth for a
successful payment; verifies, marks completed, settles to seller net of fee),
`WebhookService` + `WebhookController` (signature + optional bearer key,
verified before parsing), and the `/pay/callback` redirect target.

### Phase 6 — Documentation
DECISIONS.md, REVENUE.md, this plan.

### Phase 7 — Tests
Unit tests for the money-critical logic:
- `PaymentConfirmationServiceTest` — success + fee math, idempotency,
  amount-mismatch refusal, failure handling, single-use link transition.
- `WebhookServiceTest` — signature gating, empty payload, nested reference
  extraction, bearer-key enforcement.

All 9 unit tests pass. (`ByteBuddy` experimental flag added to Surefire so
Mockito can mock concrete classes on JDK 23.)

## Not yet done / next steps

- **Payout reconciliation.** Payout failures are logged for retry but there is
  no scheduled job that re-attempts them or reconciles transfer webhooks. This
  is the most important gap before production.
- **Transfer/payout webhooks.** Inbound webhooks currently drive payment
  confirmation; payout status updates from the provider aren't yet consumed.
- **Persisted webhook log.** Webhook events are logged to the app log, not the
  `webhooks` table; wiring a `Webhook` entity would aid audit/debugging.
- **Integration tests.** `contextLoads` needs a live DB + env. A Testcontainers
  Postgres profile would let the full Flyway + JPA validate path run in CI.
- **Idempotency on initiate.** A customer double-submitting the checkout form
  creates two `PENDING` payments; only one can succeed, but deduping on
  (link, email, open payment) would be cleaner.
