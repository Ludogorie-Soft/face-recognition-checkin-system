package org.example.attendTrack.attendance;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Exhaustive tests for the pure session projection — the core of server-side normalization. */
class SessionProjectorTest {

    private static final Duration GAP = Duration.ofSeconds(60);
    private static final LocalDate DAY = LocalDate.of(2026, 9, 8);

    private static SessionProjector.Event terminal(String time) {
        return event(time, AttendanceSource.TERMINAL_FACE, null);
    }

    private static SessionProjector.Event event(String time, AttendanceSource src, AttendanceType stored) {
        String[] p = time.split(":");
        LocalDateTime at = DAY.atTime(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                p.length > 2 ? Integer.parseInt(p[2]) : 0);
        return new SessionProjector.Event(UUID.randomUUID(), at, src, stored);
    }

    private static List<SessionProjector.Resolution> project(List<SessionProjector.Event> events) {
        return SessionProjector.project(events, GAP);
    }

    /**
     * Resolutions in chronological order. Kept events render as their type; ignored events render
     * only as "IGNORED:reason" — the type of an excluded event carries no meaning.
     */
    private static List<String> render(List<SessionProjector.Event> events) {
        Map<UUID, LocalDateTime> times = events.stream()
                .collect(Collectors.toMap(SessionProjector.Event::id, SessionProjector.Event::at));
        return project(events).stream()
                .sorted((a, b) -> times.get(a.id()).compareTo(times.get(b.id())))
                .map(r -> r.ignored() ? "IGNORED:" + r.ignoreReason() : r.type().toString())
                .toList();
    }

    @Test
    void normalDay_alternates() {
        assertThat(render(List.of(terminal("07:44"), terminal("17:02"))))
                .containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void secondCheckIn_becomesCheckOut_biserCase() {
        // БИСЕР 08.09: 07:44 in, 17:02:34 (terminal sent CHECK_IN), 17:02:36 (2s later)
        var events = List.of(terminal("07:44:33"), terminal("17:02:34"), terminal("17:02:36"));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "CHECK_OUT", "IGNORED:RESCAN");
    }

    @Test
    void checkOutAsFirstEventOfDay_becomesCheckIn_danielCase() {
        // ДАНИЕЛ 02.09: orphan check-out 07:31:54, then 07:31:55, then 17:02
        var events = List.of(terminal("07:31:54"), terminal("07:31:55"), terminal("17:02:23"));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "IGNORED:RESCAN", "CHECK_OUT");
    }

    @Test
    void shortButRealShift_isPreserved() {
        // Checked in 08:00, out 08:10. Ten minutes is real data — it must NOT be swallowed as a
        // re-scan, otherwise the shift stays open and the day gets credited to the auto-checkout.
        var events = List.of(terminal("08:00"), terminal("08:10"));
        assertThat(render(events)).containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void twoLegitimateSessionsInOneDay_areKept() {
        var events = List.of(terminal("08:00"), terminal("12:00"), terminal("13:00"), terminal("17:00"));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN", "CHECK_OUT");
    }

    @Test
    void schedulerAuto_keptWhenSessionIsOpen() {
        var events = List.of(terminal("08:00"), event("18:00", AttendanceSource.SCHEDULER_AUTO, AttendanceType.CHECK_OUT));
        assertThat(render(events)).containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void schedulerAuto_supersededByRealCheckOut_andreyCase() {
        // АНДРЕЙ: real 17:10 check-out arrives late, scheduler had already closed at 18:00
        var events = List.of(
                terminal("08:55"),
                terminal("17:10"),
                event("18:00", AttendanceSource.SCHEDULER_AUTO, AttendanceType.CHECK_OUT));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "CHECK_OUT", "IGNORED:SUPERSEDED");
    }

    @Test
    void adminManual_isAuthoritative_andResetsDirection() {
        var events = List.of(
                terminal("08:00"),
                event("12:00", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                terminal("13:00"));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN");
    }

    @Test
    void lateOfflineEvent_healsTheDay() {
        // Terminal A (online) recorded 12:00 while terminal B's 08:00 was still queued offline.
        // Once B syncs, the whole day is re-projected and becomes correct.
        var events = List.of(terminal("12:00"), terminal("08:00"));
        assertThat(render(events)).containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void resultIsIndependentOfArrivalOrder() {
        var events = new ArrayList<>(List.of(
                terminal("07:44"), terminal("09:00"), terminal("12:30"),
                terminal("13:00"), terminal("17:02"), terminal("17:02:30")));
        Map<UUID, String> baseline = asMap(events);
        for (int i = 0; i < 25; i++) {
            Collections.shuffle(events);
            assertThat(asMap(events))
                    .as("shuffle #" + i)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void projectionIsIdempotent() {
        var events = List.of(terminal("08:00"), terminal("08:05"), terminal("17:00"));
        var first = project(events);
        // Feed the resolved types back in as if they had been persisted, then re-project.
        Map<UUID, SessionProjector.Resolution> byId = first.stream()
                .collect(Collectors.toMap(SessionProjector.Resolution::id, Function.identity()));
        var reFed = events.stream()
                .map(e -> new SessionProjector.Event(e.id(), e.at(), e.source(), byId.get(e.id()).type()))
                .toList();
        assertThat(project(reFed)).containsExactlyInAnyOrderElementsOf(first);
    }

    @Test
    void overnightShift_continuesWhenSeededWithCheckOut() {
        // Guard checked in 19:00 yesterday; today's first scan must CLOSE that session,
        // not open a new one.
        var events = List.of(terminal("07:00"));
        var res = SessionProjector.project(events, GAP, AttendanceType.CHECK_OUT);
        assertThat(res).singleElement()
                .satisfies(r -> {
                    assertThat(r.type()).isEqualTo(AttendanceType.CHECK_OUT);
                    assertThat(r.ignored()).isFalse();
                });
    }

    @Test
    void overnightShift_seededDayThenAlternatesNormally() {
        // 07:00 closes yesterday's shift, 19:00 opens tonight's.
        var events = List.of(terminal("07:00"), terminal("19:00"));
        var res = SessionProjector.project(events, GAP, AttendanceType.CHECK_OUT);
        assertThat(res.stream().map(SessionProjector.Resolution::type))
                .containsExactly(AttendanceType.CHECK_OUT, AttendanceType.CHECK_IN);
    }

    @Test
    void emptyInput_yieldsEmptyOutput() {
        assertThat(project(List.of())).isEmpty();
    }

    @Test
    void burstOfScans_keepsOnlyTheFirst() {
        var events = List.of(terminal("08:00:00"), terminal("08:00:05"), terminal("08:00:10"), terminal("08:00:15"));
        assertThat(render(events))
                .containsExactly("CHECK_IN", "IGNORED:RESCAN", "IGNORED:RESCAN", "IGNORED:RESCAN");
    }

    private static Map<UUID, String> asMap(List<SessionProjector.Event> events) {
        return project(events).stream().collect(Collectors.toMap(
                SessionProjector.Resolution::id,
                r -> r.ignored() ? "IGNORED:" + r.ignoreReason() : r.type().toString()));
    }
}
