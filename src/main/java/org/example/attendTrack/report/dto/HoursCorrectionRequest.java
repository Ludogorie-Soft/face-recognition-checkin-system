package org.example.attendTrack.report.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record HoursCorrectionRequest(
        @NotNull UUID workerId,
        @NotNull UUID siteId,
        @NotNull LocalDate date,
        @DecimalMin("0.0") double correctedHours,
        String note
) {}
