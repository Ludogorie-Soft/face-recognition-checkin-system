package org.example.garant.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.garant.attendance.Attendance;
import org.example.garant.attendance.AttendanceRepository;
import org.example.garant.attendance.AttendanceType;
import org.example.garant.site.Site;
import org.example.garant.site.SiteRepository;
import org.example.garant.site.SiteWorkerRepository;
import org.example.garant.user.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    /**
     * Runs every minute. For each active site whose work day ends 30 minutes ago,
     * checks which workers have not checked in and sends notifications.
     */
    @Scheduled(cron = "0 * * * * *")
    public void checkMissingWorkers() {
        LocalTime triggerTime = LocalTime.now()
                .withSecond(0)
                .withNano(0)
                .minusMinutes(30);

        siteRepository.findByWorkEndTime(triggerTime).forEach(site -> {
            try {
                List<User> missing = findMissingWorkers(site, LocalDate.now());
                if (!missing.isEmpty()) {
                    log.info("Site '{}' has {} missing worker(s)", site.getName(), missing.size());
                    notificationService.notifyMissingWorkers(site, missing);
                }
            } catch (Exception e) {
                log.error("Error checking missing workers for site {}: {}", site.getId(), e.getMessage());
            }
        });
    }

    private List<User> findMissingWorkers(Site site, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Set<String> checkedInIds = attendanceRepository
                .findBySiteAndDateRange(site.getId(), start, end)
                .stream()
                .filter(a -> a.getType() == AttendanceType.CHECK_IN)
                .map(a -> a.getWorker().getId().toString())
                .collect(Collectors.toSet());

        return siteWorkerRepository.findBySiteId(site.getId()).stream()
                .map(sw -> sw.getUser())
                .filter(w -> !checkedInIds.contains(w.getId().toString()))
                .toList();
    }
}
