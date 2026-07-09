package org.example.attendTrack.report.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One row in the "by-site" worked hours report.
 * checkOut and calculatedHours are null when the shift is still open (no CHECK_OUT recorded).
 * inferredCheckOut is true when checkOut was not recorded directly but was inferred from
 *   the worker's next CHECK_IN at a different site on the same day (multi-site auto-close).
 * correctedHours and correctionNote are null when no manual correction exists.
 * effectiveHours = correctedHours if correction exists, otherwise calculatedHours.
 */
public record WorkedHoursRow(
        UUID workerId,
        String workerName,
        UUID siteId,
        String siteName,
        LocalDate date,
        LocalTime checkIn,
        LocalTime checkOut,        // null → open shift
        boolean inferredCheckOut,  // true → checkOut was auto-closed from next site's CHECK_IN
        Double calculatedHours,    // null → open shift
        Double effectiveHours,     // null → open shift with no correction
        Double correctedHours,     // null → no correction
        String correctionNote      // null → no correction
) {}
