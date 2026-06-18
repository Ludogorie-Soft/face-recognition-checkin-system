package org.example.attendTrack.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AttendanceSyncRequest(
        @NotNull UUID siteId,
        @NotEmpty @Valid List<AttendanceRecord> records
) {}
