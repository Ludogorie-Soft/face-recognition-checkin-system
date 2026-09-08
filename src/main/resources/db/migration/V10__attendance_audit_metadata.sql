-- V10: Explicit record origin + audit metadata.
--
-- source: replaces the (manual_override && manager IS NULL) heuristic. Set explicitly
--   on every new record going forward.
-- ip_address / user_agent: captured server-side from the request that uploaded the
--   record (the verify terminal), via nginx X-Real-IP / X-Forwarded-For.
-- client_device_id: stable per-device UUID from the terminal's localStorage.
-- app_version: frontend build version at record time.

ALTER TABLE attendance
    ADD COLUMN source           VARCHAR(30),
    ADD COLUMN ip_address       VARCHAR(64),
    ADD COLUMN user_agent       TEXT,
    ADD COLUMN client_device_id VARCHAR(64),
    ADD COLUMN app_version      VARCHAR(30);

-- Best-effort backfill for historical rows:
--   manager present            → admin dashboard entry
--   not a manual override      → normal terminal face recognition
-- The ambiguous case (manual_override = true AND manager_id IS NULL) could be either a
-- scheduler auto-checkout or a terminal manual confirmation — these are byte-for-byte
-- identical in the old data, so they are intentionally left NULL ("legacy / unknown").
UPDATE attendance SET source = 'ADMIN_MANUAL'  WHERE manager_id IS NOT NULL AND manual_override = true;
UPDATE attendance SET source = 'TERMINAL_FACE' WHERE manual_override = false;
