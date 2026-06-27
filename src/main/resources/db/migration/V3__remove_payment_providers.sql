-- ============================================
-- V3: Remove payment_providers table
--
-- Use CASCADE to also drop the foreign key
-- constraint in payment_links that references it
-- ============================================

DROP TABLE IF EXISTS payment_providers CASCADE;