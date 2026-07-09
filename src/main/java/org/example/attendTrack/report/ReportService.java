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
import org.example.attendTrack.report.dto.HoursCorrectionRequest;
import org.example.attendTrack.report.dto.MissingWorkerReport;
import org.example.attendTrack.report.dto.WorkedHoursRow;
import org.example.attendTrack.report.dto.WorkedHoursSummaryRow;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRepository attendanceRepository;
    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final HoursCorrectionRepository hoursCorrectionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> getAttendance(UUID siteId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
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

    // ── Worked Hours ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkedHoursRow> getWorkedHours(UUID siteId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        validateSiteExists(siteId);
        List<Attendance> records = attendanceRepository.findBySiteAndDateRange(
                siteId, from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.of(6, 0)));

        Map<CorrectionKey, HoursCorrection> corrections = hoursCorrectionRepository
                .findBySiteAndPeriod(siteId, from, to).stream()
                .collect(Collectors.toMap(
                        c -> new CorrectionKey(c.getWorker().getId(), c.getSite().getId(), c.getDate()),
                        c -> c
                ));

        return buildWorkedHoursRows(records, corrections);
    }

    @Transactional(readOnly = true)
    public List<WorkedHoursSummaryRow> getWorkedHoursSummary(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<Attendance> records = attendanceRepository.findAllInDateRange(
                from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.of(6, 0)));

        Map<CorrectionKey, HoursCorrection> corrections = hoursCorrectionRepository
                .findAllInPeriod(from, to).stream()
                .collect(Collectors.toMap(
                        c -> new CorrectionKey(c.getWorker().getId(), c.getSite().getId(), c.getDate()),
                        c -> c
                ));

        List<WorkedHoursRow> rows = buildWorkedHoursRows(records, corrections);

        return rows.stream()
                .collect(Collectors.groupingBy(WorkedHoursRow::workerId))
                .entrySet().stream()
                .map(entry -> {
                    List<WorkedHoursRow> workerRows = entry.getValue();
                    double total = workerRows.stream()
                            .mapToDouble(r -> r.effectiveHours() != null ? r.effectiveHours() : 0.0)
                            .sum();
                    total = Math.round(total * 100.0) / 100.0;
                    return new WorkedHoursSummaryRow(
                            entry.getKey(),
                            workerRows.get(0).workerName(),
                            workerRows,
                            total
                    );
                })
                .sorted(Comparator.comparing(WorkedHoursSummaryRow::workerName))
                .toList();
    }

    @Transactional
    public void saveHoursCorrection(HoursCorrectionRequest req, User admin) {
        User worker = userRepository.findById(req.workerId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                        "Worker not found: " + req.workerId()));
        Site site = siteRepository.findById(req.siteId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND,
                        "Site not found: " + req.siteId()));

        HoursCorrection correction = hoursCorrectionRepository
                .findByWorkerIdAndSiteIdAndDate(req.workerId(), req.siteId(), req.date())
                .orElse(null);

        if (correction == null) {
            hoursCorrectionRepository.save(HoursCorrection.builder()
                    .worker(worker)
                    .site(site)
                    .date(req.date())
                    .correctedHours(req.correctedHours())
                    .note(req.note())
                    .createdBy(admin)
                    .build());
        } else {
            correction.update(req.correctedHours(), req.note(), admin);
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportWorkedHoursToExcel(UUID siteId, LocalDate from, LocalDate to) {
        List<WorkedHoursRow> rows = getWorkedHours(siteId, from, to);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Worked Hours");
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle warningStyle = buildWarningStyle(workbook);

            String[] headers = {"Worker", "Site", "Date", "Check-In", "Check-Out",
                                 "Hours", "Corrected Hours", "Note"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (WorkedHoursRow r : rows) {
                Row row = sheet.createRow(rowNum++);
                boolean openShift = r.checkOut() == null;

                row.createCell(0).setCellValue(r.workerName());
                row.createCell(1).setCellValue(r.siteName());
                row.createCell(2).setCellValue(r.date().toString());
                row.createCell(3).setCellValue(r.checkIn() != null ? r.checkIn().format(TIME_FORMAT) : "");
                Cell checkOutCell = row.createCell(4);
                if (openShift) {
                    checkOutCell.setCellValue("OPEN SHIFT");
                    checkOutCell.setCellStyle(warningStyle);
                } else {
                    checkOutCell.setCellValue(r.checkOut().format(TIME_FORMAT));
                }
                row.createCell(5).setCellValue(r.calculatedHours() != null ? r.calculatedHours() : 0.0);
                if (r.correctedHours() != null) row.createCell(6).setCellValue(r.correctedHours());
                if (r.correctionNote() != null) row.createCell(7).setCellValue(r.correctionNote());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "Failed to generate Excel file");
        }
    }

    @Transactional(readOnly = true)
    public byte[] exportWorkedHoursSummaryToExcel(LocalDate from, LocalDate to) {
        List<WorkedHoursSummaryRow> rows = getWorkedHoursSummary(from, to);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Hours Summary");
            CellStyle headerStyle = buildHeaderStyle(workbook);

            String[] headers = {"Worker", "Site", "Date", "Check-In", "Check-Out",
                                 "Hours", "Corrected Hours", "Note"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle totalStyle = buildTotalStyle(workbook);
            int rowNum = 1;
            for (WorkedHoursSummaryRow worker : rows) {
                for (WorkedHoursRow d : worker.details()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(worker.workerName());
                    row.createCell(1).setCellValue(d.siteName());
                    row.createCell(2).setCellValue(d.date().toString());
                    row.createCell(3).setCellValue(d.checkIn() != null ? d.checkIn().format(TIME_FORMAT) : "");
                    row.createCell(4).setCellValue(d.checkOut() != null ? d.checkOut().format(TIME_FORMAT) : "OPEN SHIFT");
                    row.createCell(5).setCellValue(d.calculatedHours() != null ? d.calculatedHours() : 0.0);
                    if (d.correctedHours() != null) row.createCell(6).setCellValue(d.correctedHours());
                    if (d.correctionNote() != null) row.createCell(7).setCellValue(d.correctionNote());
                }
                // Total row per worker
                Row totalRow = sheet.createRow(rowNum++);
                Cell totalLabelCell = totalRow.createCell(0);
                totalLabelCell.setCellValue(worker.workerName() + " — TOTAL");
                totalLabelCell.setCellStyle(totalStyle);
                Cell totalHoursCell = totalRow.createCell(5);
                totalHoursCell.setCellValue(worker.totalHours());
                totalHoursCell.setCellStyle(totalStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                    "Failed to generate Excel file");
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

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Date 'from' must not be after 'to'");
        }
        if (from.until(to).toTotalMonths() > 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Date range must not exceed 12 months");
        }
    }

    /**
     * Pairs CHECK_IN / CHECK_OUT records per (worker, site, date) and builds WorkedHoursRow list.
     * Open shifts (CHECK_IN without subsequent CHECK_OUT) are included with null checkOut/hours.
     */
    private List<WorkedHoursRow> buildWorkedHoursRows(
            List<Attendance> records,
            Map<CorrectionKey, HoursCorrection> corrections) {

        record GroupKey(UUID workerId, UUID siteId, LocalDate date) {}

        Map<GroupKey, List<Attendance>> grouped = records.stream()
                .collect(Collectors.groupingBy(a ->
                        new GroupKey(a.getWorker().getId(), a.getSite().getId(),
                                normalizeShiftDate(a.getRecordedAt()))));

        List<WorkedHoursRow> rows = new ArrayList<>();

        for (Map.Entry<GroupKey, List<Attendance>> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            List<Attendance> dayRecords = entry.getValue();

            Optional<Attendance> checkIn = dayRecords.stream()
                    .filter(a -> a.getType() == AttendanceType.CHECK_IN)
                    .min(Comparator.comparing(Attendance::getRecordedAt));

            if (checkIn.isEmpty()) continue; // no CHECK_IN — skip anomaly

            Optional<Attendance> checkOut = dayRecords.stream()
                    .filter(a -> a.getType() == AttendanceType.CHECK_OUT
                            && a.getRecordedAt().isAfter(checkIn.get().getRecordedAt()))
                    .min(Comparator.comparing(Attendance::getRecordedAt));

            LocalTime checkInTime = checkIn.get().getRecordedAt().toLocalTime();
            LocalTime checkOutTime = null;
            LocalDateTime inferredEndDt = null;
            boolean inferredCheckOut = false;

            if (checkOut.isPresent()) {
                checkOutTime = checkOut.get().getRecordedAt().toLocalTime();
            } else {
                // AUTO-CLOSE: worker moved to another site without checking out.
                // Find the earliest CHECK_IN at a different site on the same shift-day,
                // occurring after the current CHECK_IN. This is only effective when
                // 'records' contains data from multiple sites (summary mode).
                Optional<LocalDateTime> nextSiteCheckIn = records.stream()
                        .filter(a -> a.getWorker().getId().equals(key.workerId())
                                && !a.getSite().getId().equals(key.siteId())
                                && normalizeShiftDate(a.getRecordedAt()).equals(key.date())
                                && a.getType() == AttendanceType.CHECK_IN
                                && a.getRecordedAt().isAfter(checkIn.get().getRecordedAt()))
                        .map(Attendance::getRecordedAt)
                        .min(Comparator.naturalOrder());

                if (nextSiteCheckIn.isPresent()) {
                    checkOutTime = nextSiteCheckIn.get().toLocalTime();
                    inferredEndDt = nextSiteCheckIn.get();
                    inferredCheckOut = true;
                }
            }

            Double calculatedHours = null;
            if (checkOutTime != null && !inferredCheckOut && checkOut.isPresent()) {
                long minutes = Duration.between(
                        checkIn.get().getRecordedAt(), checkOut.get().getRecordedAt()).toMinutes();
                calculatedHours = roundToQuarter(minutes);
            } else if (checkOutTime != null && inferredCheckOut) {
                // Use the actual next-site CHECK_IN datetime to avoid negative duration
                // when the inferred checkout crosses midnight into the next day.
                long minutes = Duration.between(checkIn.get().getRecordedAt(), inferredEndDt).toMinutes();
                calculatedHours = roundToQuarter(minutes);
            }

            CorrectionKey corrKey = new CorrectionKey(key.workerId(), key.siteId(), key.date());
            HoursCorrection correction = corrections.get(corrKey);
            Double correctedHours = correction != null ? correction.getCorrectedHours() : null;
            Double effectiveHours = correctedHours != null ? correctedHours : calculatedHours;

            rows.add(new WorkedHoursRow(
                    key.workerId(),
                    checkIn.get().getWorker().getName(),
                    key.siteId(),
                    checkIn.get().getSite().getName(),
                    key.date(),
                    checkInTime,
                    checkOutTime,
                    inferredCheckOut,
                    calculatedHours,
                    effectiveHours,
                    correctedHours,
                    correction != null ? correction.getNote() : null
            ));
        }

        rows.sort(Comparator.comparing(WorkedHoursRow::workerName)
                .thenComparing(WorkedHoursRow::siteName)
                .thenComparing(WorkedHoursRow::date));
        return rows;
    }

    /** Rounds total minutes to the nearest quarter-hour (0.25h increments). */
    private static double roundToQuarter(long totalMinutes) {
        return Math.round(totalMinutes / 15.0) * 0.25;
    }

    /**
     * Normalizes a timestamp to a "shift date": records between 00:00 and 06:00
     * are attributed to the previous day's shift to handle overnight shifts.
     */
    private static LocalDate normalizeShiftDate(LocalDateTime dt) {
        return dt.toLocalTime().isBefore(LocalTime.of(6, 0))
                ? dt.toLocalDate().minusDays(1)
                : dt.toLocalDate();
    }

    private CellStyle buildWarningStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.ORANGE.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle buildTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /** Simple value type used as map key for correction lookups. */
    private record CorrectionKey(UUID workerId, UUID siteId, LocalDate date) {}
}
