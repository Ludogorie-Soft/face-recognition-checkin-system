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
     * Runs at 00:01 every day. For each worker who checked in yesterday at a site
     * with a configured workEndTime but never checked out, creates an automatic
     * CHECK_OUT at the site's workEndTime.
     */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void autoCheckoutMissedWorkers() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime from = yesterday.atStartOfDay();
        LocalDateTime to = yesterday.plusDays(1).atStartOfDay();          // today 00:00 — CHECK_IN window
        LocalDateTime checkOutTo = yesterday.plusDays(1).atTime(LocalTime.of(6, 0)); // today 06:00 — catches late checkouts

        List<Attendance> unclosed = attendanceRepository.findUnclosedCheckIns(from, to, checkOutTo);
        if (unclosed.isEmpty()) return;

        // Keep only the latest CHECK_IN per (worker, site) — guards against duplicate sync records
        Map<String, Attendance> lastCheckIn = new LinkedHashMap<>();
        for (Attendance a : unclosed) {
            String key = a.getWorker().getId() + ":" + a.getSite().getId();
            lastCheckIn.merge(key, a, (existing, next) ->
                    next.getRecordedAt().isAfter(existing.getRecordedAt()) ? next : existing);
        }

        Collection<Attendance> candidates = lastCheckIn.values();
        log.info("Auto-checkout: {} candidate(s) for {} (from {} raw records)",
                candidates.size(), yesterday, unclosed.size());

        int created = 0;
        for (Attendance checkIn : candidates) {
            LocalTime endTime = checkIn.getSite().getWorkEndTime();
            // Try checkout on the same day first; if that is not after check-in
            // (overnight shift), place it on the following day instead.
            LocalDateTime checkOutTime = yesterday.atTime(endTime);
            if (!checkOutTime.isAfter(checkIn.getRecordedAt())) {
                checkOutTime = yesterday.plusDays(1).atTime(endTime);
            }

            // Final safety: if still not after check-in, skip (misconfigured site)
            if (!checkOutTime.isAfter(checkIn.getRecordedAt())) {
                log.debug("Auto-checkout skipped: workEndTime {} is not after checkIn {} for worker {}",
                        checkOutTime, checkIn.getRecordedAt(), checkIn.getWorker().getId());
                continue;
            }

            // Guard against duplicate auto-checkouts (e.g. scheduler restart or overnight
            // checkout time falling exactly on the checkOutTo boundary of the search window)
            if (attendanceRepository.existsDuplicate(
                    checkIn.getWorker().getId(), checkIn.getSite().getId(),
                    AttendanceType.CHECK_OUT, checkOutTime)) {
                log.debug("Auto-checkout skipped: duplicate already exists for worker {} at {}",
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

            log.debug("Auto-checkout: worker {} at site {} → {}",
                    checkIn.getWorker().getId(), checkIn.getSite().getId(), checkOutTime);
            created++;
        }

        log.info("Auto-checkout: {} record(s) created for {}", created, yesterday);
    }
}
