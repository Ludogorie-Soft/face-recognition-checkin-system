-- V11: Server-side normalization of check-in / check-out direction.
--
-- The terminal no longer decides the direction of a scan; it only reports that a worker was seen.
-- `type` becomes a DERIVED value, recomputed from the ordered event list for a (worker, site, day)
-- whenever new events arrive — which makes the result independent of arrival order and therefore
-- correct for offline terminals that sync late.
--
-- client_type    : the raw direction the terminal reported. Never changes; kept for audit so the
--                  Details view can show "terminal said X, stored as Y".
-- ignored        : the event is excluded from the session sequence (accidental re-scan, or a
--                  scheduler guess replaced by a real check-out). Rows are never deleted.
-- ignored_reason : RESCAN | SUPERSEDED

ALTER TABLE attendance
    ADD COLUMN client_type    VARCHAR(10),
    ADD COLUMN ignored        BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ignored_reason VARCHAR(30);

-- Backfill: existing rows keep their stored direction as the reported one, and none are ignored.
UPDATE attendance SET client_type = type WHERE client_type IS NULL;

-- The projection always loads a whole (worker, site, day); the existing index on
-- (worker_id, site_id, recorded_at) already serves it.
