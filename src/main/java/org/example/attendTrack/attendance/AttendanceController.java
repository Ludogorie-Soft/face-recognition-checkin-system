package org.example.attendTrack.attendance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.dto.AttendanceDetail;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.attendance.dto.ManualAttendanceRequest;
import org.example.attendTrack.attendance.dto.WorkerDayStatus;
import org.example.attendTrack.user.User;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AutoCheckoutScheduler autoCheckoutScheduler;

    @GetMapping("/today")
    public ResponseEntity<Map<String, String>> getTodayStatus(@RequestParam UUID siteId) {
        Map<UUID, AttendanceType> status = attendanceService.getTodayStatus(siteId);
        Map<String, String> response = new java.util.LinkedHashMap<>();
        status.forEach((id, type) -> response.put(id.toString(), type.name()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync")
    public ResponseEntity<AttendanceSyncResponse> sync(
            @Valid @RequestBody AttendanceSyncRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(attendanceService.sync(null, request, clientIp, userAgent));
    }

    /**
     * Real client IP behind the nginx → Next.js proxy chain.
     * X-Real-IP is set by our nginx (proxy_set_header X-Real-IP $remote_addr) and OVERWRITES any
     * client-supplied value, so it is trustworthy. X-Forwarded-For is only a fallback and we take
     * its LAST hop (the one nginx appended via $proxy_add_x_forwarded_for) — never XFF[0], which a
     * client can spoof.
     */
    private static String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim(); // nginx-appended hop, not the spoofable first entry
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttendanceDetail> getDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(attendanceService.getDetail(id));
    }

    // ── Manual attendance — admin only ────────────────────────────────────────

    @GetMapping("/workers-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkerDayStatus>> getWorkersDayStatus(
            @RequestParam UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(attendanceService.getWorkersDayStatus(siteId, date));
    }

    @PostMapping("/manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> manualRecord(
            @Valid @RequestBody ManualAttendanceRequest request,
            @AuthenticationPrincipal User admin) {
        attendanceService.manualRecord(request, admin);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAttendance(@PathVariable UUID id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/move-session")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> moveSession(
            @RequestParam UUID checkInId,
            @RequestParam(required = false) UUID checkOutId,
            @RequestParam UUID siteId) {
        attendanceService.changeSite(checkInId, siteId);
        if (checkOutId != null) attendanceService.changeSite(checkOutId, siteId);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/site")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> changeSite(
            @PathVariable UUID id,
            @RequestParam UUID siteId) {
        attendanceService.changeSite(id, siteId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/auto-checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> triggerAutoCheckout(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int created = autoCheckoutScheduler.triggerAutoCheckout(from, to);
        return ResponseEntity.ok(Map.of("created", created));
    }

    @PostMapping("/revalidate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Integer>> revalidateLocation(
            @RequestParam(required = false) UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int updated = attendanceService.revalidateLocation(siteId, from, to);
        return ResponseEntity.ok(Map.of("updated", updated));
    }
}
