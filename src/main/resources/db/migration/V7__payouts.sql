-- ============================================
-- V7: Payouts
--
-- Persist each settlement to a seller so failed payouts can be retried and
-- reconciled out of band, and so transfer webhooks can update payout status.
--
-- One payout per payment (the settlement of that payment's net amount). The
-- reference is our idempotency key (PO-<paymentReference>), unique so a retry
-- or a duplicate transfer webhook never double-pays.
-- ============================================

CREATE TABLE payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL UNIQUE,
    payout_account_id UUID NOT NULL,

    -- Our idempotency key sent to the provider as the transfer reference.
    reference VARCHAR(255) NOT NULL UNIQUE,
    -- The provider's own transfer reference, once we have it.
    provider_reference VARCHAR(255),

    amount BIGINT NOT NULL,          -- seller net, minor units
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(20) NOT NULL,     -- PENDING, PROCESSING, PAID, FAILED
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    last_attempt_at TIMESTAMP,
    completed_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payout_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE,
    CONSTRAINT fk_payout_account
        FOREIGN KEY (payout_account_id) REFERENCES payout_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payout_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED'))
);

-- Reconciliation scans for retryable (PENDING/FAILED) payouts.
CREATE INDEX idx_payouts_status ON payouts(status);
CREATE INDEX idx_payouts_payment_id ON payouts(payment_id);
