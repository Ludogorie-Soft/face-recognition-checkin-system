package org.example.attendTrack.attendance.dto;

import jakarta.validation.constraints.NotNull;
import org.example.attendTrack.attendance.AttendanceType;

import java.time.LocalDate;
import java.util.UUID;

public record ManualAttendanceRequest(
        @NotNull UUID workerId,
        @NotNull UUID siteId,
        @NotNull AttendanceType type,
        LocalDate date   // null → use today; used to place the record on the correct day
) {}
