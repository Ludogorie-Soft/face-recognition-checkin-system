CREATE TABLE site_checkpoints (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id       UUID             NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    name          VARCHAR(255),
    lat           DOUBLE PRECISION NOT NULL,
    lng           DOUBLE PRECISION NOT NULL,
    radius_meters INTEGER          NOT NULL DEFAULT 200,
    created_at    TIMESTAMP        NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_site_checkpoints_site_id ON site_checkpoints (site_id);

-- Migrate existing site points to checkpoints
INSERT INTO site_checkpoints (site_id, lat, lng, radius_meters)
SELECT id, lat, lng, radius_meters FROM sites;
