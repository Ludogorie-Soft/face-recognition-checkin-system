package org.example.attendTrack.dashboard;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final SiteRepository siteRepository;
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
}
