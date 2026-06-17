package org.example.garant.attendance;

import lombok.RequiredArgsConstructor;
import org.example.garant.attendance.dto.AttendanceRecord;
import org.example.garant.attendance.dto.AttendanceSyncRequest;
import org.example.garant.attendance.dto.AttendanceSyncResponse;
import org.example.garant.common.exception.ApiException;
import org.example.garant.common.exception.ErrorCode;
import org.example.garant.site.Site;
import org.example.garant.site.SiteManagerRepository;
import org.example.garant.site.SiteRepository;
import org.example.garant.site.SiteWorkerRepository;
import org.example.garant.user.Role;
import org.example.garant.user.User;
import org.example.garant.user.UserRepository;
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
    private final SiteManagerRepository siteManagerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<UUID, AttendanceType> getTodayStatus(UUID siteId, User currentUser) {
        if (!siteRepository.existsById(siteId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }
        if (currentUser.getRole() == Role.MANAGER
                && !siteManagerRepository.existsBySiteIdAndUserId(siteId, currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.SITE_NOT_ASSIGNED, "You are not assigned to this site");
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
    public AttendanceSyncResponse sync(User manager, AttendanceSyncRequest request) {
        Site site = siteRepository.findById(request.siteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + request.siteId()));

        // Managers can only sync for their assigned sites
        if (manager.getRole() == Role.MANAGER
                && !siteManagerRepository.existsBySiteIdAndUserId(site.getId(), manager.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.SITE_NOT_ASSIGNED, "You are not assigned to this site");
        }

        int saved = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (AttendanceRecord record : request.records()) {
            try {
                saved += processRecord(record, site, manager);
            } catch (Exception e) {
                skipped++;
                errors.add("Worker %s — %s".formatted(record.workerId(), e.getMessage()));
            }
        }

        return new AttendanceSyncResponse(saved, skipped, errors);
    }

    private int processRecord(AttendanceRecord record, Site site, User manager) {
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
                .manager(manager)
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
