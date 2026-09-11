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
import org.example.attendTrack.site.GeoResolver;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
        // The worker's own last kept event today, at whichever site they scanned. Someone checked
        // in at another site is offered a check-OUT here, which is what actually happened.
        for (Attendance a : attendanceRepository.findTodayForSiteWorkers(siteId, start, end)) {
            result.put(a.getWorker().getId(), a.getType()); // last record per worker wins
        }
        return result;
    }

    /**
     * Identifies one (worker, day) whose session sequence must be re-projected. Deliberately not
     * per-site: a worker has one sequence per day across every site they scan at.
     */
    record DayKey(UUID workerId, LocalDate day) {}

    /**
     * Resolves the site of each scan in one sync batch, caching the per-worker assignments and
     * per-site checkpoints it loads. A batch touches a handful of workers and sites, so this turns
     * what would be two queries per record into two queries per distinct worker and site.
     */
    private final class SiteResolver {
        private final Map<UUID, List<Site>> assignedSites = new HashMap<>();
        private final Map<UUID, List<SiteCheckpoint>> checkpointsBySite = new HashMap<>();

        GeoResolver.SiteMatch resolve(AttendanceRecord record, Site claimedSite) {
            // No fix yet (the terminal sends 0/0 until the GPS settles): nothing to resolve from.
            // Keep the terminal's choice rather than picking the site nearest to the Gulf of Guinea.
            if (record.lat() == 0.0 && record.lng() == 0.0) {
                return GeoResolver.unlocated(claimedSite);
            }

            List<Site> candidates = assignedSites.computeIfAbsent(record.workerId(), workerId ->
                    siteWorkerRepository.findByUserIdWithSite(workerId).stream()
                            .map(sw -> sw.getSite())
                            .toList());

            candidates.forEach(s -> checkpointsBySite.computeIfAbsent(
                    s.getId(), siteCheckpointRepository::findBySiteId));

            return GeoResolver.resolve(record.lat(), record.lng(), claimedSite,
                            candidates, checkpointsBySite, tolerance(record.accuracyMeters()))
                    // A worker with no assignments at all: keep the claim so processOne raises the
                    // existing SITE_NOT_ASSIGNED error instead of a confusing NoSuchElement.
                    .orElseGet(() -> GeoResolver.unlocated(claimedSite));
        }
    }

    /** How far a zone may be widened for a fix of the given accuracy. Null accuracy earns nothing. */
    private double tolerance(Double accuracyMeters) {
        if (accuracyMeters == null || accuracyMeters <= 0) return 0;
        return Math.min(accuracyMeters, geoAccuracyCapMeters);
    }

    /** How far back to look for a shift that is still open when projecting a new day. */
    private static final int SHIFT_LOOKBACK_HOURS = 48;

    /**
     * Longest a 12/24h shift may plausibly run. A check-in older than this is treated as forgotten:
     * the new scan opens a fresh session and the stale one is left open for an admin to correct,
     * rather than being closed into an absurd multi-day session.
     */
    private static final int MAX_OPEN_SHIFT_HOURS = 26;

    /**
     * Two scans of the same worker at the same site closer than this are the same physical action
     * (a double tap, or the face being recognised twice). Deliberately measured in SECONDS: a
     * genuinely short shift — checked in 08:00, out 08:10 — is real data and must be preserved.
     * Every accidental re-scan observed in production fell between 1 and 18 seconds.
     */
    @org.springframework.beans.factory.annotation.Value("${attendance.min-gap-seconds:60}")
    private int minGapSeconds;

    /**
     * Upper bound on how much a device's own GPS accuracy may widen a site's zone. The allowance is
     * real — a 10 m corridor and a ±15 m fix cannot both be taken literally — but an unbounded one
     * would let a 5 km "accuracy" reading validate a scan from the next town.
     */
    @org.springframework.beans.factory.annotation.Value("${attendance.geo-accuracy-cap-meters:50}")
    private double geoAccuracyCapMeters;

    // Self-reference so per-record processing runs in its OWN transaction (REQUIRES_NEW):
    // one bad record can no longer mark the whole sync batch rollback-only.
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AttendanceService self;

    public AttendanceSyncResponse sync(User admin, AttendanceSyncRequest request, String clientIp, String userAgent) {
        Site claimedSite = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + request.siteId()));

        // The site on the request is only what the terminal believes. Resolution happens per record,
        // from its coordinates, against every site the worker is assigned to.
        SiteResolver resolver = new SiteResolver();

        int saved = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        // Track workers already notified this sync — prevent notification spam for batch uploads
        Set<UUID> notifiedWorkers = new HashSet<>();
        // Days touched by this batch — each is re-projected once, after all inserts have committed.
        Set<DayKey> touchedDays = new LinkedHashSet<>();

        // Offline batches can arrive out of order; store chronologically for stable ids/ordering.
        List<AttendanceRecord> ordered = request.records().stream()
                .sorted(Comparator.comparing(AttendanceRecord::recordedAt))
                .toList();

        for (AttendanceRecord record : ordered) {
            try {
                GeoResolver.SiteMatch match = resolver.resolve(record, claimedSite);
                if (self.processOne(record, claimedSite, match, admin, notifiedWorkers, clientIp, userAgent) > 0) {
                    saved++;
                    // Keyed by the RESOLVED site — that is the day whose sequence actually changed.
                    touchedDays.add(new DayKey(record.workerId(), record.recordedAt().toLocalDate()));
                }
            } catch (Exception e) {
                skipped++;
                errors.add("Worker %s — %s".formatted(record.workerId(), e.getMessage()));
            }
        }

        // A 12/24h shift crosses midnight, so a change on one day can move the boundary of the
        // next one — re-project that day as well.
        Map<UUID, Boolean> shiftWorkerCache = new HashMap<>();
        for (DayKey key : Set.copyOf(touchedDays)) {
            if (shiftWorkerCache.computeIfAbsent(key.workerId(), this::isShiftWorker)) {
                touchedDays.add(new DayKey(key.workerId(), key.day().plusDays(1)));
            }
        }

        // Derive the authoritative direction for every day this batch touched. Doing it here —
        // after the raw events are committed — is what makes the outcome independent of the order
        // in which offline terminals happen to sync.
        for (DayKey key : touchedDays) {
            try {
                self.renormalizeDay(key.workerId(), key.day());
            } catch (Exception e) {
                errors.add("Re-projection failed for worker %s on %s — %s"
                        .formatted(key.workerId(), key.day(), e.getMessage()));
            }
        }

        return new AttendanceSyncResponse(saved, skipped, errors);
    }

    /**
     * Recomputes one worker's CHECK_IN / CHECK_OUT sequence for a day, from their raw events at
     * <b>every</b> site. Idempotent and independent of arrival order, so it is safe to run as often
     * as needed.
     *
     * <p>The sequence is per worker rather than per site because a person has one session at a
     * time. Someone who checks in at site A and scans at site B in the evening is ending that
     * session, not starting a second one — the per-site version read it as a fresh check-in, opened
     * a phantom session at B, and the nightly scheduler then closed both.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void renormalizeDay(UUID workerId, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        List<Attendance> records = attendanceRepository.findForWorkerDay(
                workerId, start, start.plusDays(1));
        if (records.isEmpty()) return;

        // Day shifts always start a day with a check-in. A 12/24h shift may still be open from the
        // previous day, in which case the next scan closes it rather than opening a new session.
        AttendanceType initialExpected = AttendanceType.CHECK_IN;
        if (isShiftWorker(workerId)) {
            List<Attendance> previous = attendanceRepository.findLastKeptBefore(
                    workerId, start, start.minusHours(SHIFT_LOOKBACK_HOURS), PageRequest.of(0, 1));
            if (!previous.isEmpty() && previous.get(0).getType() == AttendanceType.CHECK_IN) {
                // Continue the session only if it could still plausibly be running. A shift left
                // open for days must not be closed into a multi-day session by an unrelated scan.
                long openHours = Duration.between(
                        previous.get(0).getRecordedAt(), records.get(0).getRecordedAt()).toHours();
                if (openHours <= MAX_OPEN_SHIFT_HOURS) {
                    initialExpected = AttendanceType.CHECK_OUT;
                }
            }
        }

        List<SessionProjector.Event> events = records.stream()
                .map(a -> new SessionProjector.Event(a.getId(), a.getRecordedAt(), a.getSource(), a.getType()))
                .toList();

        Map<UUID, Attendance> byId = new HashMap<>();
        records.forEach(a -> byId.put(a.getId(), a));

        for (SessionProjector.Resolution r : SessionProjector.project(events, Duration.ofSeconds(minGapSeconds), initialExpected)) {
            Attendance a = byId.get(r.id());
            if (a == null) continue;
            boolean changed = a.getType() != r.type()
                    || a.isIgnored() != r.ignored()
                    || a.getIgnoredReason() != r.ignoreReason();
            if (changed) {
                a.applyProjection(r.type(), r.ignored(), r.ignoreReason()); // flushed by dirty checking
            }
        }
    }

    /** True when the worker runs 12/24h shifts that cross midnight (guards). */
    private boolean isShiftWorker(UUID workerId) {
        return userRepository.findById(workerId)
                .map(u -> u.getShiftType() == org.example.attendTrack.user.ShiftType.SHIFT_24H)
                .orElse(false);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int processOne(AttendanceRecord record, Site claimedSite, GeoResolver.SiteMatch match,
                          User admin, Set<UUID> notifiedWorkers,
                          String clientIp, String userAgent) {
        Site site = match.site();

        // Idempotency: this exact device event is already stored → nothing to do.
        if (record.clientEventId() != null
                && attendanceRepository.existsByClientEventId(record.clientEventId())) {
            return 0;
        }
        // Legacy dedup fallback for terminals that predate client event ids. Matches on the site the
        // TERMINAL claimed, for the same reason it matches on clientType: site_id is derived now, so
        // a re-sent record would otherwise be resolved afresh and look like a new event.
        if (attendanceRepository.existsDuplicate(
                record.workerId(), claimedSite.getId(), record.type(), record.recordedAt())) {
            return 0;
        }

        // Sanity-bound the device-provided timestamp: reject clearly bogus values (wrong device
        // clock, corrupt payload) while still allowing late offline syncs. recordedAt is device-local
        // and synced_at is server time, so the generous window also absorbs the timezone offset.
        LocalDateTime nowServer = LocalDateTime.now();
        if (record.recordedAt().isAfter(nowServer.plusDays(2))
                || record.recordedAt().isBefore(nowServer.minusDays(30))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "recordedAt out of acceptable range: " + record.recordedAt());
        }

        User worker = userRepository.findById(record.workerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.USER_NOT_FOUND, "Worker not found: " + record.workerId()));

        // The resolver only ever returns a site the worker is assigned to, so this can fail only
        // when the claimed site was kept for a record with no usable position.
        if (!siteWorkerRepository.existsBySiteIdAndUserId(site.getId(), worker.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SITE_NOT_ASSIGNED, "Worker is not assigned to this site");
        }

        boolean locationValid = match.inside();

        if (!locationValid && notifiedWorkers.add(worker.getId())) {
            // At most one notification per worker per sync batch
            notificationService.notifySuspiciousCheckIn(site, worker, record.lat(), record.lng());
        }

        // The terminal is NOT trusted to decide the direction. Store the raw event exactly as
        // reported (client_type) and let renormalizeDay() derive the authoritative `type` from the
        // whole day's ordered events once the batch is committed.
        attendanceRepository.save(Attendance.builder()
                .worker(worker)
                .site(site)
                .clientSite(claimedSite)
                .distanceMeters(match.metresOutside())
                .accuracyMeters(record.accuracyMeters())
                .manager(admin)
                .type(record.type())
                .clientType(record.type())
                .lat(record.lat())
                .lng(record.lng())
                .locationValid(locationValid)
                .faceConfidence(record.faceConfidence())
                .manualOverride(record.manualOverride())
                .clientEventId(record.clientEventId())
                .createdOffline(record.createdOffline())
                .source(record.manualOverride() ? AttendanceSource.TERMINAL_MANUAL : AttendanceSource.TERMINAL_FACE)
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .clientDeviceId(record.clientDeviceId())
                .appVersion(record.appVersion())
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
        for (Attendance a : attendanceRepository.findBySiteAndDateRange(siteId, from, to)) {
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

    /** Adds an admin record, then re-derives the day so the surrounding sequence stays consistent. */
    public void manualRecord(ManualAttendanceRequest req, User admin) {
        DayKey touched = self.storeManualRecord(req, admin);
        self.renormalizeDay(touched.workerId(), touched.day());
    }

    @Transactional
    public DayKey storeManualRecord(ManualAttendanceRequest req, User admin) {
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
                .findLastForWorkerOnDay(worker.getId(), dayStart, dayEnd, PageRequest.of(0, 1));
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
                // Mirrors the stored direction and site so the legacy dedup — which matches on the
                // client_* columns — still recognises an entry it already created.
                .clientType(req.type())
                .clientSite(site)
                .source(AttendanceSource.ADMIN_MANUAL)
                .recordedAt(recordedAt)
                .syncedAt(LocalDateTime.now())
                .build());

        return new DayKey(worker.getId(), targetDate);
    }

    @Transactional(readOnly = true)
    public org.example.attendTrack.attendance.dto.AttendanceDetail getDetail(UUID id) {
        Attendance a = attendanceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id));
        return org.example.attendTrack.attendance.dto.AttendanceDetail.from(a);
    }

    /**
     * Deletes a record and re-derives the day it belonged to. Split in two transactions on purpose:
     * the re-projection must see the DB after the delete has committed, otherwise it would rebuild
     * the day from the row that is being removed.
     */
    public void deleteAttendance(UUID id) {
        DayKey touched = self.removeRecord(id);
        self.renormalizeDay(touched.workerId(), touched.day());
    }

    @Transactional
    public DayKey removeRecord(UUID id) {
        Attendance a = attendanceRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + id));
        DayKey key = new DayKey(a.getWorker().getId(), a.getRecordedAt().toLocalDate());
        attendanceRepository.delete(a);
        return key;
    }

    // ── Change site on existing attendance record ─────────────────────────────

    /**
     * Moves a record to another site and re-derives BOTH days it touched — the record leaves one
     * site's sequence and joins another, so each has to be rebuilt. Re-projection runs after the
     * move has committed so it reads the record at its new site.
     */
    public void changeSite(UUID attendanceId, UUID newSiteId) {
        for (DayKey key : self.moveRecordToSite(attendanceId, newSiteId)) {
            self.renormalizeDay(key.workerId(), key.day());
        }
    }

    @Transactional
    public List<DayKey> moveRecordToSite(UUID attendanceId, UUID newSiteId) {
        Attendance a = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ATTENDANCE_NOT_FOUND, "Attendance record not found: " + attendanceId));

        Site newSite = siteRepository.findById(newSiteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.SITE_NOT_FOUND, "Site not found: " + newSiteId));

        // Re-measure against the new site. client_site_id is deliberately left alone: it records what
        // the terminal claimed, and that stays true no matter where an admin files the record.
        double distance = GeoResolver.distanceToZone(a.getLat(), a.getLng(), newSite,
                siteCheckpointRepository.findBySiteId(newSiteId), 0);
        GeoResolver.SiteMatch match = new GeoResolver.SiteMatch(newSite, distance);

        attendanceRepository.updateResolvedSite(
                attendanceId, newSiteId, match.inside(), match.metresOutside());

        // Moving a record between sites no longer splits its day: the sequence is the worker's, so
        // the same single day has to be rebuilt either way.
        return List.of(new DayKey(a.getWorker().getId(), a.getRecordedAt().toLocalDate()));
    }

    // ── Retroactive site re-resolution ───────────────────────────────────────

    /**
     * Re-decides site and in-zone status for stored records, from their coordinates.
     *
     * <p>Repairs the days when the terminal filed scans at whichever site happened to be the
     * worker's first assignment. Records that move sites change two days' sequences, so the
     * re-projection runs after the updates have committed.
     *
     * @param siteId restrict to records currently filed at this site, or null for all
     * @return how many records were changed
     */
    public int revalidateLocation(UUID siteId, LocalDate from, LocalDate to) {
        Reresolution result = self.reresolveSites(siteId, from, to);
        for (DayKey key : result.touchedDays()) {
            try {
                self.renormalizeDay(key.workerId(), key.day());
            } catch (Exception ignored) {
                // One unprojectable day must not abort the rest of the repair.
            }
        }
        return result.changed();
    }

    /** Outcome of a re-resolution pass: how many records moved, and which days must be rebuilt. */
    record Reresolution(int changed, Set<DayKey> touchedDays) {}

    /**
     * Must stay public: Spring only applies {@code @Transactional} to public methods, so a
     * package-private version would silently run without one and a failure part-way through would
     * leave the repair half-applied.
     */
    @Transactional
    public Reresolution reresolveSites(UUID siteId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<Attendance> records = siteId != null
                ? attendanceRepository.findBySiteAndDateRange(siteId, start, end)
                : attendanceRepository.findAllInDateRange(start, end);

        if (records.isEmpty()) return new Reresolution(0, Set.of());

        Map<UUID, List<Site>> assignedSites = new HashMap<>();
        Map<UUID, List<SiteCheckpoint>> checkpointsBySite = new HashMap<>();
        Set<DayKey> touched = new LinkedHashSet<>();
        int changed = 0;

        for (Attendance a : records) {
            // Scheduler and admin records inherit their site by construction; only terminal scans
            // carry a position that can be re-measured.
            if (a.getLat() == 0.0 && a.getLng() == 0.0) continue;

            UUID workerId = a.getWorker().getId();
            List<Site> candidates = assignedSites.computeIfAbsent(workerId, id ->
                    siteWorkerRepository.findByUserIdWithSite(id).stream().map(sw -> sw.getSite()).toList());
            candidates.forEach(s -> checkpointsBySite.computeIfAbsent(
                    s.getId(), siteCheckpointRepository::findBySiteId));

            GeoResolver.SiteMatch match = GeoResolver
                    .resolve(a.getLat(), a.getLng(), a.getSite(), candidates, checkpointsBySite,
                            tolerance(a.getAccuracyMeters()))
                    .orElse(null);
            if (match == null) continue;

            boolean siteChanged = !match.site().getId().equals(a.getSite().getId());
            boolean validChanged = match.inside() != a.isLocationValid();
            if (!siteChanged && !validChanged) continue;

            attendanceRepository.updateResolvedSite(
                    a.getId(), match.site().getId(), match.inside(), match.metresOutside());
            changed++;

            touched.add(new DayKey(workerId, a.getRecordedAt().toLocalDate()));
        }
        return new Reresolution(changed, touched);
    }
}
