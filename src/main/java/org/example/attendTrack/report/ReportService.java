package org.example.attendTrack.report;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.attendTrack.attendance.Attendance;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.attendance.AttendanceType;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.company.CompanyRepository;
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
    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<AttendanceReportRow> getAttendance(UUID siteId, UUID companyId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (siteId != null) validateSiteExists(siteId);
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atTime(LocalTime.of(6, 0));
        Set<UUID> companyWorkers = workerIdsForCompany(companyId);

        List<Attendance> records = siteId != null
                ? attendanceRepository.findBySiteAndDateRange(siteId, start, end)
                : attendanceRepository.findAllInDateRange(start, end);

        List<Attendance> filtered = records.stream()
                .filter(a -> companyWorkers == null || companyWorkers.contains(a.getWorker().getId()))
                .toList();

        Map<UUID, String> companyNames = workerCompanyNames(
                filtered.stream().map(a -> a.getWorker().getId()).distinct().toList());

        return filtered.stream()
                .map(a -> toRow(a, companyNames.get(a.getWorker().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MissingWorkerReport> getMissingWorkers(UUID siteId, UUID companyId, LocalDate date) {
        validateSiteExists(siteId);

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Set<UUID> companyWorkers = workerIdsForCompany(companyId);

        Set<UUID> checkedIn = attendanceRepository
                .findBySiteAndDateRange(siteId, start, end).stream()
                .filter(a -> a.getType() == AttendanceType.CHECK_IN)
                .map(a -> a.getWorker().getId())
                .collect(Collectors.toSet());

        return siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> sw.getUser())
                .filter(w -> !checkedIn.contains(w.getId()))
                .filter(w -> companyWorkers == null || companyWorkers.contains(w.getId()))
                .map(w -> new MissingWorkerReport(w.getId(), w.getName()))
                .sorted(Comparator.comparing(MissingWorkerReport::workerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportAttendanceToExcel(UUID siteId, LocalDate from, LocalDate to) {
        List<AttendanceReportRow> rows = getAttendance(siteId, null, from, to);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Присъствие");
            CellStyle headerStyle = buildHeaderStyle(workbook);

            writeHeader(sheet, headerStyle);
            writeRows(sheet, rows);

            for (int i = 0; i < 17; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Failed to generate Excel file");
        }
    }

    // ── Worked Hours ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WorkedHoursRow> getWorkedHours(UUID siteId, UUID companyId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        if (siteId != null) validateSiteExists(siteId);
        Set<UUID> companyWorkers = workerIdsForCompany(companyId);

        List<Attendance> allRecords = siteId != null
                ? attendanceRepository.findBySiteAndDateRange(siteId, from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.of(6, 0)))
                : attendanceRepository.findAllInDateRange(from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.of(6, 0)));

        List<Attendance> records = allRecords.stream()
                .filter(a -> companyWorkers == null || companyWorkers.contains(a.getWorker().getId()))
                .toList();

        List<HoursCorrection> correctionList = siteId != null
                ? hoursCorrectionRepository.findBySiteAndPeriod(siteId, from, to)
                : hoursCorrectionRepository.findAllInPeriod(from, to);

        Map<CorrectionKey, HoursCorrection> corrections = correctionList.stream()
                .collect(Collectors.toMap(
                        c -> new CorrectionKey(c.getWorker().getId(), c.getSite().getId(), c.getDate()),
                        c -> c
                ));

        Map<UUID, String> companyNames = workerCompanyNames(
                records.stream().map(a -> a.getWorker().getId()).distinct().toList());

        return buildWorkedHoursRows(records, corrections, companyNames);
    }

    @Transactional(readOnly = true)
    public List<WorkedHoursSummaryRow> getWorkedHoursSummary(UUID companyId, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        Set<UUID> companyWorkers = workerIdsForCompany(companyId);
        List<Attendance> records = attendanceRepository.findAllInDateRange(
                from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.of(6, 0)))
                .stream()
                .filter(a -> companyWorkers == null || companyWorkers.contains(a.getWorker().getId()))
                .toList();

        Map<CorrectionKey, HoursCorrection> corrections = hoursCorrectionRepository
                .findAllInPeriod(from, to).stream()
                .collect(Collectors.toMap(
                        c -> new CorrectionKey(c.getWorker().getId(), c.getSite().getId(), c.getDate()),
                        c -> c
                ));

        Map<UUID, String> companyNames = workerCompanyNames(
                records.stream().map(a -> a.getWorker().getId()).distinct().toList());

        List<WorkedHoursRow> rows = buildWorkedHoursRows(records, corrections, companyNames);

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
        List<WorkedHoursRow> rows = getWorkedHours(siteId, null, from, to);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Отработени часове");
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle warningStyle = buildWarningStyle(workbook);

            String[] headers = {"Работник", "Фирма", "Обект", "Дата", "Начало", "Край",
                                 "Часове", "Коригирани часове", "Бележка",
                                 "Локация при влизане", "Локация при излизане", "Аномалия", "Офлайн"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle totalStyle = buildTotalStyle(workbook);
            int rowNum = 1;
            for (WorkedHoursRow r : rows) {
                Row row = sheet.createRow(rowNum++);
                boolean isTotalRow = r.pairIndex() == -1;
                boolean openShift = !isTotalRow && r.checkOut() == null && !r.inferredCheckOut();

                row.createCell(0).setCellValue(r.workerName());
                row.createCell(1).setCellValue(r.companyName() != null ? r.companyName() : "");
                row.createCell(2).setCellValue(r.siteName());
                row.createCell(3).setCellValue(r.date().toString());

                if (isTotalRow) {
                    Cell startCell = row.createCell(4);
                    startCell.setCellValue("ОБЩО");
                    startCell.setCellStyle(totalStyle);
                    row.createCell(5).setCellValue("");
                    Cell hoursCell = row.createCell(6);
                    hoursCell.setCellValue(r.effectiveHours() != null ? r.effectiveHours() : 0.0);
                    hoursCell.setCellStyle(totalStyle);
                    if (r.correctedHours() != null) row.createCell(7).setCellValue(r.correctedHours());
                    if (r.correctionNote() != null) row.createCell(8).setCellValue(r.correctionNote());
                } else {
                    row.createCell(4).setCellValue(r.checkIn() != null ? r.checkIn().format(TIME_FORMAT) : "");
                    Cell checkOutCell = row.createCell(5);
                    if (openShift) {
                        checkOutCell.setCellValue("ОТВОРЕНА СМЯНА");
                        checkOutCell.setCellStyle(warningStyle);
                    } else {
                        String checkOutStr = r.checkOut().format(TIME_FORMAT);
                        if (r.autoCheckout()) checkOutStr += " (AUTO)";
                        else if (r.inferredCheckOut()) checkOutStr += " (→)";
                        checkOutCell.setCellValue(checkOutStr);
                    }
                    row.createCell(6).setCellValue(r.calculatedHours() != null ? r.calculatedHours() : 0.0);
                    if (r.correctedHours() != null) row.createCell(7).setCellValue(r.correctedHours());
                    if (r.correctionNote() != null) row.createCell(8).setCellValue(r.correctionNote());
                    if (r.checkInLat() != null) {
                        row.createCell(9).setCellValue(
                                "https://www.google.com/maps?q=" + r.checkInLat() + "," + r.checkInLng());
                    }
                    if (r.checkOutLat() != null) {
                        row.createCell(10).setCellValue(
                                "https://www.google.com/maps?q=" + r.checkOutLat() + "," + r.checkOutLng());
                    }
                    if (r.anomalyReason() != null) row.createCell(11).setCellValue(anomalyReasonBg(r.anomalyReason()));
                    if (r.offline()) row.createCell(12).setCellValue("ДА");
                }
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
        List<WorkedHoursSummaryRow> rows = getWorkedHoursSummary(null, from, to);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Обобщение часове");
            CellStyle headerStyle = buildHeaderStyle(workbook);

            String[] headers = {"Работник", "Фирма", "Обект", "Дата", "Начало", "Край",
                                 "Часове", "Коригирани часове", "Бележка",
                                 "Локация при влизане", "Локация при излизане", "Аномалия", "Офлайн"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle totalStyle = buildTotalStyle(workbook);
            CellStyle warningStyle = buildWarningStyle(workbook);
            int rowNum = 1;
            for (WorkedHoursSummaryRow worker : rows) {
                for (WorkedHoursRow d : worker.details()) {
                    Row row = sheet.createRow(rowNum++);
                    boolean isTotalRow = d.pairIndex() == -1;
                    boolean openShift = !isTotalRow && d.checkOut() == null && !d.inferredCheckOut();

                    row.createCell(0).setCellValue(worker.workerName());
                    row.createCell(1).setCellValue(d.companyName() != null ? d.companyName() : "");
                    row.createCell(2).setCellValue(d.siteName());
                    row.createCell(3).setCellValue(d.date().toString());

                    if (isTotalRow) {
                        Cell startCell = row.createCell(4);
                        startCell.setCellValue("ОБЩО");
                        startCell.setCellStyle(totalStyle);
                        row.createCell(5).setCellValue("");
                        Cell hoursCell = row.createCell(6);
                        hoursCell.setCellValue(d.effectiveHours() != null ? d.effectiveHours() : 0.0);
                        hoursCell.setCellStyle(totalStyle);
                        if (d.correctedHours() != null) row.createCell(7).setCellValue(d.correctedHours());
                        if (d.correctionNote() != null) row.createCell(8).setCellValue(d.correctionNote());
                    } else {
                        row.createCell(4).setCellValue(d.checkIn() != null ? d.checkIn().format(TIME_FORMAT) : "");
                        Cell checkOutCell = row.createCell(5);
                        if (openShift) {
                            checkOutCell.setCellValue("ОТВОРЕНА СМЯНА");
                            checkOutCell.setCellStyle(warningStyle);
                        } else {
                            String checkOutStr = d.checkOut().format(TIME_FORMAT);
                            if (d.autoCheckout()) checkOutStr += " (AUTO)";
                            else if (d.inferredCheckOut()) checkOutStr += " (→)";
                            checkOutCell.setCellValue(checkOutStr);
                        }
                        row.createCell(6).setCellValue(d.calculatedHours() != null ? d.calculatedHours() : 0.0);
                        if (d.correctedHours() != null) row.createCell(7).setCellValue(d.correctedHours());
                        if (d.correctionNote() != null) row.createCell(8).setCellValue(d.correctionNote());
                        if (d.checkInLat() != null) {
                            row.createCell(9).setCellValue(
                                    "https://www.google.com/maps?q=" + d.checkInLat() + "," + d.checkInLng());
                        }
                        if (d.checkOutLat() != null) {
                            row.createCell(10).setCellValue(
                                    "https://www.google.com/maps?q=" + d.checkOutLat() + "," + d.checkOutLng());
                        }
                        if (d.anomalyReason() != null) row.createCell(11).setCellValue(anomalyReasonBg(d.anomalyReason()));
                        if (d.offline()) row.createCell(12).setCellValue("ДА");
                    }
                }
                // Grand-total row per worker
                Row totalRow = sheet.createRow(rowNum++);
                Cell totalLabelCell = totalRow.createCell(0);
                totalLabelCell.setCellValue(worker.workerName() + " — ОБЩО");
                totalLabelCell.setCellStyle(totalStyle);
                Cell totalHoursCell = totalRow.createCell(6);
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
                "ID работник", "Работник", "Фирма", "Обект", "Дата",
                "Тип", "Записано в", "Ширина", "Дължина", "Валидна локация", "Разпознаване на лице", "Ръчно въведено",
                "Източник", "Офлайн", "IP адрес", "Устройство", "Аномалия"
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
            row.createCell(2).setCellValue(r.companyName() != null ? r.companyName() : "");
            row.createCell(3).setCellValue(r.siteName());
            row.createCell(4).setCellValue(r.date().toString());
            row.createCell(5).setCellValue(r.type().name());
            row.createCell(6).setCellValue(r.recordedAt().format(DT_FORMAT));
            row.createCell(7).setCellValue(r.lat());
            row.createCell(8).setCellValue(r.lng());
            row.createCell(9).setCellValue(r.locationValid());
            row.createCell(10).setCellValue(r.faceConfidence() != null ? r.faceConfidence() : 0);
            row.createCell(11).setCellValue(r.manualOverride());
            row.createCell(12).setCellValue(r.source() != null ? r.source() : "");
            row.createCell(13).setCellValue(r.createdOffline());
            row.createCell(14).setCellValue(r.ipAddress() != null ? r.ipAddress() : "");
            row.createCell(15).setCellValue(r.clientDeviceId() != null ? r.clientDeviceId() : "");
            row.createCell(16).setCellValue(r.anomalyReason() != null ? r.anomalyReason() : "");
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

    private AttendanceReportRow toRow(Attendance a, String companyName) {
        return new AttendanceReportRow(
                a.getId(),
                a.getWorker().getId(),
                a.getWorker().getName(),
                companyName,
                a.getSite().getName(),
                a.getRecordedAt().toLocalDate(),
                a.getType(),
                a.getRecordedAt(),
                a.getLat(),
                a.getLng(),
                a.isLocationValid(),
                a.getFaceConfidence(),
                a.isManualOverride(),
                a.isManualOverride() && a.getManager() != null,
                a.getSource() != null ? a.getSource().name() : null,
                a.getIpAddress(),
                a.getClientDeviceId(),
                a.isCreatedOffline(),
                a.getAnomalyReason() != null ? a.getAnomalyReason().name() : null
        );
    }

    private Map<UUID, String> workerCompanyNames(List<UUID> workerIds) {
        if (workerIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, String> result = new HashMap<>();
        companyRepository.findWorkerCompanyPairs(workerIds).forEach(row -> {
            UUID wId = (UUID) row[0];
            result.putIfAbsent(wId, (String) row[2]); // first company alphabetically
        });
        return result;
    }

    private Set<UUID> workerIdsForCompany(UUID companyId) {
        if (companyId == null) return null;
        return companyRepository.findWorkerIdsByCompanyId(companyId);
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
        if (from.until(to).toTotalMonths() >= 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Date range must not exceed 12 months");
        }
    }

    /**
     * Pairs CHECK_IN / CHECK_OUT records per (worker, site, date) and builds WorkedHoursRow list.
     *
     * Each sequential IN/OUT pair within a shift-day becomes its own session row (pairIndex ≥ 0).
     * When a worker has more than one session on the same day at the same site, an extra
     * day-total row (pairIndex = -1) is appended that holds the sum of all session hours
     * and any day-level correction. Session rows in a multi-session day have effectiveHours = null
     * so that summary totals (which sum effectiveHours) do not double-count.
     *
     * Open shifts (CHECK_IN without subsequent CHECK_OUT) are included with null checkOut/hours.
     */
    private List<WorkedHoursRow> buildWorkedHoursRows(
            List<Attendance> records,
            Map<CorrectionKey, HoursCorrection> corrections,
            Map<UUID, String> companyNames) {

        record GroupKey(UUID workerId, UUID siteId, LocalDate date) {}

        Map<GroupKey, List<Attendance>> grouped = records.stream()
                .collect(Collectors.groupingBy(a ->
                        new GroupKey(a.getWorker().getId(), a.getSite().getId(),
                                normalizeShiftDate(a.getRecordedAt()))));

        List<WorkedHoursRow> rows = new ArrayList<>();

        for (Map.Entry<GroupKey, List<Attendance>> entry : grouped.entrySet()) {
            GroupKey key = entry.getKey();
            String companyName = companyNames.get(key.workerId());
            List<Attendance> dayRecords = entry.getValue().stream()
                    .sorted(Comparator.comparing(Attendance::getRecordedAt))
                    .toList();

            // ── Sequential pairing ────────────────────────────────────────────
            List<WorkedHoursRow> sessionRows = new ArrayList<>();
            Attendance openIn = null;

            for (Attendance a : dayRecords) {
                if (a.getType() == AttendanceType.CHECK_IN) {
                    if (openIn != null) {
                        // Consecutive CHECK_IN without a CHECK_OUT: emit as open shift
                        sessionRows.add(sessionRow(key.date(), openIn, null, false, false, null, sessionRows.size(), companyName));
                    }
                    openIn = a;
                } else { // CHECK_OUT
                    if (openIn != null) {
                        long minutes = Duration.between(openIn.getRecordedAt(), a.getRecordedAt()).toMinutes();
                        // Prefer the explicit source; fall back to the legacy heuristic for pre-V10 rows.
                        boolean auto = a.getSource() != null
                                ? a.getSource() == org.example.attendTrack.attendance.AttendanceSource.SCHEDULER_AUTO
                                : (a.isManualOverride() && a.getManager() == null);
                        sessionRows.add(sessionRow(key.date(), openIn, a, false, auto, roundToQuarter(minutes), sessionRows.size(), companyName));
                        openIn = null;
                    }
                    // Orphan CHECK_OUT (no preceding open CHECK_IN): ignore
                }
            }

            // Handle the last open CHECK_IN: try inferred checkout from another site, else open shift
            if (openIn != null) {
                final Attendance finalOpenIn = openIn;
                Optional<LocalDateTime> nextSiteIn = records.stream()
                        .filter(a -> a.getWorker().getId().equals(key.workerId())
                                && !a.getSite().getId().equals(key.siteId())
                                && normalizeShiftDate(a.getRecordedAt()).equals(key.date())
                                && a.getType() == AttendanceType.CHECK_IN
                                && a.getRecordedAt().isAfter(finalOpenIn.getRecordedAt()))
                        .map(Attendance::getRecordedAt)
                        .min(Comparator.naturalOrder());

                if (nextSiteIn.isPresent()) {
                    long minutes = Duration.between(openIn.getRecordedAt(), nextSiteIn.get()).toMinutes();
                    boolean hasRealCheckInCoords = openIn.getLat() != 0.0 || openIn.getLng() != 0.0;
                    sessionRows.add(new WorkedHoursRow(
                            openIn.getId(), null,  // inferred checkout — no real CHECK_OUT record
                            openIn.getWorker().getId(), openIn.getWorker().getName(), companyName,
                            openIn.getSite().getId(), openIn.getSite().getName(),
                            key.date(), sessionRows.size(),
                            openIn.getRecordedAt().toLocalTime(),
                            nextSiteIn.get().toLocalTime(),
                            true, false, roundToQuarter(minutes), null, null, null,
                            null, null,  // inferred checkout — no real CHECK_OUT coords
                            hasRealCheckInCoords ? openIn.getLat() : null,
                            hasRealCheckInCoords ? openIn.getLng() : null,
                            hasRealCheckInCoords ? openIn.isLocationValid() : null,
                            null,  // inferred checkout — no locationValid
                            openIn.isManualOverride() && openIn.getManager() != null,
                            anomalyReasonOf(openIn),
                            offlineOf(openIn)));
                } else {
                    sessionRows.add(sessionRow(key.date(), openIn, null, false, false, null, sessionRows.size(), companyName));
                }
            }

            if (sessionRows.isEmpty()) continue;

            // ── Apply day-level correction ────────────────────────────────────
            CorrectionKey corrKey = new CorrectionKey(key.workerId(), key.siteId(), key.date());
            HoursCorrection correction = corrections.get(corrKey);
            Double correctedHours = correction != null ? correction.getCorrectedHours() : null;
            String corrNote = correction != null ? correction.getNote() : null;

            if (sessionRows.size() == 1) {
                // Single session: embed correction directly on the row
                WorkedHoursRow s = sessionRows.get(0);
                boolean openShift = s.checkOut() == null && !s.inferredCheckOut();
                Double effectiveHours = openShift ? null
                        : (correctedHours != null ? correctedHours : s.calculatedHours());
                rows.add(new WorkedHoursRow(
                        s.checkInId(), s.checkOutId(),
                        s.workerId(), s.workerName(), s.companyName(), s.siteId(), s.siteName(), s.date(),
                        0, s.checkIn(), s.checkOut(), s.inferredCheckOut(), s.autoCheckout(),
                        s.calculatedHours(), effectiveHours, correctedHours, corrNote,
                        s.checkOutLat(), s.checkOutLng(), s.checkInLat(), s.checkInLng(),
                        s.checkInLocationValid(), s.checkOutLocationValid(),
                        s.adminManualCheckIn(), s.anomalyReason(), s.offline()));
            } else {
                // Multiple sessions: session rows carry no effectiveHours (prevents double-counting);
                // a day-total row (pairIndex = -1) carries the sum and any correction.
                rows.addAll(sessionRows);

                double daySum = sessionRows.stream()
                        .filter(r -> r.calculatedHours() != null)
                        .mapToDouble(WorkedHoursRow::calculatedHours)
                        .sum();
                daySum = Math.round(daySum * 100.0) / 100.0;
                Double effectiveDay = correctedHours != null ? correctedHours : (daySum == 0 ? null : daySum);

                rows.add(new WorkedHoursRow(
                        null, null,  // day-total row — no individual record IDs
                        key.workerId(), sessionRows.get(0).workerName(), companyName,
                        key.siteId(), sessionRows.get(0).siteName(),
                        key.date(), -1,
                        null, null, false, false,
                        daySum == 0 ? null : daySum, effectiveDay,
                        correctedHours, corrNote,
                        null, null, null, null,    // day-total row — no location fields
                        null, null,                // day-total row — no locationValid
                        false,                     // day-total row — no individual check-in
                        null,                      // day-total row — no anomaly
                        false));                   // day-total row — no offline flag
            }
        }

        // pairIndex == -1 (day-total) must sort AFTER session rows (≥0) for the same day.
        // Replace -1 with Integer.MAX_VALUE for ordering purposes only.
        rows.sort(Comparator.comparing(WorkedHoursRow::workerName)
                .thenComparing(WorkedHoursRow::siteName)
                .thenComparing(WorkedHoursRow::date)
                .thenComparingInt(r -> r.pairIndex() == -1 ? Integer.MAX_VALUE : r.pairIndex()));
        return rows;
    }

    private static WorkedHoursRow sessionRow(
            LocalDate shiftDate, Attendance checkIn, Attendance checkOut,
            boolean inferredCheckOut, boolean autoCheckout,
            Double calculatedHours, int pairIdx, String companyName) {
        // Check-in coordinates: null when record was created manually (0,0 coords).
        boolean hasRealCheckInCoords = checkIn.getLat() != 0.0 || checkIn.getLng() != 0.0;
        Double ciLat = hasRealCheckInCoords ? checkIn.getLat() : null;
        Double ciLng = hasRealCheckInCoords ? checkIn.getLng() : null;
        // Checkout coordinates: only from a real CHECK_OUT record (not inferred),
        // and only when non-zero (0.0 means the record was created manually without GPS).
        boolean hasRealCheckOutCoords = checkOut != null && !inferredCheckOut
                && (checkOut.getLat() != 0.0 || checkOut.getLng() != 0.0);
        Double coLat = hasRealCheckOutCoords ? checkOut.getLat() : null;
        Double coLng = hasRealCheckOutCoords ? checkOut.getLng() : null;
        Boolean ciLocationValid = hasRealCheckInCoords ? checkIn.isLocationValid() : null;
        Boolean coLocationValid = hasRealCheckOutCoords ? checkOut.isLocationValid() : null;
        boolean adminManualCheckIn = checkIn.isManualOverride() && checkIn.getManager() != null;
        return new WorkedHoursRow(
                checkIn.getId(),
                checkOut != null && !inferredCheckOut ? checkOut.getId() : null,
                checkIn.getWorker().getId(), checkIn.getWorker().getName(), companyName,
                checkIn.getSite().getId(), checkIn.getSite().getName(),
                shiftDate, pairIdx,
                checkIn.getRecordedAt().toLocalTime(),
                checkOut != null ? checkOut.getRecordedAt().toLocalTime() : null,
                inferredCheckOut, autoCheckout,
                calculatedHours, null, null, null,
                coLat, coLng, ciLat, ciLng,
                ciLocationValid, coLocationValid,
                adminManualCheckIn,
                anomalyReasonOf(checkIn, checkOut),
                offlineOf(checkIn, checkOut));
    }

    /** First non-null anomaly reason among the given records (as its enum name), else null. */
    private static String anomalyReasonOf(Attendance... records) {
        for (Attendance a : records) {
            if (a != null && a.getAnomalyReason() != null) return a.getAnomalyReason().name();
        }
        return null;
    }

    /** Bulgarian label for an anomaly reason, for Excel export. */
    private static String anomalyReasonBg(String reason) {
        if (reason == null) return "";
        return switch (reason) {
            case "DUPLICATE_CHECK_IN" -> "Двойно влизане";
            case "DUPLICATE_CHECK_OUT" -> "Двойно излизане";
            case "CHECKOUT_WITHOUT_CHECKIN" -> "Излизане без влизане";
            default -> reason;
        };
    }

    /** True when any of the given records was recorded on an offline device. */
    private static boolean offlineOf(Attendance... records) {
        for (Attendance a : records) {
            if (a != null && a.isCreatedOffline()) return true;
        }
        return false;
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
