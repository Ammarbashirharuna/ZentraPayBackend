-- ============================================
-- V8: Checkout branding + schema alignment
--
-- Adds seller branding fields to payment_links so the checkout page can be
-- customized per seller/link (logo, colors, thank-you message).
--
-- Also aligns the webhooks.event_type column with the WebhookEvent entity
-- (nullable), since CashOnRails webhooks sometimes omit the event name.
-- ============================================

-- Checkout branding columns (all nullable — backward-compatible with existing rows)
ALTER TABLE payment_links
    ADD COLUMN logo_url TEXT,
    ADD COLUMN brand_color VARCHAR(7),
    ADD COLUMN accent_color VARCHAR(7),
    ADD COLUMN thank_you_message TEXT;

-- Fix: entity has eventType nullable but V1 migration declared it NOT NULL
ALTER TABLE webhooks
    ALTER COLUMN event_type DROP NOT NULL;
