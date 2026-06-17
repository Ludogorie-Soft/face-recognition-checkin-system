-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Users (all roles: ADMIN, MANAGER, WORKER)
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255)        NOT NULL,
    email         VARCHAR(255)        NOT NULL UNIQUE,
    phone         VARCHAR(50),
    password_hash VARCHAR(255)        NOT NULL,
    role          VARCHAR(20)         NOT NULL,
    active        BOOLEAN             NOT NULL DEFAULT true,
    created_at    TIMESTAMP           NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'WORKER'))
);

-- Construction sites
CREATE TABLE sites (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(255)    NOT NULL,
    address          TEXT,
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    radius_meters    INTEGER          NOT NULL DEFAULT 200,
    work_start_time  TIME,
    work_end_time    TIME,
    active           BOOLEAN          NOT NULL DEFAULT true,
    created_at       TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- Site ↔ Manager assignments (many-to-many)
CREATE TABLE site_managers (
    site_id UUID NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (site_id, user_id)
);

-- Site ↔ Worker assignments (many-to-many)
CREATE TABLE site_workers (
    site_id     UUID      NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (site_id, user_id)
);

-- Face descriptors (one per worker)
CREATE TABLE face_descriptors (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID  NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    descriptor JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Attendance records (check-in / check-out)
CREATE TABLE attendance (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id        UUID             NOT NULL REFERENCES users(id),
    site_id          UUID             NOT NULL REFERENCES sites(id),
    manager_id       UUID             REFERENCES users(id),
    type             VARCHAR(10)      NOT NULL,
    lat              DOUBLE PRECISION NOT NULL,
    lng              DOUBLE PRECISION NOT NULL,
    location_valid   BOOLEAN          NOT NULL DEFAULT false,
    face_confidence  DOUBLE PRECISION,
    manual_override  BOOLEAN          NOT NULL DEFAULT false,
    recorded_at      TIMESTAMP        NOT NULL,
    synced_at        TIMESTAMP,
    created_at       TIMESTAMP        NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_attendance_type CHECK (type IN ('CHECK_IN', 'CHECK_OUT'))
);

CREATE INDEX idx_attendance_worker_site_date
    ON attendance (worker_id, site_id, recorded_at);

-- Web Push notification subscriptions
CREATE TABLE push_subscriptions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint   TEXT NOT NULL UNIQUE,
    p256dh     TEXT NOT NULL,
    auth       TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Notification log
CREATE TABLE notifications (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id  UUID        NOT NULL REFERENCES users(id),
    type     VARCHAR(50) NOT NULL,
    message  TEXT        NOT NULL,
    read     BOOLEAN     NOT NULL DEFAULT false,
    sent_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
