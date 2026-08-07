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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
                    isWithinCheckpoint(record.lat(), record.lng(), cp));
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

        // Track last record, first check-in, and last check-out per worker
        Map<UUID, Attendance> lastRecord = new LinkedHashMap<>();
        Map<UUID, Attendance> firstCheckIn = new LinkedHashMap<>();
        Map<UUID, Attendance> lastCheckOut = new LinkedHashMap<>();
        for (Attendance a : attendanceRepository.findTodayBySite(siteId, from, to)) {
            UUID wid = a.getWorker().getId();
            lastRecord.put(wid, a);
            if (a.getType() == AttendanceType.CHECK_IN) {
                firstCheckIn.putIfAbsent(wid, a);
            } else if (a.getType() == AttendanceType.CHECK_OUT) {
                lastCheckOut.put(wid, a);
            }
        }

        return siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> {
                    User w = sw.getUser();
                    UUID wid = w.getId();
                    Attendance last = lastRecord.get(wid);
                    Attendance ci = firstCheckIn.get(wid);
                    Attendance co = lastCheckOut.get(wid);

                    LocalTime checkInTime = ci != null ? ci.getRecordedAt().toLocalTime() : null;
                    LocalTime checkOutTime = co != null ? co.getRecordedAt().toLocalTime() : null;
                    Double calculatedHours = null;
                    if (ci != null && co != null) {
                        long minutes = Duration.between(ci.getRecordedAt(), co.getRecordedAt()).toMinutes();
                        calculatedHours = Math.round(minutes / 60.0 * 10) / 10.0;
                    }

                    return new WorkerDayStatus(
                            w.getId(),
                            w.getName(),
                            last != null ? last.getId() : null,
                            last != null ? last.getType() : null,
                            checkInTime,
                            checkOutTime,
                            calculatedHours
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

        // Use the provided time (or current time) so the record lands in the correct day
        LocalDateTime recordedAt = targetDate.atTime(req.time() != null ? req.time() : LocalTime.now());

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

    // ── Change site on existing attendance record ─────────────────────────────

    @Transactional
    public void changeSite(UUID attendanceId, UUID newSiteId) {
        Attendance a = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + attendanceId));

        Site newSite = siteRepository.findById(newSiteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.SITE_NOT_FOUND, "Site not found: " + newSiteId));

        // Re-compute locationValid for the new site
        List<SiteCheckpoint> checkpoints = siteCheckpointRepository.findBySiteId(newSiteId);
        boolean locationValid;
        if (!checkpoints.isEmpty()) {
            locationValid = checkpoints.stream().anyMatch(cp ->
                    isWithinCheckpoint(a.getLat(), a.getLng(), cp));
        } else {
            locationValid = haversineDistance(a.getLat(), a.getLng(), newSite.getLat(), newSite.getLng())
                    <= newSite.getRadiusMeters();
        }

        attendanceRepository.updateSite(attendanceId, newSiteId);
        if (locationValid != a.isLocationValid()) {
            attendanceRepository.updateLocationValidBulk(List.of(attendanceId), locationValid);
        }
    }

    // ── Retroactive location re-validation ───────────────────────────────────

    @Transactional
    public int revalidateLocation(UUID siteId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Attendance> records = siteId != null
                ? attendanceRepository.findBySiteAndDateRange(siteId, start, end)
                : attendanceRepository.findAllInDateRange(start, end);

        if (records.isEmpty()) return 0;

        // Bulk-load checkpoints to avoid N+1
        Map<UUID, List<SiteCheckpoint>> checkpointsBySite;
        if (siteId != null) {
            checkpointsBySite = Map.of(siteId, siteCheckpointRepository.findBySiteId(siteId));
        } else {
            List<UUID> siteIds = records.stream()
                    .map(a -> a.getSite().getId())
                    .distinct()
                    .toList();
            checkpointsBySite = new java.util.HashMap<>();
            siteCheckpointRepository.findBySiteIdIn(siteIds)
                    .forEach(cp -> checkpointsBySite
                            .computeIfAbsent(cp.getSite().getId(), k -> new ArrayList<>())
                            .add(cp));
        }

        List<UUID> nowValid = new ArrayList<>();
        List<UUID> nowInvalid = new ArrayList<>();

        for (Attendance a : records) {
            List<SiteCheckpoint> cps = checkpointsBySite.getOrDefault(a.getSite().getId(), List.of());
            boolean valid;
            if (!cps.isEmpty()) {
                valid = cps.stream().anyMatch(cp -> isWithinCheckpoint(a.getLat(), a.getLng(), cp));
            } else {
                valid = haversineDistance(a.getLat(), a.getLng(), a.getSite().getLat(), a.getSite().getLng())
                        <= a.getSite().getRadiusMeters();
            }
            if (valid != a.isLocationValid()) {
                (valid ? nowValid : nowInvalid).add(a.getId());
            }
        }

        if (!nowValid.isEmpty()) attendanceRepository.updateLocationValidBulk(nowValid, true);
        if (!nowInvalid.isEmpty()) attendanceRepository.updateLocationValidBulk(nowInvalid, false);

        return nowValid.size() + nowInvalid.size();
    }

    /**
     * Returns true if (userLat, userLng) is within the checkpoint's zone.
     * For POINT checkpoints: standard haversine circle check.
     * For LINE checkpoints: perpendicular distance from user to the line segment ≤ radiusMeters.
     */
    private static boolean isWithinCheckpoint(double userLat, double userLng, SiteCheckpoint cp) {
        if (cp.getCheckpointType() == SiteCheckpoint.CheckpointType.LINE
                && cp.getLat2() != null && cp.getLng2() != null) {
            return distanceToSegmentMeters(
                    userLat, userLng,
                    cp.getLat(), cp.getLng(),
                    cp.getLat2(), cp.getLng2()) <= cp.getRadiusMeters();
        }
        return haversineDistance(userLat, userLng, cp.getLat(), cp.getLng()) <= cp.getRadiusMeters();
    }

    /**
     * Minimum distance (metres) from point P to line segment AB.
     * Uses a planar approximation valid for short distances (< ~1 km).
     */
    private static double distanceToSegmentMeters(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng) {
        // Convert to local Cartesian coordinates (metres) relative to A
        final double R = 6_371_000.0;
        final double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));
        double ax = 0, ay = 0;
        double bx = Math.toRadians(bLng - aLng) * R * cosLat;
        double by = Math.toRadians(bLat - aLat) * R;
        double px = Math.toRadians(pLng - aLng) * R * cosLat;
        double py = Math.toRadians(pLat - aLat) * R;

        double abx = bx - ax, aby = by - ay;
        double len2 = abx * abx + aby * aby;
        if (len2 < 1e-10) {
            // Degenerate segment (endpoints identical) — fall back to point distance
            return Math.sqrt(px * px + py * py);
        }
        // Project P onto AB, clamped to [0, 1]
        double t = Math.max(0, Math.min(1, (px * abx + py * aby) / len2));
        double closestX = ax + t * abx;
        double closestY = ay + t * aby;
        double dx = px - closestX, dy = py - closestY;
        return Math.sqrt(dx * dx + dy * dy);
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
