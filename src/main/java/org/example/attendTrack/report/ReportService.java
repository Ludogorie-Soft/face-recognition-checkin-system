package org.example.attendTrack.report;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.attendTrack.attendance.Attendance;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.attendance.AttendanceType;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.report.dto.AttendanceReportRow;
import org.example.attendTrack.report.dto.MissingWorkerReport;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> getAttendance(UUID siteId, LocalDate from, LocalDate to) {
        validateSiteExists(siteId);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        return attendanceRepository.findBySiteAndDateRange(siteId, start, end).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MissingWorkerReport> getMissingWorkers(UUID siteId, LocalDate date) {
        validateSiteExists(siteId);

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Set<UUID> checkedIn = attendanceRepository
                .findBySiteAndDateRange(siteId, start, end).stream()
                .filter(a -> a.getType() == AttendanceType.CHECK_IN)
                .map(a -> a.getWorker().getId())
                .collect(Collectors.toSet());

        return siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> sw.getUser())
                .filter(w -> !checkedIn.contains(w.getId()))
                .map(w -> new MissingWorkerReport(w.getId(), w.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceToExcel(UUID siteId, LocalDate from, LocalDate to) {
        List<AttendanceReportRow> rows = getAttendance(siteId, from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Attendance");
            CellStyle headerStyle = buildHeaderStyle(workbook);

            writeHeader(sheet, headerStyle);
            writeRows(sheet, rows);

            for (int i = 0; i < 11; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Failed to generate Excel file");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeHeader(Sheet sheet, CellStyle style) {
        String[] headers = {
                "Worker ID", "Worker Name", "Site", "Date",
                "Type", "Recorded At", "Lat", "Lng", "Location Valid", "Face Confidence", "Manual Override"
        };
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeRows(Sheet sheet, List<AttendanceReportRow> rows) {
        int rowNum = 1;
        for (AttendanceReportRow r : rows) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(r.workerId().toString());
            row.createCell(1).setCellValue(r.workerName());
            row.createCell(2).setCellValue(r.siteName());
            row.createCell(3).setCellValue(r.date().toString());
            row.createCell(4).setCellValue(r.type().name());
            row.createCell(5).setCellValue(r.recordedAt().format(DT_FORMAT));
            row.createCell(6).setCellValue(r.lat());
            row.createCell(7).setCellValue(r.lng());
            row.createCell(8).setCellValue(r.locationValid());
            row.createCell(9).setCellValue(r.faceConfidence() != null ? r.faceConfidence() : 0);
            row.createCell(10).setCellValue(r.manualOverride());
        }
    }

    private CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private AttendanceReportRow toRow(Attendance a) {
        return new AttendanceReportRow(
                a.getWorker().getId(),
                a.getWorker().getName(),
                a.getSite().getName(),
                a.getRecordedAt().toLocalDate(),
                a.getType(),
                a.getRecordedAt(),
                a.getLat(),
                a.getLng(),
                a.isLocationValid(),
                a.getFaceConfidence(),
                a.isManualOverride()
        );
    }

    private void validateSiteExists(UUID siteId) {
        if (!siteRepository.existsById(siteId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId);
        }
    }
}
