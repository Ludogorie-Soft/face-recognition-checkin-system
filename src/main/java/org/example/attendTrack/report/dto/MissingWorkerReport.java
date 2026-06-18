package org.example.garant.report.dto;

import java.util.UUID;

public record MissingWorkerReport(
        UUID workerId,
        String workerName
) {}
