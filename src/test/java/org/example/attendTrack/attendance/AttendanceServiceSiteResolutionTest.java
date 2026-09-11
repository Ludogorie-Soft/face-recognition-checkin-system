package org.example.attendTrack.attendance;

import org.example.attendTrack.attendance.dto.AttendanceRecord;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.notification.NotificationService;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteCheckpoint;
import org.example.attendTrack.site.SiteCheckpointRepository;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorker;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.ShiftType;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The terminal's site is a claim, not a fact — these tests pin the server's re-resolution of it.
 *
 * <p>Fixtures are the real sites and the real coordinates from 8–10 September 2026, when every scan
 * whose GPS fix missed the cached zones was filed at whichever site happened to be first in the
 * worker's assignment list. Six of ВЕНЕЛИН's scans landed 76 km away; a crew of four landed 163 km
 * away. The operators had to fix each one by hand.
 */
class AttendanceServiceSiteResolutionTest {

    private AttendanceRepository attendanceRepository;
    private AttendanceService service;

    private final UUID workerId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    /** Where the crew actually stood: three 10 m corridors along ул. "Ангел Кънчев", Враца. */
    private Site angelKanchev;
    /** 76 km away — the worker's first assignment, and therefore the terminal's blind guess. */
    private Site obshtinskiPat;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        SiteRepository siteRepository = mock(SiteRepository.class);
        SiteWorkerRepository siteWorkerRepository = mock(SiteWorkerRepository.class);
        SiteCheckpointRepository siteCheckpointRepository = mock(SiteCheckpointRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        service = new AttendanceService(attendanceRepository, siteRepository, siteWorkerRepository,
                siteCheckpointRepository, userRepository, notificationService);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "minGapSeconds", 60);
        ReflectionTestUtils.setField(service, "geoAccuracyCapMeters", 50.0);

        angelKanchev = site("Основен ремонт на ул. \"Ангел Кънчев\" гр. Враца",
                43.212620113478, 23.545242543418524, 10);
        obshtinskiPat = site("ОБЩИНСКИ ПЪТ VRC 1128", 43.68953086779656, 24.276040792465214, 10);

        User worker = mock(User.class);
        when(worker.getId()).thenReturn(workerId);
        when(worker.getShiftType()).thenReturn(ShiftType.DAY);

        when(siteRepository.findById(any())).thenAnswer(inv ->
                Optional.of(inv.getArgument(0).equals(angelKanchev.getId()) ? angelKanchev : obshtinskiPat));
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(siteWorkerRepository.existsBySiteIdAndUserId(any(), any())).thenReturn(true);
        // Built outside when(...) — the SiteWorker constructor calls worker.getId(), and Mockito
        // treats a mock invocation inside an in-progress stubbing as an unfinished one.
        List<SiteWorker> assignments = List.of(
                new SiteWorker(obshtinskiPat, worker),   // first assignment — the old blind fallback
                new SiteWorker(angelKanchev, worker));
        when(siteWorkerRepository.findByUserIdWithSite(workerId)).thenReturn(assignments);

        when(siteCheckpointRepository.findBySiteId(angelKanchev.getId()))
                .thenReturn(corridorsOf(angelKanchev));
        when(siteCheckpointRepository.findBySiteId(obshtinskiPat.getId())).thenReturn(List.of());

        when(attendanceRepository.existsByClientEventId(any())).thenReturn(false);
        when(attendanceRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(false);
        when(attendanceRepository.findForWorkerDay(any(), any(), any())).thenReturn(List.of());
    }

    // ── The incident ─────────────────────────────────────────────────────────────

    @Test
    void filesTheScanWhereTheCoordinatesSay_notWhereTheTerminalClaimed() {
        // ВЕНЕЛИН КРЪСТЕВ ДИМИТРОВ, 10 Sep 17:02 — standing on Ангел Кънчев, filed at ОБЩИНСКИ ПЪТ.
        AttendanceSyncResponse res = syncClaiming(obshtinskiPat, 43.2093897, 23.5465668);

        assertThat(res.saved()).isEqualTo(1);
        Attendance saved = firstSave();
        assertThat(saved.getSite().getName()).isEqualTo(angelKanchev.getName());
        assertThat(saved.isLocationValid()).isTrue();
        assertThat(saved.getDistanceMeters()).isZero();
    }

    @Test
    void keepsTheTerminalsClaimForAudit() {
        syncClaiming(obshtinskiPat, 43.2093897, 23.5465668);

        Attendance saved = firstSave();
        assertThat(saved.getClientSite().getName()).isEqualTo(obshtinskiPat.getName());
        assertThat(saved.getSite().getName()).isEqualTo(angelKanchev.getName());
    }

    @Test
    void leavesACorrectClaimAlone() {
        syncClaiming(angelKanchev, 43.2093897, 23.5465668);

        Attendance saved = firstSave();
        assertThat(saved.getSite().getName()).isEqualTo(angelKanchev.getName());
        assertThat(saved.isLocationValid()).isTrue();
    }

    @Test
    void outsideEveryZone_picksTheNearestSiteAndRecordsHowFarOut() {
        // The end-of-day scan made in the van: ~1 km north of the corridors, 70+ km from the other site.
        syncClaiming(obshtinskiPat, 43.2200, 23.5500);

        Attendance saved = firstSave();
        assertThat(saved.getSite().getName()).isEqualTo(angelKanchev.getName());
        assertThat(saved.isLocationValid()).isFalse();
        assertThat(saved.getDistanceMeters()).isBetween(500, 2_000);
    }

    // ── GPS accuracy allowance ───────────────────────────────────────────────────

    @Test
    void aFixJustOutsideATenMetreCorridorCountsAsInsideWhenItsOwnErrorSaysItMight() {
        // 25 m beyond the corridor with a ±30 m fix: the device cannot tell the difference, and
        // neither should we. Without the allowance this is the coin toss that produced most of the
        // spurious "outside zone" flags.
        double lat = 43.2090898, lng = 23.5468737;   // 25 m beyond the nearest corridor

        assertThat(syncClaiming(angelKanchev, lat, lng, null).saved()).isEqualTo(1);
        assertThat(firstSave().isLocationValid()).isFalse();

        setUp();
        syncClaiming(angelKanchev, lat, lng, 30.0);
        assertThat(firstSave().isLocationValid()).isTrue();
    }

    @Test
    void anAbsurdAccuracyReadingIsCappedAndCannotValidateAnything() {
        // 1 km out claiming ±5 km accuracy. The cap is 50 m, so this stays outside.
        syncClaiming(angelKanchev, 43.2200, 23.5500, 5_000.0);

        assertThat(firstSave().isLocationValid()).isFalse();
    }

    @Test
    void storesTheReportedAccuracyForAudit() {
        syncClaiming(angelKanchev, 43.2093897, 23.5465668, 12.5);

        assertThat(firstSave().getAccuracyMeters()).isEqualTo(12.5);
    }

    // ── Degenerate input ─────────────────────────────────────────────────────────

    @Test
    void noGpsFix_keepsTheClaimedSiteRatherThanGuessingFromZeroZero() {
        // useGeoLocation reports 0/0 until the first fix arrives. Treating that as a position would
        // resolve every scan against a point in the Gulf of Guinea.
        syncClaiming(obshtinskiPat, 0.0, 0.0);

        Attendance saved = firstSave();
        assertThat(saved.getSite().getName()).isEqualTo(obshtinskiPat.getName());
        assertThat(saved.isLocationValid()).isFalse();
        assertThat(saved.getDistanceMeters()).isNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private AttendanceSyncResponse syncClaiming(Site claimed, double lat, double lng) {
        return syncClaiming(claimed, lat, lng, null);
    }

    private AttendanceSyncResponse syncClaiming(Site claimed, double lat, double lng, Double accuracyMeters) {
        AttendanceRecord record = new AttendanceRecord(
                workerId, AttendanceType.CHECK_IN, lat, lng, accuracyMeters, true, 0.9, false,
                today.atTime(17, 2), UUID.randomUUID(), false, "dev-vratsa", "1.0.0");
        return service.sync(null, new AttendanceSyncRequest(claimed.getId(), List.of(record)),
                "149.62.206.4", "JUnit-UA");
    }

    private Attendance firstSave() {
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Site site(String name, double lat, double lng, int radius) {
        return Site.builder().id(UUID.randomUUID()).name(name)
                .lat(lat).lng(lng).radiusMeters(radius).build();
    }

    private static List<SiteCheckpoint> corridorsOf(Site site) {
        return List.of(
                corridor(site, 43.212620113478, 23.545242543418524, 43.21086874555744, 23.546608686447147),
                corridor(site, 43.210822, 23.546630, 43.210016404122015, 23.546533566531618),
                corridor(site, 43.210172, 23.546569, 43.209089762534035, 23.546442377446134));
    }

    private static SiteCheckpoint corridor(Site site, double lat, double lng, double lat2, double lng2) {
        return SiteCheckpoint.builder()
                .id(UUID.randomUUID()).site(site)
                .lat(lat).lng(lng).lat2(lat2).lng2(lng2)
                .checkpointType(SiteCheckpoint.CheckpointType.LINE)
                .radiusMeters(10)
                .build();
    }
}
