package org.example.attendTrack.attendance;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.dto.AttendanceRecord;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.attendance.dto.ManualAttendanceRequest;
import org.example.attendTrack.attendance.dto.WorkerDayStatus;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.notification.NotificationService;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteCheckpoint;
import org.example.attendTrack.site.SiteCheckpointRepository;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final SiteCheckpointRepository siteCheckpointRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<UUID, AttendanceType> getTodayStatus(UUID siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        Map<UUID, AttendanceType> result = new LinkedHashMap<>();
        for (Attendance a : attendanceRepository.findTodayBySite(siteId, start, end)) {
            result.put(a.getWorker().getId(), a.getType()); // last record per worker wins
        }
        return result;
    }

    @Transactional
    public AttendanceSyncResponse sync(User admin, AttendanceSyncRequest request) {
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + request.siteId()));

        List<SiteCheckpoint> checkpoints = siteCheckpointRepository.findBySiteId(site.getId());

        int saved = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        // Track workers already notified this sync — prevent notification spam for batch uploads
        Set<UUID> notifiedWorkers = new HashSet<>();

        for (AttendanceRecord record : request.records()) {
            try {
                saved += processRecord(record, site, admin, checkpoints, notifiedWorkers);
            } catch (Exception e) {
                skipped++;
                errors.add("Worker %s — %s".formatted(record.workerId(), e.getMessage()));
            }
        }

        return new AttendanceSyncResponse(saved, skipped, errors);
    }

    private int processRecord(AttendanceRecord record, Site site, User admin,
                              List<SiteCheckpoint> checkpoints, Set<UUID> notifiedWorkers) {
        // Skip duplicates
        if (attendanceRepository.existsDuplicate(
                record.workerId(), site.getId(), record.type(), record.recordedAt())) {
            return 0;
        }

        User worker = userRepository.findById(record.workerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.USER_NOT_FOUND, "Worker not found: " + record.workerId()));

        if (!siteWorkerRepository.existsBySiteIdAndUserId(site.getId(), worker.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SITE_NOT_ASSIGNED, "Worker is not assigned to this site");
        }

        // Server-side location validation using checkpoints
        boolean locationValid;
        if (!checkpoints.isEmpty()) {
            locationValid = checkpoints.stream().anyMatch(cp ->
                    haversineDistance(record.lat(), record.lng(), cp.getLat(), cp.getLng()) <= cp.getRadiusMeters()
            );
        } else {
            locationValid = haversineDistance(record.lat(), record.lng(), site.getLat(), site.getLng()) <= site.getRadiusMeters();
        }

        if (!locationValid && notifiedWorkers.add(worker.getId())) {
            // At most one notification per worker per sync batch
            notificationService.notifySuspiciousCheckIn(site, worker, record.lat(), record.lng());
        }

        attendanceRepository.save(Attendance.builder()
                .worker(worker)
                .site(site)
                .manager(admin)
                .type(record.type())
                .lat(record.lat())
                .lng(record.lng())
                .locationValid(locationValid)
                .faceConfidence(record.faceConfidence())
                .manualOverride(record.manualOverride())
                .recordedAt(record.recordedAt())
                .syncedAt(LocalDateTime.now())
                .build());

        return 1;
    }

    // ── Manual attendance (admin) ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkerDayStatus> getWorkersDayStatus(UUID siteId, LocalDate date) {
        if (!siteRepository.existsById(siteId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        // Last attendance record per worker for this site + day
        Map<UUID, Attendance> lastRecord = new LinkedHashMap<>();
        for (Attendance a : attendanceRepository.findTodayBySite(siteId, from, to)) {
            lastRecord.put(a.getWorker().getId(), a); // later records overwrite earlier ones
        }

        return siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> {
                    User w = sw.getUser();
                    Attendance last = lastRecord.get(w.getId());
                    return new WorkerDayStatus(
                            w.getId(),
                            w.getName(),
                            last != null ? last.getId() : null,
                            last != null ? last.getType() : null
                    );
                })
                .sorted(Comparator.comparing(WorkerDayStatus::workerName))
                .toList();
    }

    @Transactional
    public void manualRecord(ManualAttendanceRequest req, User admin) {
        User worker = userRepository.findById(req.workerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.USER_NOT_FOUND, "Worker not found: " + req.workerId()));
        Site site = siteRepository.findById(req.siteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.SITE_NOT_FOUND, "Site not found: " + req.siteId()));

        if (!siteWorkerRepository.existsBySiteIdAndUserId(site.getId(), worker.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SITE_NOT_ASSIGNED, "Worker is not assigned to this site");
        }

        // Derive the target date (defaults to today)
        LocalDate targetDate = req.date() != null ? req.date() : LocalDate.now();
        LocalDateTime dayStart = targetDate.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);

        // Find current last-type for this worker+site on the target date (targeted query)
        List<Attendance> lastRecords = attendanceRepository
                .findLastForWorkerOnDay(worker.getId(), site.getId(), dayStart, dayEnd, PageRequest.of(0, 1));
        AttendanceType lastType = lastRecords.isEmpty() ? null : lastRecords.get(0).getType();

        // Prevent redundant consecutive records (e.g. CHECK_IN when already checked in)
        if (req.type() == lastType) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_ATTENDANCE,
                    "Worker already has a " + req.type() + " record for this day");
        }

        // Use current time on the target date so the record lands in the correct day
        LocalDateTime recordedAt = targetDate.atTime(java.time.LocalTime.now());

        attendanceRepository.save(Attendance.builder()
                .worker(worker)
                .site(site)
                .manager(admin)
                .type(req.type())
                .lat(0.0)
                .lng(0.0)
                .locationValid(false)
                .faceConfidence(null)
                .manualOverride(true)
                .recordedAt(recordedAt)
                .syncedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void deleteAttendance(UUID id) {
        if (!attendanceRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id);
        }
        attendanceRepository.deleteById(id);
    }

    private static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
