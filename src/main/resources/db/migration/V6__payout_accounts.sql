

-- ============================================
-- V6: Generalize bank_accounts -> payout_accounts
--
-- ZentraPay is going pan-African. A seller's payout destination is no longer
-- a Nigeria-only bank account. It now carries:
--   - country  (ISO-3166 alpha-2, e.g. NG, GH, KE, ZA)
--   - currency (ISO-4217, e.g. NGN, GHS, KES, ZAR, USD)
--   - method   (payout rail: BANK_ACCOUNT, MOBILE_MONEY, ...)
--
-- Provider switch: Paystack -> CashOnRails. CashOnRails has no subaccount/split,
-- so paystack_subaccount_code is replaced by provider_recipient_code (nullable),
-- and funds are paid out to this account via a transfer after payment.
-- ============================================

ALTER TABLE bank_accounts RENAME TO payout_accounts;

-- Provider-neutral fields
ALTER TABLE payout_accounts
    ADD COLUMN country VARCHAR(2) NOT NULL DEFAULT 'NG';
ALTER TABLE payout_accounts
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NGN';
ALTER TABLE payout_accounts
    ADD COLUMN method VARCHAR(30) NOT NULL DEFAULT 'BANK_ACCOUNT';
ALTER TABLE payout_accounts
    ADD COLUMN account_validated BOOLEAN NOT NULL DEFAULT FALSE;

-- Rename the Paystack-specific column to a provider-neutral one
ALTER TABLE payout_accounts
    RENAME COLUMN paystack_subaccount_code TO provider_recipient_code;

-- Mobile-money and international account identifiers can exceed 10 chars
ALTER TABLE payout_accounts
    ALTER COLUMN account_number TYPE VARCHAR(34);

-- Backfill: existing rows were verified NGN bank accounts
UPDATE payout_accounts
    SET account_validated = TRUE,
        country = 'NG',
        currency = 'NGN',
        method = 'BANK_ACCOUNT';

-- Drop the DEFAULTs now that existing rows are backfilled; the application
-- always supplies these values explicitly on insert.
ALTER TABLE payout_accounts ALTER COLUMN country DROP DEFAULT;
ALTER TABLE payout_accounts ALTER COLUMN currency DROP DEFAULT;
ALTER TABLE payout_accounts ALTER COLUMN method DROP DEFAULT;

ALTER TABLE payout_accounts
    ADD CONSTRAINT chk_payout_method
        CHECK (method IN ('BANK_ACCOUNT', 'MOBILE_MONEY', 'EFT'));

-- Keep the payment_links reference consistent with the new table name
ALTER TABLE payment_links
    RENAME COLUMN bank_account_id TO payout_account_id;

-- Rename indexes/constraints for clarity (index rename is metadata-only)
ALTER INDEX IF EXISTS idx_bank_accounts_user_id RENAME TO idx_payout_accounts_user_id;
