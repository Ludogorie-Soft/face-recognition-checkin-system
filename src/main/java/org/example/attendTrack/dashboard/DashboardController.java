package org.example.attendTrack.dashboard;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    /** A 12/24h shift open longer than this is almost certainly a forgotten check-out. */
    private static final int GUARD_OPEN_SHIFT_ALERT_HOURS = 26;

    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        long totalSites = siteRepository.findAllByActiveTrue().size();
        long totalWorkers = userRepository.findAllByRoleAndActiveTrue(Role.WORKER).size();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        long presentToday = attendanceRepository.countDistinctWorkersPresentBetween(startOfDay, endOfDay);
        long missingToday = Math.max(0, totalWorkers - presentToday);

        return ResponseEntity.ok(new DashboardStats(totalSites, totalWorkers, presentToday, missingToday));
    }

    @GetMapping("/extended")
    public ResponseEntity<DashboardExtended> getExtended() {
        LocalDate today = LocalDate.now();

        // ── Weekly chart ──────────────────────────────────────────────────────
        LocalDate thisMonday = today.with(DayOfWeek.MONDAY);
        LocalDate lastMonday = thisMonday.minusWeeks(1);

        List<DayAttendance> thisWeek = buildWeekData(thisMonday, today);
        List<DayAttendance> lastWeek = buildWeekData(lastMonday, lastMonday.plusDays(6));

        // ── Site breakdown ────────────────────────────────────────────────────
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<SiteAttendance> sites = siteRepository.findAllByActiveTrue().stream()
                .map(site -> {
                    long present = attendanceRepository.countDistinctWorkersPresentBySite(
                            site.getId(), startOfDay, endOfDay);
                    long total = siteWorkerRepository.countBySiteId(site.getId());
                    return new SiteAttendance(site.getId(), site.getName(), present, total);
                })
                .sorted(Comparator.comparing(SiteAttendance::siteName))
                .toList();

        // ── Recent activity ───────────────────────────────────────────────────
        List<ActivityEntry> recentActivity = attendanceRepository.findRecentActivity(PageRequest.of(0, 15));

        // ── Auto-checkouts last night ─────────────────────────────────────────
        LocalDate yesterday = today.minusDays(1);
        long autoCheckoutsLastNight = attendanceRepository.countAutoCheckouts(
                yesterday.atStartOfDay(), today.atStartOfDay());

        // ── Out-of-zone check-ins today ───────────────────────────────────────
        List<OutOfZoneEntry> outOfZoneToday =
                attendanceRepository.findOutOfZoneCheckInsToday(startOfDay, endOfDay);

        // ── Guard shifts open longer than a shift can plausibly run ───────────
        List<OpenShiftEntry> openGuardShifts = attendanceRepository.findOpenShiftsOlderThan(
                LocalDateTime.now().minusHours(GUARD_OPEN_SHIFT_ALERT_HOURS));

        return ResponseEntity.ok(new DashboardExtended(
                thisWeek, lastWeek, sites, recentActivity,
                autoCheckoutsLastNight, outOfZoneToday, openGuardShifts
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DayAttendance> buildWeekData(LocalDate monday, LocalDate maxDay) {
        List<DayAttendance> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            long count = day.isAfter(maxDay) ? 0
                    : attendanceRepository.countDistinctWorkersPresentBetween(
                            day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            result.add(new DayAttendance(day, count));
        }
        return result;
    }
}
