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
    private static final Duration SAME_DIR_GAP = Duration.ofMinutes(5);
    private static final LocalDate DAY = LocalDate.of(2026, 9, 8);

    private static SessionProjector.Event terminal(String time) {
        return event(time, AttendanceSource.TERMINAL_FACE, null);
    }

    /** A scan whose reported direction matters — the terminal said {@code claimed}. */
    private static SessionProjector.Event claiming(String time, AttendanceType claimed) {
        return at(time, AttendanceSource.TERMINAL_FACE, null, claimed);
    }

    private static SessionProjector.Event event(String time, AttendanceSource src, AttendanceType stored) {
        return at(time, src, stored, null);
    }

    /** Reaches the server the moment it happens — the normal case. */
    private static SessionProjector.Event at(String time, AttendanceSource src,
                                             AttendanceType stored, AttendanceType claimed) {
        LocalDateTime at = time(time);
        return new SessionProjector.Event(UUID.randomUUID(), at, src, stored, claimed, at);
    }

    private static LocalDateTime time(String time) {
        String[] p = time.split(":");
        return DAY.atTime(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                p.length > 2 ? Integer.parseInt(p[2]) : 0);
    }

    /** The same row, reaching the server only at {@code received} — a locked phone held it. */
    private static SessionProjector.Event receivedAt(SessionProjector.Event e, LocalDateTime received) {
        return new SessionProjector.Event(e.id(), e.at(), e.source(), e.storedType(), e.clientType(), received);
    }

    private static List<SessionProjector.Resolution> project(List<SessionProjector.Event> events) {
        return SessionProjector.project(events, GAP, SAME_DIR_GAP);
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
                .map(e -> new SessionProjector.Event(e.id(), e.at(), e.source(), byId.get(e.id()).type(),
                        e.clientType(), e.receivedAt()))
                .toList();
        assertThat(project(reFed)).containsExactlyInAnyOrderElementsOf(first);
    }

    @Test
    void overnightShift_continuesWhenSeededWithCheckOut() {
        // Guard checked in 19:00 yesterday; today's first scan must CLOSE that session,
        // not open a new one.
        var events = List.of(terminal("07:00"));
        var res = SessionProjector.project(events, GAP, SAME_DIR_GAP, AttendanceType.CHECK_OUT);
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
        var res = SessionProjector.project(events, GAP, SAME_DIR_GAP, AttendanceType.CHECK_OUT);
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

    // ── The terminal repeating itself ───────────────────────────────────────────

    @Test
    void sameDirectionTwiceWithinMinutes_isOneButtonPressedTwice_iliyaCase() {
        // ИЛИЯ ПЕТРОВ ДИМОВ, 11.09: checked out 17:01:36, scanned again 17:03:09. The terminal
        // reported CHECK_OUT both times, but 93 seconds cleared the 60-second window, so the second
        // became a check-IN, stayed open, and handed him 1.8 hours an admin had to remove.
        assertThat(render(List.of(
                claiming("07:47", AttendanceType.CHECK_IN),
                claiming("17:01:36", AttendanceType.CHECK_OUT),
                claiming("17:03:09", AttendanceType.CHECK_OUT))))
                .containsExactly("CHECK_IN", "CHECK_OUT", "IGNORED:RESCAN");
    }

    @Test
    void sameDirectionHoursApart_isKept_becauseItIsTheRealCheckOut() {
        // The common shape by far: a terminal working from stale local state reports CHECK_IN again
        // in the evening. Across one production week, 23 of 24 same-direction pairs looked like
        // this. Suppressing them would delete a real check-out and halve everyone's day.
        assertThat(render(List.of(
                claiming("07:47", AttendanceType.CHECK_IN),
                claiming("17:02", AttendanceType.CHECK_IN))))
                .containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void oppositeDirectionAfterTheWindow_opensTheNextSession() {
        // A genuine second session: out at 11:30, back in at 13:00. Nothing here repeats itself.
        assertThat(render(List.of(
                claiming("07:00", AttendanceType.CHECK_IN),
                claiming("11:30", AttendanceType.CHECK_OUT),
                claiming("13:00", AttendanceType.CHECK_IN),
                claiming("17:00", AttendanceType.CHECK_OUT))))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN", "CHECK_OUT");
    }

    @Test
    void aScanWithNoReportedDirection_fallsBackToTheTimeWindowAlone() {
        // Rows predating the client_type column carry null; they must not be suppressed by a
        // comparison that cannot be made.
        assertThat(render(List.of(terminal("17:01:36"), terminal("17:03:09"))))
                .containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void repeatIsMeasuredFromTheLastKEPTScan_notTheLastSuppressedOne() {
        // Three CHECK_OUTs in a row: the second and third are both the same button, and the third
        // must not escape because it is more than a window away from the second (ignored) one.
        assertThat(render(List.of(
                claiming("07:00", AttendanceType.CHECK_IN),
                claiming("17:00", AttendanceType.CHECK_OUT),
                claiming("17:02", AttendanceType.CHECK_OUT),
                claiming("17:04:30", AttendanceType.CHECK_OUT))))
                .containsExactly("CHECK_IN", "CHECK_OUT", "IGNORED:RESCAN", "IGNORED:RESCAN");
    }

    @Test
    void anAdminEntryBetweenScans_clearsTheComparison() {
        // An admin correction is not a button anyone pressed, so the next scan has nothing to
        // repeat — it must be judged on its own.
        assertThat(render(List.of(
                claiming("07:00", AttendanceType.CHECK_IN),
                event("16:00", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                claiming("16:02", AttendanceType.CHECK_OUT))))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN");
    }

    // ── An admin entry standing in for a scan still held on the phone ───────────

    @Test
    void adminCheckInMadeWhileTheScanWasHeld_isSupersededByTheScan_danielCase() {
        // ДАНИЕЛ 06.10: scanned in at 08:32:52, but the locked phone held it until 17:02:59. At 10:02
        // the admin saw no check-in and entered one. Both stayed: two check-ins, and the report
        // showed 08:32 as an open shift and paid him from 10:02.
        var admin = receivedAt(event("10:02", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_IN),
                time("10:02:49"));
        assertThat(render(List.of(
                receivedAt(terminal("08:32:52"), time("17:02:59")),
                admin,
                receivedAt(terminal("17:03:36"), time("17:27:16")))))
                .containsExactly("CHECK_IN", "IGNORED:SUPERSEDED", "CHECK_OUT");
    }

    @Test
    void adminCheckOutMadeWhileTheScanWasHeld_isSupersededByTheScan_venelinCase() {
        // ВЕНЕЛИН 17.09: scanned out at 18:09:30, held until 08:09 the next morning. At 19:52 the
        // admin closed his day at 16:52; the scan then arrived and became an open check-in.
        var admin = receivedAt(event("16:52", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                time("19:52"));
        assertThat(render(List.of(
                terminal("07:58:48"),
                admin,
                receivedAt(terminal("18:09:30"), DAY.plusDays(1).atTime(8, 9, 45)))))
                .containsExactly("CHECK_IN", "IGNORED:SUPERSEDED", "CHECK_OUT");
    }

    @Test
    void adminCheckOutAfterAHeldCheckOut_isSuperseded_insteadOfLeftAsAnOrphan() {
        // The held check-out lands BEFORE the admin's: the day already reads correctly, and the
        // entry is a second check-out the report would drop anyway. It is the same stand-in.
        var admin = receivedAt(event("17:07", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                time("19:53"));
        assertThat(render(List.of(
                terminal("07:00"),
                receivedAt(terminal("17:03:22"), DAY.plusDays(1).atTime(8, 0)),
                admin)))
                .containsExactly("CHECK_IN", "CHECK_OUT", "IGNORED:SUPERSEDED");
    }

    @Test
    void adminEntryMadeAfterTheScanArrived_staysAuthoritative() {
        // The admin could see the scan and entered a check-out anyway: a deliberate decision.
        var admin = receivedAt(event("17:30", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                time("17:35"));
        assertThat(render(List.of(
                terminal("07:00"),
                receivedAt(terminal("17:03"), time("17:03:10")),
                admin)))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_OUT");
    }

    @Test
    void aScanTakenAfterTheAdminEntry_isANewEvent_evenIfItArrivedLate() {
        // Closed by the admin at 17:00 (entered 17:05); the worker came back and scanned at 18:00.
        // That scan did not exist when the admin acted, so it opens the next session.
        var admin = receivedAt(event("17:00", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                time("17:05"));
        assertThat(render(List.of(
                terminal("07:00"),
                admin,
                receivedAt(terminal("18:00"), DAY.plusDays(1).atTime(8, 0)))))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN");
    }

    @Test
    void aHeldScanOfTheOtherDirection_leavesTheAdminEntryAlone() {
        // The held scan is the check-in, the admin entered the check-out: two different events.
        var admin = receivedAt(event("12:00", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_OUT),
                time("12:05"));
        assertThat(render(List.of(
                receivedAt(terminal("07:55"), time("17:00")),
                admin)))
                .containsExactly("CHECK_IN", "CHECK_OUT");
    }

    @Test
    void aKeptEventBetweenThem_meansTwoSessions_notAStandIn() {
        // Held check-in 08:30, a check-out at 12:00 that arrived on time, admin check-in at 13:00:
        // the admin opened the afternoon session, not the morning one.
        var admin = receivedAt(event("13:00", AttendanceSource.ADMIN_MANUAL, AttendanceType.CHECK_IN),
                time("13:01"));
        assertThat(render(List.of(
                receivedAt(terminal("08:30"), time("17:00")),
                terminal("12:00"),
                admin)))
                .containsExactly("CHECK_IN", "CHECK_OUT", "CHECK_IN");
    }
}
