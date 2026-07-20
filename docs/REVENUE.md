# Revenue Model

## How ZentraPay makes money

ZentraPay charges a **platform fee on every successful payment**, taken from the
spread between what the customer pays and what the seller receives.

- The customer pays the full link amount `A`.
- The platform keeps a fee `F`.
- The seller is paid out `A - F`.

The money never splits at the provider. Funds are collected into the platform
wallet, and the payout to the seller is for the net amount, so the fee simply
stays behind in the wallet.

## Fee calculation

The fee is configured in **basis points** (1 bp = 0.01%):

```
platform.fee-basis-points  (env: PLATFORM_FEE_BASIS_POINTS, default 100 = 1%)

fee          = amount * feeBasisPoints / 10_000     (integer math, minor units)
sellerAmount = amount - fee
```

Worked example at the default 1% on an NGN 10,000.00 payment (amount is in kobo,
so `1_000_000`):

| Quantity      | Minor units | Display        |
|---------------|-------------|----------------|
| Customer pays | 1,000,000   | ₦10,000.00     |
| Platform fee  | 10,000      | ₦100.00        |
| Seller payout | 990,000     | ₦9,900.00      |

Because the math is integer division in minor units, there is no rounding drift
and the fee is always exact to the smallest currency unit.

## Where it happens in code

`PaymentConfirmationService.settleToSeller` computes `fee` and `sellerAmount`
right before issuing the payout. If `sellerAmount` would be zero or negative
(a fee misconfiguration, or a payment smaller than the fee), the payout is
skipped and logged rather than sending a non-positive transfer.

## Tuning the fee

The rate is a single environment variable, so it can be changed per environment
without a code change. Future extensions (per-seller pricing, per-currency
rates, fixed + percentage fees) would live in the same `settleToSeller` step.
