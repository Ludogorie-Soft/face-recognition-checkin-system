package org.example.attendTrack.attendance.dto;

import jakarta.validation.constraints.NotNull;
import org.example.attendTrack.attendance.AttendanceType;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceRecord(
        @NotNull UUID workerId,
        @NotNull AttendanceType type,
        @NotNull Double lat,
        @NotNull Double lng,
        boolean locationValid,
        Double faceConfidence,
        boolean manualOverride,
        @NotNull LocalDateTime recordedAt
) {}
