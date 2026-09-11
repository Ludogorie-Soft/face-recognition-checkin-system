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
        /** GPS accuracy radius in metres at scan time; null for clients that predate this field. */
        Double accuracyMeters,
        boolean locationValid,
        Double faceConfidence,
        boolean manualOverride,
        @NotNull LocalDateTime recordedAt,
        // Nullable for backward compatibility with clients that predate event IDs.
        UUID clientEventId,
        // Device connectivity at scan time; defaults to false for older clients.
        boolean createdOffline,
        // Stable per-device id and frontend version; nullable for older clients.
        String clientDeviceId,
        String appVersion
) {}
