# Plan: next features

## Finding first (important)

Your "build 1 to 3" referred to the DEV_PLAN "next steps" list. Items 1–4 on
that list are **already implemented** in the current (uncommitted) working tree
— the plan doc was just never updated:

- **#1 Payout reconciliation** → `PayoutReconciliationJob` (scheduled retry,
  backoff, max attempts).
- **#2 Transfer/payout webhooks** → `PayoutService.handleTransferWebhook` +
  `WebhookService` routing `PO-` references.
- **#3 Persisted webhook log** → `WebhookEvent` entity → `webhooks` table.
- **#4 Integration tests** → Testcontainers `AbstractIntegrationTest` +
  `PayoutRepositoryIT`.

Build is clean; all 16 tests pass. The only genuine remainder from that list is
**#5 checkout idempotency**.

So this plan builds the real remaining gap plus two features that solve concrete
seller/customer pain points and reuse infra that already exists.

## Scope

### Feature A — Checkout idempotency (DEV_PLAN #5)
**Pain:** a customer double-clicking "Pay" creates duplicate `PENDING` payments
and two provider checkouts for one intent.

**Approach:** in `CheckoutService.initiatePayment`, before creating a payment,
look for an existing reusable `PENDING` payment for the same
`(link, customerEmail)` and reuse it instead of creating a new row + new
provider init.
- Add `PaymentRepository.findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(...)`.
- Reuse only if the existing PENDING payment is recent (configurable window,
  `checkout.reuse-window-minutes`, default 30) — otherwise start fresh so a
  stale/abandoned attempt doesn't trap the customer.
- On reuse, re-call `provider.initialize` with the **same reference** (the
  reference is already the provider idempotency key) and return that checkout
  URL. This keeps one payment per intent without persisting the checkout URL.
- No schema change.

### Feature B — Payment notification + receipt emails
**Pain:** neither the seller ("you got paid") nor the customer (a receipt) is
told anything after payment. `EmailService`'s own comments list these as TODO
and the Resend infra is already wired.

**Approach:**
- Extend `EmailService` with `sendPaymentReceivedEmail(seller, payment, link)`
  and `sendPaymentReceiptEmail(customerEmail, payment, link)`, mirroring the
  existing HTML-builder style. Money is formatted from minor units to a
  display string.
- Call both from `PaymentConfirmationService` **after** the payment is marked
  COMPLETED and settlement is kicked off. Wrap in try/catch and never let an
  email failure affect the money path — same stance `EmailService` already
  takes for verification email (log, don't throw).
- Seller address comes from `link.getUser().getEmail()`; customer address is
  `payment.getCustomerEmail()`.
- No schema change.

### Feature C — Seller earnings summary endpoint
**Pain:** sellers can list individual payments but have no at-a-glance view of
total collected, fees, net paid out, and what's still pending/failed.

**Approach:**
- New `GET /api/v1/payments/summary` on the existing authenticated
  `PaymentController`, backed by a new `getMySummary()` in `PaymentQueryService`
  (reuses the existing `getCurrentUser()` scoping).
- Aggregate with repository count/sum queries scoped to the seller:
  - gross collected + count of COMPLETED payments,
  - total platform fees and net (computed with the same `feeBasisPoints`),
  - pending/failed payment counts,
  - payout totals by status (PAID / PENDING+PROCESSING / FAILED) via
    `PayoutRepository` aggregate queries.
- Add JPQL `@Query` aggregate methods to `PaymentRepository` and
  `PayoutRepository` (grouped by currency, since a seller may collect in
  several currencies — return a per-currency breakdown).
- New DTO `EarningsSummaryResponse` (list of per-currency rows). No schema
  change.

## Explicitly out of scope
- **Refund flow.** `PaymentStatus.REFUNDED` exists but wiring a real refund
  needs the CashOnRails refund endpoint, which is **not** in the known API
  contract, and it moves money. Needs provider-API confirmation first — will
  flag separately rather than guess.

## Verification
- Unit tests: checkout reuse (reuse within window / new outside window / new
  for different email), summary aggregation math, and that a thrown email send
  does not break confirmation.
- Extend existing `PaymentConfirmationServiceTest` for the email-failure
  isolation case.
- Run `./mvnw test` (all existing 16 + new must pass) before finishing.
- Update `docs/DEV_PLAN.md` to reflect reality (mark #1–#4 done, #5 done) and
  note the new features + the deferred refund flow.
