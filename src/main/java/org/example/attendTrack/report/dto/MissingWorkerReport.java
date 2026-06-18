package org.example.attendTrack.report.dto;

import java.util.UUID;

public record MissingWorkerReport(
        UUID workerId,
        String workerName
) {}
