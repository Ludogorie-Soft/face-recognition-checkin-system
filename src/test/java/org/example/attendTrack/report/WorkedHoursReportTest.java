package org.example.attendTrack.report;

import org.example.attendTrack.attendance.Attendance;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.attendance.AttendanceSource;
import org.example.attendTrack.attendance.AttendanceType;
import org.example.attendTrack.company.CompanyRepository;
import org.example.attendTrack.report.dto.WorkedHoursRow;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.ShiftType;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Worked-hours pairing: the layer that turns a day's scans into sessions and hours.
 *
 * <p>Sessions are paired per <b>worker and day</b>, not per site, and each is filed under the site
 * its check-in happened at. Before that, a worker who checked in at one site and scanned at another
 * got two half-sessions instead of one shift — on 10 September that turned НЕЛИ's eight-hour day
 * into 0.05 h + 0.48 h, and gave four colleagues 9.1 h where 8.7 h was worked.
 */
class WorkedHoursReportTest {

    private AttendanceRepository attendanceRepository;
    private HoursCorrectionRepository hoursCorrectionRepository;
    private ReportService service;

    private User worker;
    private Site siteA;   // where the day starts
    private Site siteB;   // where the crew ends up in the evening

    private final LocalDate day = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        SiteRepository siteRepository = mock(SiteRepository.class);
        SiteWorkerRepository siteWorkerRepository = mock(SiteWorkerRepository.class);
        hoursCorrectionRepository = mock(HoursCorrectionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CompanyRepository companyRepository = mock(CompanyRepository.class);

        service = new ReportService(attendanceRepository, siteRepository, siteWorkerRepository,
                hoursCorrectionRepository, userRepository, companyRepository);

        siteA = Site.builder().id(UUID.randomUUID()).name("Ангел Кънчев")
                .lat(43.21).lng(23.54).radiusMeters(10).build();
        siteB = Site.builder().id(UUID.randomUUID()).name("ОБЩИНСКИ ПЪТ")
                .lat(43.68).lng(24.27).radiusMeters(10).build();

        worker = mock(User.class);
        when(worker.getId()).thenReturn(UUID.randomUUID());
        when(worker.getName()).thenReturn("НЕЛИ ЦОЛОВА ЦЕНОВА");
        when(worker.getShiftType()).thenReturn(ShiftType.DAY);

        when(siteRepository.existsById(any())).thenReturn(true);
        when(companyRepository.findWorkerCompanyPairs(anyList())).thenReturn(List.of());
        when(hoursCorrectionRepository.findBySiteAndPeriod(any(), any(), any())).thenReturn(List.of());
        when(hoursCorrectionRepository.findAllInPeriod(any(), any())).thenReturn(List.of());
    }

    // ── One session, two sites ───────────────────────────────────────────────────

    @Test
    void aDaySpanningTwoSites_isOneSessionFiledWhereItOpened() {
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        assertThat(rows).hasSize(1);
        WorkedHoursRow session = rows.get(0);
        assertThat(session.siteName()).isEqualTo("Ангел Кънчев");
        assertThat(session.checkIn()).isEqualTo(LocalTime.of(7, 47));
        assertThat(session.checkOut()).isEqualTo(LocalTime.of(17, 2));
        assertThat(session.calculatedHours()).isEqualTo(9.25);
        assertThat(session.effectiveHours()).isEqualTo(9.25);
    }

    @Test
    void theSiteReportShowsAShiftThatStartedThere_evenWhenItEndedElsewhere() {
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT));

        List<WorkedHoursRow> rows = service.getWorkedHours(siteA.getId(), null, day, day);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).calculatedHours()).isEqualTo(9.25);
    }

    @Test
    void theSiteReportDoesNotShowAShiftThatMerelyEndedThere() {
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT));

        // Site B saw only the closing scan. Counting it here would bill the same shift twice —
        // which is exactly what the old per-site pairing did, as a phantom half-hour session.
        assertThat(service.getWorkedHours(siteB.getId(), null, day, day)).isEmpty();
    }

    @Test
    void theSchedulersAutoCheckoutIsStillMarkedAsSuch() {
        Attendance auto = scan(siteA, day.atTime(18, 0), AttendanceType.CHECK_OUT);
        auto = rebuild(auto, AttendanceSource.SCHEDULER_AUTO);
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN), auto);

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).autoCheckout()).isTrue();
    }

    // ── Genuinely two sessions ───────────────────────────────────────────────────

    @Test
    void twoRealSessionsInADay_giveTwoRowsPlusADayTotal() {
        given(scan(siteA, day.atTime(7, 0), AttendanceType.CHECK_IN),
              scan(siteA, day.atTime(11, 0), AttendanceType.CHECK_OUT),
              scan(siteB, day.atTime(13, 0), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 0), AttendanceType.CHECK_OUT));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        assertThat(rows).hasSize(3);
        List<WorkedHoursRow> sessions = rows.stream().filter(r -> r.pairIndex() >= 0).toList();
        assertThat(sessions).extracting(WorkedHoursRow::siteName)
                .containsExactly("Ангел Кънчев", "ОБЩИНСКИ ПЪТ");
        // Session rows carry no effectiveHours, so a summary cannot double-count them.
        assertThat(sessions).allMatch(r -> r.effectiveHours() == null);

        WorkedHoursRow total = rows.stream().filter(r -> r.pairIndex() == -1).findFirst().orElseThrow();
        assertThat(total.calculatedHours()).isEqualTo(8.0);
        assertThat(total.effectiveHours()).isEqualTo(8.0);
        // The day is filed under the site it opened at.
        assertThat(total.siteName()).isEqualTo("Ангел Кънчев");
    }

    @Test
    void anUnclosedShiftHasNoCheckOutAndNoHours() {
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).checkOut()).isNull();
        assertThat(rows.get(0).calculatedHours()).isNull();
        assertThat(rows.get(0).effectiveHours()).isNull();
    }

    // ── Corrections ──────────────────────────────────────────────────────────────

    @Test
    void aCorrectionOverridesTheCalculatedHours() {
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT));
        when(hoursCorrectionRepository.findAllInPeriod(any(), any()))
                .thenReturn(List.of(correction(siteA, 8.0, "по график")));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        assertThat(rows.get(0).calculatedHours()).isEqualTo(9.25);
        assertThat(rows.get(0).effectiveHours()).isEqualTo(8.0);
        assertThat(rows.get(0).correctionNote()).isEqualTo("по график");
    }

    @Test
    void aCorrectionSavedAgainstTheOtherSiteOfTheSameDayIsStillFound() {
        // Corrections are stored per (worker, site, day) while a day now spans sites. One entered
        // before this change may sit under the site the shift ended at.
        given(scan(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN),
              scan(siteA, day.atTime(11, 0), AttendanceType.CHECK_OUT),
              scan(siteB, day.atTime(13, 0), AttendanceType.CHECK_IN),
              scan(siteB, day.atTime(17, 0), AttendanceType.CHECK_OUT));
        when(hoursCorrectionRepository.findAllInPeriod(any(), any()))
                .thenReturn(List.of(correction(siteB, 6.5, "коригирано")));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day);

        WorkedHoursRow total = rows.stream().filter(r -> r.pairIndex() == -1).findFirst().orElseThrow();
        assertThat(total.effectiveHours()).isEqualTo(6.5);
        assertThat(total.correctionNote()).isEqualTo("коригирано");
    }

    // ── Guards ───────────────────────────────────────────────────────────────────

    @Test
    void aGuardShiftCrossingMidnightAndSitesStaysOneSessionDatedByItsCheckIn() {
        when(worker.getShiftType()).thenReturn(ShiftType.SHIFT_24H);
        given(scan(siteA, day.atTime(19, 0), AttendanceType.CHECK_IN),
              scan(siteB, day.plusDays(1).atTime(7, 0), AttendanceType.CHECK_OUT));

        List<WorkedHoursRow> rows = service.getWorkedHours(null, null, day, day.plusDays(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).date()).isEqualTo(day);
        assertThat(rows.get(0).siteName()).isEqualTo("Ангел Кънчев");
        assertThat(rows.get(0).calculatedHours()).isEqualTo(12.0);
        assertThat(rows.get(0).shiftType()).isEqualTo("SHIFT_24H");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Feeds the same records to both the all-sites and the per-site query paths. */
    private void given(Attendance... records) {
        when(attendanceRepository.findAllInDateRange(any(), any())).thenReturn(List.of(records));
        when(attendanceRepository.findForSiteWorkersInRange(any(), any(), any()))
                .thenReturn(List.of(records));
    }

    private Attendance scan(Site site, LocalDateTime at, AttendanceType type) {
        return Attendance.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .site(site)
                .clientSite(site)
                .type(type)
                .clientType(type)
                .source(AttendanceSource.TERMINAL_FACE)
                .lat(43.21).lng(23.54)
                .locationValid(true)
                .recordedAt(at)
                .syncedAt(at.plusSeconds(20))
                .build();
    }

    private Attendance rebuild(Attendance a, AttendanceSource source) {
        return Attendance.builder()
                .id(a.getId()).worker(a.getWorker()).site(a.getSite()).clientSite(a.getClientSite())
                .type(a.getType()).clientType(a.getClientType())
                .source(source).manualOverride(true)
                .lat(a.getLat()).lng(a.getLng()).locationValid(a.isLocationValid())
                .recordedAt(a.getRecordedAt()).syncedAt(a.getSyncedAt())
                .build();
    }

    private HoursCorrection correction(Site site, double hours, String note) {
        return HoursCorrection.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .site(site)
                .date(day)
                .correctedHours(hours)
                .note(note)
                .build();
    }
}
