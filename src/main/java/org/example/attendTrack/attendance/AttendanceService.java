package org.example.attendTrack.attendance;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.dto.AttendanceRecord;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final UserRepository userRepository;

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

        int saved = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (AttendanceRecord record : request.records()) {
            try {
                saved += processRecord(record, site, admin);
            } catch (Exception e) {
                skipped++;
                errors.add("Worker %s — %s".formatted(record.workerId(), e.getMessage()));
            }
        }

        return new AttendanceSyncResponse(saved, skipped, errors);
    }

    private int processRecord(AttendanceRecord record, Site site, User admin) {
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

        attendanceRepository.save(Attendance.builder()
                .worker(worker)
                .site(site)
                .manager(admin)
                .type(record.type())
                .lat(record.lat())
                .lng(record.lng())
                .locationValid(record.locationValid())
                .faceConfidence(record.faceConfidence())
                .manualOverride(record.manualOverride())
                .recordedAt(record.recordedAt())
                .syncedAt(LocalDateTime.now())
                .build());

        return 1;
    }
}
