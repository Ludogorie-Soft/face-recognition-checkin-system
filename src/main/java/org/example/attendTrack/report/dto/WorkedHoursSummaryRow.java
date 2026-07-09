package org.example.attendTrack.report.dto;

import java.util.List;
import java.util.UUID;

/**
 * One row in the summary worked hours report, grouped by worker across all sites.
 * details holds every individual WorkedHoursRow for that worker — used by the
 * correction dialog in the frontend to allow per-day/per-site editing.
 */
public record WorkedHoursSummaryRow(
        UUID workerId,
        String workerName,
        List<WorkedHoursRow> details,
        double totalHours
) {}
