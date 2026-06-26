-- ============================================
-- V2: Email Verification Tokens
--
-- Purpose: Store tokens sent to users via email
-- to verify their email address
-- ============================================

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_verification_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index for fast token lookups
-- (we look up by token every time someone clicks the link)
CREATE INDEX idx_verification_token
    ON email_verification_tokens(token);

-- Index for finding tokens by user
CREATE INDEX idx_verification_user_id
    ON email_verification_tokens(user_id);