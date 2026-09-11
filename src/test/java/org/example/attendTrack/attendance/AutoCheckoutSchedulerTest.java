package org.example.attendTrack.attendance;

import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The nightly auto-checkout, which invents the hours nobody scanned — so what it declines to create
 * matters as much as what it creates.
 */
class AutoCheckoutSchedulerTest {

    private AttendanceRepository attendanceRepository;
    private AutoCheckoutScheduler scheduler;

    private final UUID workerId = UUID.randomUUID();
    private final LocalDate day = LocalDate.of(2026, 9, 10);

    private Site siteA;
    private Site siteB;
    private User worker;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);
        scheduler = new AutoCheckoutScheduler(attendanceRepository, attendanceService);

        siteA = Site.builder().id(UUID.randomUUID()).name("Ангел Кънчев")
                .lat(43.21).lng(23.54).radiusMeters(10).workEndTime(LocalTime.of(18, 0)).build();
        siteB = Site.builder().id(UUID.randomUUID()).name("ОБЩИНСКИ ПЪТ")
                .lat(43.68).lng(24.27).radiusMeters(10).workEndTime(LocalTime.of(18, 0)).build();

        worker = mock(User.class);
        when(worker.getId()).thenReturn(workerId);

        when(attendanceRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(false);
    }

    @Test
    void twoOpenCheckInsAtDifferentSites_produceOneAutoCheckout() {
        // A worker has one session at a time. Two open check-ins on the same day are the same
        // logical session seen from two terminals — closing both invents a second working day.
        when(attendanceRepository.findUnclosedCheckIns(any(), any(), any()))
                .thenReturn(List.of(
                        checkIn(siteA, day.atTime(7, 47)),
                        checkIn(siteB, day.atTime(17, 2))));

        List<AttendanceService.DayKey> touched = scheduler.insertMissingCheckouts(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now());

        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository, org.mockito.Mockito.times(1)).save(saved.capture());
        assertThat(touched).hasSize(1);

        // The later check-in wins: it is the one still open at the end of the day.
        assertThat(saved.getValue().getSite().getName()).isEqualTo(siteB.getName());
        assertThat(saved.getValue().getSource()).isEqualTo(AttendanceSource.SCHEDULER_AUTO);
        assertThat(saved.getValue().getRecordedAt()).isEqualTo(day.atTime(18, 0));
    }

    @Test
    void anOpenCheckInStillGetsItsCheckout() {
        when(attendanceRepository.findUnclosedCheckIns(any(), any(), any()))
                .thenReturn(List.of(checkIn(siteA, day.atTime(7, 47))));

        List<AttendanceService.DayKey> touched = scheduler.insertMissingCheckouts(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now());

        assertThat(touched).containsExactly(new AttendanceService.DayKey(workerId, day));
        verify(attendanceRepository).save(any());
    }

    @Test
    void nothingOpen_writesNothing() {
        when(attendanceRepository.findUnclosedCheckIns(any(), any(), any())).thenReturn(List.of());

        assertThat(scheduler.insertMissingCheckouts(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now())).isEmpty();
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void anExistingAutoCheckoutIsNotCreatedTwice() {
        when(attendanceRepository.findUnclosedCheckIns(any(), any(), any()))
                .thenReturn(List.of(checkIn(siteA, day.atTime(7, 47))));
        when(attendanceRepository.existsDuplicate(any(), any(), any(), any())).thenReturn(true);

        assertThat(scheduler.insertMissingCheckouts(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now())).isEmpty();
        verify(attendanceRepository, never()).save(any());
    }

    private Attendance checkIn(Site site, LocalDateTime at) {
        return Attendance.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .site(site)
                .type(AttendanceType.CHECK_IN)
                .clientType(AttendanceType.CHECK_IN)
                .source(AttendanceSource.TERMINAL_FACE)
                .locationValid(true)
                .recordedAt(at)
                .build();
    }
}
