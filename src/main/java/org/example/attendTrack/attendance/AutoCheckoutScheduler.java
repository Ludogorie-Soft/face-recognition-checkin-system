package org.example.attendTrack.attendance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCheckoutScheduler {

    private final AttendanceRepository attendanceRepository;

    /**
     * Runs at 00:01 every day. Searches the last 7 days for workers who checked in
     * but never checked out, and creates an automatic CHECK_OUT at the site's
     * workEndTime (or 23:59 for sites without a configured end time).
     *
     * The 7-day rolling window ensures that offline sync records arriving late
     * (synced hours or days after the actual check-in) are still processed.
     */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void autoCheckoutMissedWorkers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.toLocalDate().minusDays(7).atStartOfDay();
        LocalDateTime to = now.toLocalDate().atStartOfDay(); // exclude today's ongoing sessions
        int created = processUnclosedCheckIns(from, to, now);
        log.info("Scheduled auto-checkout complete: {} record(s) created (window: {} → {})", created, from, to);
    }

    /**
     * Manual retrigger — callable from the admin API to recover records missed
     * during a scheduler downtime or after a bulk offline sync.
     */
    @Transactional
    public int triggerAutoCheckout(LocalDate from, LocalDate to) {
        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.plusDays(1).atStartOfDay();
        LocalDateTime checkOutTo = LocalDateTime.now();
        log.info("Manual auto-checkout trigger: {} → {}", from, to);
        int created = processUnclosedCheckIns(dtFrom, dtTo, checkOutTo);
        log.info("Manual auto-checkout complete: {} record(s) created", created);
        return created;
    }

    private int processUnclosedCheckIns(LocalDateTime from, LocalDateTime to, LocalDateTime checkOutTo) {
        List<Attendance> unclosed = attendanceRepository.findUnclosedCheckIns(from, to, checkOutTo);
        if (unclosed.isEmpty()) {
            log.debug("Auto-checkout: no unclosed check-ins in window {} → {}", from, to);
            return 0;
        }

        // Keep only the latest CHECK_IN per (worker, site) — guards against duplicate sync records
        // for the same logical session.
        Map<String, Attendance> lastCheckIn = new LinkedHashMap<>();
        for (Attendance a : unclosed) {
            String key = a.getWorker().getId() + ":" + a.getSite().getId();
            lastCheckIn.merge(key, a, (existing, next) ->
                    next.getRecordedAt().isAfter(existing.getRecordedAt()) ? next : existing);
        }

        Collection<Attendance> candidates = lastCheckIn.values();
        log.info("Auto-checkout: {} candidate(s) (from {} raw records, window {} → {})",
                candidates.size(), unclosed.size(), from, to);

        int created = 0;
        for (Attendance checkIn : candidates) {
            LocalTime endTime = checkIn.getSite().getWorkEndTime();
            if (endTime == null) {
                // Sites without a configured end time use 23:59 as a safe fallback
                endTime = LocalTime.of(23, 59);
            }

            // Compute checkout time relative to the check-in date, not today
            LocalDate checkInDate = checkIn.getRecordedAt().toLocalDate();
            LocalDateTime checkOutTime = checkInDate.atTime(endTime);
            if (!checkOutTime.isAfter(checkIn.getRecordedAt())) {
                // End time is earlier than check-in (e.g. night shift: in at 22:00, end at 06:00)
                // → place checkout on the following morning
                checkOutTime = checkInDate.plusDays(1).atTime(endTime);
            }

            if (attendanceRepository.existsDuplicate(
                    checkIn.getWorker().getId(), checkIn.getSite().getId(),
                    AttendanceType.CHECK_OUT, checkOutTime)) {
                log.debug("Auto-checkout skipped: duplicate exists for worker {} at {}",
                        checkIn.getWorker().getId(), checkOutTime);
                continue;
            }

            attendanceRepository.save(Attendance.builder()
                    .worker(checkIn.getWorker())
                    .site(checkIn.getSite())
                    .manager(null)
                    .type(AttendanceType.CHECK_OUT)
                    .lat(checkIn.getLat())
                    .lng(checkIn.getLng())
                    .locationValid(true)
                    .faceConfidence(null)
                    .manualOverride(true)
                    .recordedAt(checkOutTime)
                    .syncedAt(LocalDateTime.now())
                    .build());

            log.info("Auto-checkout: worker {} at site {} → {}",
                    checkIn.getWorker().getId(), checkIn.getSite().getName(), checkOutTime);
            created++;
        }

        return created;
    }
}
