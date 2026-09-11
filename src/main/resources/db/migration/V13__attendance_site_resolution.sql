-- The terminal is no longer trusted to decide WHICH SITE a scan belongs to.
--
-- Until now the device picked the site itself and, whenever no zone matched the GPS fix, it
-- silently fell back to the worker's first site assignment. That filed scans at sites 76 and
-- 163 km away and flagged them "outside zone"; changing the site by hand made them valid again.
--
-- From here on the server re-resolves the site from the coordinates, and these two columns keep
-- the raw claim auditable — mirroring the client_type / type split introduced in V11.

ALTER TABLE attendance ADD COLUMN client_site_id UUID REFERENCES sites(id);
COMMENT ON COLUMN attendance.client_site_id IS
    'Site the terminal claimed. site_id is the server-resolved value; the two differ when the device guessed wrong.';

ALTER TABLE attendance ADD COLUMN distance_meters INTEGER;
COMMENT ON COLUMN attendance.distance_meters IS
    'Distance in metres from the recorded position to the resolved site zone. 0 = inside; > 0 = outside by that much.';

-- Existing rows: the stored site is all we know, so the claim equals the resolution. Distance is
-- left NULL rather than guessed — it is only meaningful for rows the resolver has actually seen.
UPDATE attendance SET client_site_id = site_id;
