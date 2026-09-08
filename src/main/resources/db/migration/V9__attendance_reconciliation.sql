-- V9: Server-side reconciliation of the check-in / check-out state machine.
--
-- client_event_id: stable UUID generated on the device for every attendance
--   event. Enables exact idempotent dedup regardless of recordedAt precision
--   and provides atomic (DB-level) protection against duplicate sync uploads.
-- anomaly / anomaly_reason: set by the sync reconciliation when an incoming
--   record violates the state machine (e.g. a second CHECK_IN while a session
--   is already open). The record is still stored (offline data is never lost),
--   but flagged so it surfaces in the admin report.

-- created_offline: captured on the device (navigator.onLine was false at the moment
--   of the scan). Unambiguous signal that the event was recorded without connectivity,
--   independent of any device-clock / timezone skew between recorded_at and synced_at.

ALTER TABLE attendance
    ADD COLUMN client_event_id UUID,
    ADD COLUMN anomaly         BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN anomaly_reason  VARCHAR(40),
    ADD COLUMN created_offline BOOLEAN     NOT NULL DEFAULT false;

-- Partial unique index: two concurrent sync requests carrying the same event
-- can no longer both insert. Legacy rows (NULL client_event_id) are unaffected.
CREATE UNIQUE INDEX ux_attendance_client_event_id
    ON attendance (client_event_id)
    WHERE client_event_id IS NOT NULL;
