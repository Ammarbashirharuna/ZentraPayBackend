-- ============================================
-- V4: Bank Accounts
--
-- Stores seller bank account details
-- and their Paystack subaccount information
-- ============================================

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    bank_name VARCHAR(100) NOT NULL,
    bank_code VARCHAR(10) NOT NULL,
    account_number VARCHAR(10) NOT NULL,
    account_name VARCHAR(200) NOT NULL,
    paystack_subaccount_code VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_bank_account_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_bank_accounts_user_id
    ON bank_accounts(user_id);