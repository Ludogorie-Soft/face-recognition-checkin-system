package org.example.attendTrack.attendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.attendTrack.dashboard.OpenShiftEntry;
import org.example.attendTrack.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Watches 12/24h shifts (guards) that were never checked out.
 *
 * <p>Deliberately does NOT close them. A guard's shift may be 12h or 24h, so any automatic end time
 * would be wrong for some of them — and a wrong time is worse than a missing one, because it looks
 * like real data on the payroll report. Instead the admin is alerted and enters the real time.
 * Guards are excluded from {@link AutoCheckoutScheduler} for the same reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardShiftMonitor {

    private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    /** A shift open longer than this is almost certainly a forgotten check-out. */
    @Value("${attendance.guard-open-shift-alert-hours:26}")
    private int alertAfterHours;

    /** Runs every morning at 09:00 — the dashboard shows the same list live at any time. */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void alertOnOpenGuardShifts() {
        List<OpenShiftEntry> open = attendanceRepository.findOpenShiftsOlderThan(
                LocalDateTime.now().minusHours(alertAfterHours));

        if (open.isEmpty()) {
            log.debug("Guard shift monitor: no shifts open longer than {}h", alertAfterHours);
            return;
        }

        log.warn("Guard shift monitor: {} shift(s) open longer than {}h — admins notified",
                open.size(), alertAfterHours);
        notificationService.notifyOpenGuardShifts(open);
    }
}
