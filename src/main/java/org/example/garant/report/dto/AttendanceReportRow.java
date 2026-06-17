package org.example.garant.report.dto;

import org.example.garant.attendance.AttendanceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceReportRow(
        UUID workerId,
        String workerName,
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
