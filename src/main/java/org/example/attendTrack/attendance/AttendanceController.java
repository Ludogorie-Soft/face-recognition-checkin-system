package org.example.attendTrack.attendance;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.attendance.dto.AttendanceSyncRequest;
import org.example.attendTrack.attendance.dto.AttendanceSyncResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/today")
    public ResponseEntity<Map<String, String>> getTodayStatus(@RequestParam UUID siteId) {
        Map<UUID, AttendanceType> status = attendanceService.getTodayStatus(siteId);
        Map<String, String> response = new java.util.LinkedHashMap<>();
        status.forEach((id, type) -> response.put(id.toString(), type.name()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync")
    public ResponseEntity<AttendanceSyncResponse> sync(
            @Valid @RequestBody AttendanceSyncRequest request) {
        return ResponseEntity.ok(attendanceService.sync(null, request));
    }
}
