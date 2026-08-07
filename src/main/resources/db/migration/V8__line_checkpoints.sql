-- V8: Add line/corridor checkpoint support
-- checkpoint_type: 'POINT' (default) or 'LINE'
-- lat2/lng2: second endpoint for LINE checkpoints (null for POINT)
-- For LINE checkpoints, radiusMeters defines the corridor half-width (e.g. 7m)

ALTER TABLE site_checkpoints
    ADD COLUMN checkpoint_type VARCHAR(10) NOT NULL DEFAULT 'POINT',
    ADD COLUMN lat2            DOUBLE PRECISION,
    ADD COLUMN lng2            DOUBLE PRECISION;
