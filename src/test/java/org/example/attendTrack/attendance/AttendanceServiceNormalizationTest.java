package org.example.attendTrack.attendance;

import org.example.attendTrack.attendance.dto.AttendanceRecord;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.notification.NotificationService;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteCheckpointRepository;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wiring tests for sync + day re-projection.
 * The projection rules themselves are covered exhaustively by {@link SessionProjectorTest}.
 */
class AttendanceServiceNormalizationTest {

    private AttendanceRepository attendanceRepository;
    private AttendanceService service;

    private final UUID siteId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

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
        // No Spring context: wire the self-reference (REQUIRES_NEW proxy) and the @Value threshold.
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "minGapMinutes", 15);

        Site site = mock(Site.class);
        when(site.getId()).thenReturn(siteId);
        when(site.getLat()).thenReturn(43.0);
        when(site.getLng()).thenReturn(23.0);
        when(site.getRadiusMeters()).thenReturn(100);

        User worker = mock(User.class);
        when(worker.getId()).thenReturn(workerId);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(siteCheckpointRepository.findBySiteId(siteId)).thenReturn(List.of());
        when(userRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(siteWorkerRepository.existsBySiteIdAndUserId(siteId, workerId)).thenReturn(true);
        when(attendanceRepository.existsByClientEventId(any())).thenReturn(false);
        when(attendanceRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(false);
        when(attendanceRepository.findForWorkerSiteDay(any(), any(), any(), any())).thenReturn(List.of());
    }

    private AttendanceRecord record(AttendanceType type, LocalDateTime at) {
        return new AttendanceRecord(workerId, type, 43.0, 23.0, true, 0.9, false, at,
                UUID.randomUUID(), false, "dev-test", "1.0.0");
    }

    private AttendanceSyncResponse sync(AttendanceRecord... records) {
        return service.sync(null, new AttendanceSyncRequest(siteId, List.of(records)),
                "203.0.113.7", "JUnit-UA");
    }

    private List<Attendance> capturedSaves() {
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void storesRawEventWithClientTypeAndAuditMetadata() {
        AttendanceSyncResponse res = sync(record(AttendanceType.CHECK_IN, today.atTime(8, 0)));

        assertThat(res.saved()).isEqualTo(1);
        Attendance saved = capturedSaves().get(0);
        // The reported direction is preserved verbatim; `type` is derived later by the projection.
        assertThat(saved.getClientType()).isEqualTo(AttendanceType.CHECK_IN);
        assertThat(saved.isIgnored()).isFalse();
        assertThat(saved.getSource()).isEqualTo(AttendanceSource.TERMINAL_FACE);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getUserAgent()).isEqualTo("JUnit-UA");
        assertThat(saved.getClientDeviceId()).isEqualTo("dev-test");
    }

    @Test
    void duplicateClientEventId_isSkipped() {
        when(attendanceRepository.existsByClientEventId(any())).thenReturn(true);

        AttendanceSyncResponse res = sync(record(AttendanceType.CHECK_IN, today.atTime(8, 0)));

        assertThat(res.saved()).isZero();
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void recordedAtOutOfRange_isSkipped() {
        AttendanceSyncResponse res = sync(record(AttendanceType.CHECK_IN, today.atTime(8, 0).plusDays(10)));

        assertThat(res.saved()).isZero();
        assertThat(res.skipped()).isEqualTo(1);
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void syncTriggersDayReprojection() {
        sync(record(AttendanceType.CHECK_IN, today.atTime(8, 0)));

        // The whole day is reloaded so its direction can be derived from all events.
        verify(attendanceRepository).findForWorkerSiteDay(
                any(), any(), any(), any());
    }

    @Test
    void reprojection_fixesDoubleCheckIn_andIgnoresRescan() {
        // Two check-ins the terminal reported (БИСЕР case) plus a 2-second re-scan.
        Attendance a1 = row(today.atTime(7, 44), AttendanceType.CHECK_IN);
        Attendance a2 = row(today.atTime(17, 2, 34), AttendanceType.CHECK_IN);
        Attendance a3 = row(today.atTime(17, 2, 36), AttendanceType.CHECK_OUT);
        when(attendanceRepository.findForWorkerSiteDay(any(), any(), any(), any()))
                .thenReturn(List.of(a1, a2, a3));

        service.renormalizeDay(workerId, siteId, today);

        assertThat(a1.getType()).isEqualTo(AttendanceType.CHECK_IN);
        assertThat(a1.isIgnored()).isFalse();
        // The duplicate check-in becomes the check-out the worker actually intended.
        assertThat(a2.getType()).isEqualTo(AttendanceType.CHECK_OUT);
        assertThat(a2.isIgnored()).isFalse();
        // The 2-second re-scan is excluded, never deleted.
        assertThat(a3.isIgnored()).isTrue();
        assertThat(a3.getIgnoredReason()).isEqualTo(SessionProjector.IgnoreReason.RESCAN);
    }

    @Test
    void reprojection_isSafeOnEmptyDay() {
        when(attendanceRepository.findForWorkerSiteDay(any(), any(), any(), any())).thenReturn(List.of());
        service.renormalizeDay(workerId, siteId, today); // must not throw
    }

    private Attendance row(LocalDateTime at, AttendanceType type) {
        return Attendance.builder()
                .id(UUID.randomUUID())
                .type(type)
                .clientType(type)
                .source(AttendanceSource.TERMINAL_FACE)
                .recordedAt(at)
                .build();
    }
}
