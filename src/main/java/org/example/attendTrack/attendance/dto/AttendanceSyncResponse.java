package org.example.attendTrack.attendance.dto;

import java.util.List;

public record AttendanceSyncResponse(
        int saved,
        int skipped,
        // Number of saved records flagged as anomalous by the reconciliation.
        int anomalies,
        List<String> errors
) {}
