CREATE TABLE hours_corrections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id       UUID             NOT NULL REFERENCES users(id),
    site_id         UUID             NOT NULL REFERENCES sites(id),
    date            DATE             NOT NULL,
    corrected_hours DOUBLE PRECISION NOT NULL,
    note            TEXT,
    created_by      UUID             REFERENCES users(id),
    created_at      TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP        NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_hours_correction UNIQUE (worker_id, site_id, date)
);
