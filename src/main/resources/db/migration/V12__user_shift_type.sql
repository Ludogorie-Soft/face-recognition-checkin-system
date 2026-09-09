-- V12: Shift pattern per worker.
--
-- Guards work 12/24h shifts that cross midnight and outside normal working hours. They must not be
-- closed by the end-of-day auto-checkout, and their sessions must continue across the day boundary.
-- This is deliberately NOT a Role: guards have identical permissions to a regular worker, and role
-- is already used to gate face registration and worker-specific handling.

ALTER TABLE users
    ADD COLUMN shift_type VARCHAR(20) NOT NULL DEFAULT 'DAY';
