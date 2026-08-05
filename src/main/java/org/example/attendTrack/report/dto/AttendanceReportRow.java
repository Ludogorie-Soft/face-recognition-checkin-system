package org.example.attendTrack.report.dto;

import org.example.attendTrack.attendance.AttendanceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceReportRow(
        UUID id,
        UUID workerId,
        String workerName,
        String companyName,
        String siteName,
        LocalDate date,
        AttendanceType type,
        LocalDateTime recordedAt,
        double lat,
        double lng,
        boolean locationValid,
        Double faceConfidence,
        boolean manualOverride
) {}
