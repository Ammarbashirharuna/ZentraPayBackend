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
- `CheckoutServiceTest` — checkout reuse (within window / outside window / different email).
- `PayoutServiceTest` — fee math, attempt success/failure, transfer webhook, idempotency.
- `PayoutRepositoryIT` — integration tests against real Postgres via Testcontainers.

All tests pass.

### Phase 8 — Checkout idempotency (DEV_PLAN #5)
In `CheckoutService.initiatePayment`, a recent PENDING payment for the same
`(link, customerEmail)` is reused instead of creating a duplicate row + new
provider init. Configurable window via `checkout.reuse-window-minutes` (default 30).

### Phase 9 — Payment notifications
`EmailService` extended with:
- `sendPaymentReceivedEmail` — notifies seller of incoming payment.
- `sendPaymentReceiptEmail` — sends customer a receipt with amount, date, reference.
Both called from `PaymentConfirmationService` after settlement, fire-and-forget.

### Phase 10 — Earnings summary & analytics
`PaymentQueryService.getMySummary()` — aggregated earnings across all currencies:
gross collected, platform fees, net paid, pending/failed counts, payout status
breakdown per currency.
`PaymentQueryService.getMyAnalytics()` — daily revenue trends (30 days),
per-link performance metrics.

### Phase 11 — Custom checkout branding
`PaymentLink` entity extended with `logoUrl`, `brandColor`, `accentColor`,
`thankYouMessage`. V8 migration. Branding exposed on both seller-facing
`PaymentLinkResponse` and public `PublicPaymentLinkResponse`.

### Phase 12 — Payment reminders / expiry nudges
`PaymentReminderJob` — scheduled job that:
- Emails customers about PENDING payments on links expiring within 24 hours.
- Sends abandoned checkout reminders for payments idle > 2 hours.
Configurable via `reminder.hours-before-expiry` and `reminder.abandoned-hours`.

### Phase 13 — Referral program
`Referral` entity (V9), `ReferralService`, `ReferralController`. Each seller
gets a unique 8-character code. `GET /api/v1/referrals/me` returns the code
and stats. Referral codes applied during registration.

### Phase 14 — API key management
`ApiKey` entity (V10), `ApiKeyService`, `ApiKeyController`. Sellers generate
API keys (SHA-256 hashed, never stored plaintext). Keys carry optional
permission scopes and usage metadata. `ApiKeyAuthenticationFilter` authenticates
requests via `X-API-Key` header.

### Phase 15 — Security hardening
- **CORS** — `CorsConfig` allows configurable origins via `APP_CORS_ORIGINS`.
- **Rate limiting** — `RateLimitFilter` enforces per-IP limits (15/min on auth, 100/min general).
- **Logging fix** — `GlobalExceptionHandler` now uses `log.error()` instead of `ex.printStackTrace()`.
- **Schema alignment** — V8 migration makes `webhooks.event_type` nullable to match the entity.
- **Duplicate import** — Fixed in `AuthService.java`.

## Explicitly out of scope
- **Refund flow.** `PaymentStatus.REFUNDED` exists but wiring a real refund
  needs the CashOnRails refund endpoint, which is not in the known API
  contract. Will be added when provider confirms support.
- **Multi-currency wallet.** Requires ledger accounting and compliance review.
  Deferred to Phase 2.
- **Subscription billing.** Needs recurring charge orchestration. Deferred to
  Phase 2.
