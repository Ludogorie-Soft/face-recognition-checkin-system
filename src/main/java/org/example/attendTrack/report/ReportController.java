package org.example.garant.report;

import lombok.RequiredArgsConstructor;
import org.example.garant.report.dto.AttendanceReportRow;
import org.example.garant.report.dto.MissingWorkerReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
            @RequestParam UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.getAttendance(siteId, from, to));
    }

    @GetMapping("/missing")
    public ResponseEntity<List<MissingWorkerReport>> getMissingWorkers(
            @RequestParam UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reportService.getMissingWorkers(siteId, date));
    }

    @GetMapping("/attendance/export")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam UUID siteId,
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
}
