package org.example.attendTrack.attendance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure projection of one worker's raw scan events (for a single site and day) into the correct
 * CHECK_IN / CHECK_OUT sequence.
 *
 * <p>The terminal is NOT trusted to decide the direction of a scan — it only reports that a worker
 * was seen at a point in time. This class derives the direction from the ordered event list, which
 * makes the result <b>independent of the order in which events arrive</b>. That is what makes
 * offline sync correct: a late batch from an offline terminal simply re-runs the projection and the
 * day heals itself.
 *
 * <p>Rules, applied to events sorted by time:
 * <ol>
 *   <li>A day always starts with a CHECK_IN; types then alternate.</li>
 *   <li>Two kept events must be at least {@code minGap} apart — a closer one is an accidental
 *       re-scan and is ignored (it is never deleted, only flagged).</li>
 *   <li>{@link AttendanceSource#ADMIN_MANUAL} records are authoritative: their stored type is never
 *       changed, and they reset the expected next direction.</li>
 *   <li>A {@link AttendanceSource#SCHEDULER_AUTO} check-out only survives while a session is
 *       actually open; if a real check-out already closed it, the auto guess is superseded.</li>
 * </ol>
 *
 * <p>This class has no dependencies on Spring or JPA so it can be tested exhaustively in isolation.
 */
public final class SessionProjector {

    /** Why an event is excluded from the session sequence. */
    public enum IgnoreReason {
        /** Too close to the previous kept event — an accidental double scan. */
        RESCAN,
        /** A scheduler auto-checkout that a real check-out has replaced. */
        SUPERSEDED
    }

    /** One stored attendance row, as input to the projection. */
    public record Event(UUID id, LocalDateTime at, AttendanceSource source, AttendanceType storedType) {}

    /** What the event's type / ignored flag should be after projection. */
    public record Resolution(UUID id, AttendanceType type, boolean ignored, IgnoreReason ignoreReason) {}

    private SessionProjector() {}

    /** Day-shift projection: every day starts with a CHECK_IN. */
    public static List<Resolution> project(List<Event> events, Duration minGap) {
        return project(events, minGap, AttendanceType.CHECK_IN);
    }

    /**
     * @param initialExpected direction expected for the first event. Day shifts always start a day
     *                        with CHECK_IN; a 12/24h shift that is still open from the previous day
     *                        starts with CHECK_OUT so the session continues across midnight.
     */
    public static List<Resolution> project(List<Event> events, Duration minGap,
                                           AttendanceType initialExpected) {
        // Deterministic order: by time, then by id so equal timestamps never flip between runs.
        List<Event> ordered = events.stream()
                .sorted(Comparator.comparing(Event::at)
                        .thenComparing(e -> e.id().toString()))
                .toList();

        List<Resolution> out = new ArrayList<>(ordered.size());
        AttendanceType expected = initialExpected;
        LocalDateTime lastKeptAt = null;

        for (Event e : ordered) {
            // 1. Admin entries win — never re-typed.
            if (e.source() == AttendanceSource.ADMIN_MANUAL) {
                AttendanceType type = e.storedType() != null ? e.storedType() : expected;
                out.add(new Resolution(e.id(), type, false, null));
                expected = opposite(type);
                lastKeptAt = e.at();
                continue;
            }

            // 2. Scheduler guess survives only while a session is open.
            if (e.source() == AttendanceSource.SCHEDULER_AUTO) {
                if (expected == AttendanceType.CHECK_OUT) {
                    out.add(new Resolution(e.id(), AttendanceType.CHECK_OUT, false, null));
                    expected = AttendanceType.CHECK_IN;
                    lastKeptAt = e.at();
                } else {
                    out.add(new Resolution(e.id(), AttendanceType.CHECK_OUT, true, IgnoreReason.SUPERSEDED));
                }
                continue;
            }

            // 3. Terminal scan too close to the previous kept event → accidental re-scan.
            //    An ignored event keeps whatever type it already had: we make no claim about the
            //    direction of an event we are excluding from the sequence.
            if (lastKeptAt != null && Duration.between(lastKeptAt, e.at()).compareTo(minGap) < 0) {
                AttendanceType unchanged = e.storedType() != null ? e.storedType() : expected;
                out.add(new Resolution(e.id(), unchanged, true, IgnoreReason.RESCAN));
                continue;
            }

            // 4. Normal alternation.
            out.add(new Resolution(e.id(), expected, false, null));
            expected = opposite(expected);
            lastKeptAt = e.at();
        }
        return out;
    }

    private static AttendanceType opposite(AttendanceType type) {
        return type == AttendanceType.CHECK_IN ? AttendanceType.CHECK_OUT : AttendanceType.CHECK_IN;
    }
}
