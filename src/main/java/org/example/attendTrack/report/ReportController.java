package org.example.attendTrack.report;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.report.dto.AttendanceReportRow;
import org.example.attendTrack.report.dto.HoursCorrectionRequest;
import org.example.attendTrack.report.dto.MissingWorkerReport;
import org.example.attendTrack.report.dto.WorkedHoursRow;
import org.example.attendTrack.report.dto.WorkedHoursSummaryRow;
import org.example.attendTrack.user.User;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/attendance")
    public ResponseEntity<List<AttendanceReportRow>> getAttendance(
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getAttendance(siteId, companyId, from, to));
    }

    @GetMapping("/missing")
    public ResponseEntity<List<MissingWorkerReport>> getMissingWorkers(
            @RequestParam UUID siteId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getMissingWorkers(siteId, companyId, date));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam(required = false) UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] excel = reportService.exportAttendanceToExcel(siteId, from, to);
        String filename = "attendance_%s_%s.xlsx".formatted(from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    // ── Worked Hours ──────────────────────────────────────────────────────────

    @GetMapping("/hours")
    public ResponseEntity<List<WorkedHoursRow>> getWorkedHours(
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getWorkedHours(siteId, companyId, from, to));
    }

    @GetMapping("/hours/summary")
    public ResponseEntity<List<WorkedHoursSummaryRow>> getWorkedHoursSummary(
            @RequestParam(required = false) UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getWorkedHoursSummary(companyId, from, to));
    }

    @PutMapping("/hours/correction")
    public ResponseEntity<Void> saveHoursCorrection(
            @Valid @RequestBody HoursCorrectionRequest request,
            @AuthenticationPrincipal User currentUser) {
        reportService.saveHoursCorrection(request, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hours/export")
    public ResponseEntity<byte[]> exportWorkedHours(
            @RequestParam(required = false) UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] excel = reportService.exportWorkedHoursToExcel(siteId, from, to);
        String filename = "worked_hours_%s_%s.xlsx".formatted(from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    @GetMapping("/hours/summary/export")
    public ResponseEntity<byte[]> exportWorkedHoursSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] excel = reportService.exportWorkedHoursSummaryToExcel(from, to);
        String filename = "worked_hours_summary_%s_%s.xlsx".formatted(from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }
}
