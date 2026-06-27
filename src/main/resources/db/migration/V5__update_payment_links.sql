-- ============================================
-- V5: Update payment_links table
--
-- Remove provider_id column (no longer needed)
-- Add bank_account_id column instead
-- Payment links are now linked to seller's
-- bank account, not a payment provider
-- ============================================

-- Remove old provider reference
ALTER TABLE payment_links
    DROP CONSTRAINT IF EXISTS fk_payment_links_provider;

ALTER TABLE payment_links
    DROP COLUMN IF EXISTS provider_id;

-- Add bank account reference
ALTER TABLE payment_links
    ADD COLUMN bank_account_id UUID;

ALTER TABLE payment_links
    ADD CONSTRAINT fk_payment_links_bank_account
        FOREIGN KEY (bank_account_id)
        REFERENCES bank_accounts(id)
        ON DELETE RESTRICT;