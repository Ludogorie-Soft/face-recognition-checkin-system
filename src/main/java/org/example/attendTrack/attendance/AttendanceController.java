package org.example.attendTrack.attendance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.example.attendTrack.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/today")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getTodayStatus(@RequestParam UUID siteId) {
        Map<UUID, AttendanceType> status = attendanceService.getTodayStatus(siteId);
        Map<String, String> response = new java.util.LinkedHashMap<>();
        status.forEach((id, type) -> response.put(id.toString(), type.name()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttendanceSyncResponse> sync(
            @Valid @RequestBody AttendanceSyncRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(attendanceService.sync(currentUser, request));
    }
}
