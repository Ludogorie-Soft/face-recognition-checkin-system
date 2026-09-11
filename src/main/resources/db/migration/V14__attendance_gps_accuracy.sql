-- The terminal has always known how good its GPS fix was — useGeoLocation reads `accuracy` from
-- watchPosition and even shows it on screen — and has always thrown the number away before syncing.
--
-- Zones here are tight: the corridors along ул. "Ангел Кънчев" are 10 m wide, and the scans that
-- landed inside them did so by 3 to 9 metres. At that scale a ±15 m fix decides in-zone by coin
-- toss. Storing the accuracy lets the resolver widen the zone by the fix's own error margin, and
-- lets anyone reading a record afterwards see how much the position was worth.

ALTER TABLE attendance ADD COLUMN accuracy_meters DOUBLE PRECISION;
COMMENT ON COLUMN attendance.accuracy_meters IS
    'GPS accuracy radius in metres reported by the device at scan time. NULL for records synced by clients that predate this column.';
