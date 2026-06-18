package org.example.garant.attendance.dto;

import java.util.List;

public record AttendanceSyncResponse(
        int saved,
        int skipped,
        List<String> errors
) {}
