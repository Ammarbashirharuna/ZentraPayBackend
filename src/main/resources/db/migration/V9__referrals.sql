-- ============================================
-- V9: Referral / affiliate program
--
-- Each seller gets a unique referral code. When a new seller signs up using
-- that code, the referrer is tracked. Referred sellers' payment fees can
-- optionally be reduced as an incentive.
-- ============================================

CREATE TABLE referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    referral_code VARCHAR(20) NOT NULL UNIQUE,
    referred_by_user_id UUID,
    used_count INTEGER NOT NULL DEFAULT 0,
    total_earnings BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_referrals_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_referrals_referred_by
        FOREIGN KEY (referred_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_referral_code_format
        CHECK (referral_code ~ '^[A-Z0-9]{6,12}$')
);

CREATE UNIQUE INDEX idx_referrals_code ON referrals(referral_code);
CREATE INDEX idx_referrals_user ON referrals(user_id);
CREATE INDEX idx_referrals_referred_by ON referrals(referred_by_user_id);
