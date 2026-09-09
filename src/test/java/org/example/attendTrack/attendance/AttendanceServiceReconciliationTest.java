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

/** Unit tests for the check-in/out state-machine reconciliation in {@link AttendanceService#sync}. */
class AttendanceServiceReconciliationTest {

    private AttendanceRepository attendanceRepository;
    private AttendanceService service;

    private final UUID siteId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();

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
        // No Spring context here, so the @Autowired self-reference (used for REQUIRES_NEW
        // per-record transactions) must be wired manually to the same instance.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);

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
        // No prior DB history and no dedup hits unless a test overrides.
        when(attendanceRepository.existsByClientEventId(any())).thenReturn(false);
        when(attendanceRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(false);
        when(attendanceRepository.findLastForWorkerOnDay(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private AttendanceRecord record(AttendanceType type, LocalDateTime at) {
        // lat/lng equal to the site → within radius → locationValid, no notification.
        return new AttendanceRecord(workerId, type, 43.0, 23.0, true, 0.9, false, at,
                UUID.randomUUID(), false, "dev-test", "1.0.0");
    }

    private List<Attendance> capturedSaves() {
        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void secondCheckInSameDay_isFlaggedDuplicate() {
        LocalDateTime day = LocalDate.now().atTime(7, 31);
        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_IN, day),
                record(AttendanceType.CHECK_IN, day.withHour(17).withMinute(2))
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.saved()).isEqualTo(2);
        assertThat(res.anomalies()).isEqualTo(1);

        List<Attendance> saved = capturedSaves();
        assertThat(saved.get(0).isAnomaly()).isFalse();
        assertThat(saved.get(1).isAnomaly()).isTrue();
        assertThat(saved.get(1).getAnomalyReason()).isEqualTo(AnomalyReason.DUPLICATE_CHECK_IN);
    }

    @Test
    void checkOutWithoutCheckIn_isFlagged() {
        LocalDateTime at = LocalDate.now().atTime(17, 6);
        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_OUT, at)
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.anomalies()).isEqualTo(1);
        assertThat(capturedSaves().get(0).getAnomalyReason())
                .isEqualTo(AnomalyReason.CHECKOUT_WITHOUT_CHECKIN);
    }

    @Test
    void normalInThenOut_hasNoAnomaly() {
        LocalDateTime day = LocalDate.now().atTime(8, 0);
        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_IN, day),
                record(AttendanceType.CHECK_OUT, day.withHour(17))
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.saved()).isEqualTo(2);
        assertThat(res.anomalies()).isZero();
        assertThat(capturedSaves()).allSatisfy(a -> {
            assertThat(a.isAnomaly()).isFalse();
            // Audit metadata: face-path source + server-captured IP + client-sent device id.
            assertThat(a.getSource()).isEqualTo(AttendanceSource.TERMINAL_FACE);
            assertThat(a.getIpAddress()).isEqualTo("203.0.113.7");
            assertThat(a.getUserAgent()).isEqualTo("JUnit-UA");
            assertThat(a.getClientDeviceId()).isEqualTo("dev-test");
            assertThat(a.getAppVersion()).isEqualTo("1.0.0");
        });
    }

    @Test
    void outOfOrderBatch_isSortedBeforeReconciliation() {
        // CHECK_OUT arrives first in the list but happened later — must not be flagged.
        LocalDateTime day = LocalDate.now().atTime(8, 0);
        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_OUT, day.withHour(17)),
                record(AttendanceType.CHECK_IN, day)
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.anomalies()).isZero();
    }

    @Test
    void duplicateClientEventId_isSkipped() {
        LocalDateTime at = LocalDate.now().atTime(8, 0);
        when(attendanceRepository.existsByClientEventId(any())).thenReturn(true);

        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_IN, at)
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.saved()).isZero();
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void checkInWhenAlreadyOpenFromEarlierBatch_isFlagged() {
        // Real БИСЕР scenario: a CHECK_IN was persisted earlier today in a SEPARATE sync batch,
        // so the state must be seeded from the DB, not just the in-batch map.
        Attendance existing = mock(Attendance.class);
        when(existing.getType()).thenReturn(AttendanceType.CHECK_IN);
        when(attendanceRepository.findLastForWorkerOnDay(any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));

        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_IN, LocalDate.now().atTime(17, 2))
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.anomalies()).isEqualTo(1);
        assertThat(capturedSaves().get(0).getAnomalyReason()).isEqualTo(AnomalyReason.DUPLICATE_CHECK_IN);
    }

    @Test
    void recordedAtOutOfRange_isSkipped() {
        // 10 days in the future → outside the accepted window → rejected, not stored.
        AttendanceSyncResponse res = service.sync(null, new AttendanceSyncRequest(siteId, List.of(
                record(AttendanceType.CHECK_IN, LocalDate.now().atTime(8, 0).plusDays(10))
        )), "203.0.113.7", "JUnit-UA");

        assertThat(res.saved()).isZero();
        assertThat(res.skipped()).isEqualTo(1);
        verify(attendanceRepository, never()).save(any());
    }
}
