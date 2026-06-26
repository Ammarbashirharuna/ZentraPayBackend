-- ============================================
-- V1: Initial Schema
-- ZentraPay Database
-- ============================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================
-- TABLE: users
-- Stores seller accounts (people who create payment links)
-- ============================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);

-- ============================================
-- TABLE: payment_providers
-- Connected payment accounts (Paystack, Stripe)
-- ============================================
CREATE TABLE payment_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider_type VARCHAR(50) NOT NULL,
    api_key_public TEXT NOT NULL,
    api_key_secret_encrypted TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_providers_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_provider_type
        CHECK (provider_type IN ('PAYSTACK', 'STRIPE', 'FLUTTERWAVE')),
    CONSTRAINT uq_user_provider
        UNIQUE (user_id, provider_type)
);

CREATE INDEX idx_payment_providers_user_id ON payment_providers(user_id);
CREATE INDEX idx_payment_providers_active ON payment_providers(user_id, is_active);

-- ============================================
-- TABLE: payment_links
-- Payment links created by users
-- ============================================
CREATE TABLE payment_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider_id UUID NOT NULL,
    short_code VARCHAR(8) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    single_use BOOLEAN NOT NULL DEFAULT FALSE,
    max_uses INTEGER,
    current_uses INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    redirect_url TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_links_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_links_provider
        FOREIGN KEY (provider_id) REFERENCES payment_providers(id) ON DELETE RESTRICT,
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_link_status
        CHECK (status IN ('ACTIVE', 'PAID', 'EXPIRED', 'DELETED')),
    CONSTRAINT chk_currency
        CHECK (currency IN ('NGN', 'USD', 'GHS', 'KES', 'ZAR')),
    CONSTRAINT chk_current_uses CHECK (current_uses >= 0)
);

CREATE UNIQUE INDEX idx_payment_links_short_code ON payment_links(short_code);
CREATE INDEX idx_payment_links_user_id ON payment_links(user_id);
CREATE INDEX idx_payment_links_user_status ON payment_links(user_id, status);
CREATE INDEX idx_payment_links_created_at ON payment_links(created_at DESC);

-- ============================================
-- TABLE: payments
-- Actual payment transactions
-- ============================================
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_link_id UUID NOT NULL,
    customer_email VARCHAR(255) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payments_link
        FOREIGN KEY (payment_link_id) REFERENCES payment_links(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'))
);

CREATE UNIQUE INDEX idx_payments_provider_reference ON payments(provider_reference);
CREATE INDEX idx_payments_link_id ON payments(payment_link_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_customer_email ON payments(customer_email);
CREATE INDEX idx_payments_paid_at ON payments(paid_at DESC);

-- ============================================
-- TABLE: webhooks
-- Log of all webhook events received
-- ============================================
CREATE TABLE webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_type VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    signature TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_webhook_provider
        CHECK (provider_type IN ('PAYSTACK', 'STRIPE', 'FLUTTERWAVE')),
    CONSTRAINT chk_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_webhooks_processed ON webhooks(processed, created_at);
CREATE INDEX idx_webhooks_provider ON webhooks(provider_type, processed);