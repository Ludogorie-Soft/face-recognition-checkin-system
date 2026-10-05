package org.example.attendTrack.report;

import org.example.attendTrack.attendance.Attendance;
import org.example.attendTrack.attendance.AttendanceRepository;
import org.example.attendTrack.attendance.AttendanceSource;
import org.example.attendTrack.attendance.AttendanceType;
import org.example.attendTrack.company.Company;
import org.example.attendTrack.company.CompanyRepository;
import org.example.attendTrack.report.dto.MissingWorkerReport;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorker;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Missing workers: assigned, but no check-in on the day.
 *
 * <p>Without a site the question is "who is not at work today", so a worker counts as present if
 * they checked in at any of their sites, and is listed once however many sites they are on.
 */
class MissingWorkersReportTest {

    private AttendanceRepository attendanceRepository;
    private SiteRepository siteRepository;
    private SiteWorkerRepository siteWorkerRepository;
    private CompanyRepository companyRepository;
    private ReportService service;

    private Site siteA;
    private Site siteB;
    private User ivan;    // on both sites, came to A
    private User petar;   // on both sites, came nowhere
    private User maria;   // on B only, came nowhere

    private final LocalDate day = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        siteRepository = mock(SiteRepository.class);
        siteWorkerRepository = mock(SiteWorkerRepository.class);
        companyRepository = mock(CompanyRepository.class);

        service = new ReportService(attendanceRepository, siteRepository, siteWorkerRepository,
                mock(HoursCorrectionRepository.class), mock(UserRepository.class), companyRepository);

        siteA = Site.builder().id(UUID.randomUUID()).name("Ангел Кънчев")
                .lat(43.21).lng(23.54).radiusMeters(10).build();
        siteB = Site.builder().id(UUID.randomUUID()).name("ОБЩИНСКИ ПЪТ")
                .lat(43.68).lng(24.27).radiusMeters(10).build();

        ivan = worker("Иван");
        petar = worker("Петър");
        maria = worker("Мария");

        SiteWorker ivanA = new SiteWorker(siteA, ivan);
        SiteWorker ivanB = new SiteWorker(siteB, ivan);
        SiteWorker petarA = new SiteWorker(siteA, petar);
        SiteWorker petarB = new SiteWorker(siteB, petar);
        SiteWorker mariaB = new SiteWorker(siteB, maria);

        when(siteRepository.existsById(any())).thenReturn(true);
        when(siteWorkerRepository.findBySiteId(siteA.getId())).thenReturn(List.of(ivanA, petarA));
        when(siteWorkerRepository.findBySiteId(siteB.getId())).thenReturn(List.of(ivanB, petarB, mariaB));
        when(siteWorkerRepository.findAllWithUser())
                .thenReturn(List.of(ivanA, petarA, ivanB, petarB, mariaB));

        Attendance ivanAtA = checkIn(ivan, siteA, day.atTime(7, 30));
        when(attendanceRepository.findAllInDateRange(any(), any())).thenReturn(List.of(ivanAtA));
        when(attendanceRepository.findBySiteAndDateRange(any(), any(), any())).thenReturn(List.of());
        when(attendanceRepository.findBySiteAndDateRange(siteA.getId(), day.atStartOfDay(),
                day.plusDays(1).atStartOfDay())).thenReturn(List.of(ivanAtA));
    }

    @Test
    void withoutASite_aWorkerWhoCameToAnyOfTheirSitesIsNotMissing() {
        assertThat(names(service.getMissingWorkers(null, null, day)))
                .containsExactly("Мария", "Петър");
    }

    @Test
    void withoutASite_eachMissingWorkerIsListedOnce_howeverManySitesTheyAreOn() {
        List<MissingWorkerReport> rows = service.getMissingWorkers(null, null, day);

        assertThat(rows).extracting(MissingWorkerReport::workerId)
                .containsExactly(maria.getId(), petar.getId());
    }

    @Test
    void withoutASite_theCompanyFilterStillApplies() {
        UUID companyId = UUID.randomUUID();
        Set<UUID> companyWorkers = Set.of(petar.getId());
        when(companyRepository.findWorkerIdsByCompanyId(companyId)).thenReturn(companyWorkers);

        assertThat(names(service.getMissingWorkers(null, companyId, day)))
                .containsExactly("Петър");
    }

    @Test
    void withASite_onlyThatSitesWorkersAndCheckInsCount() {
        assertThat(names(service.getMissingWorkers(siteA.getId(), null, day)))
                .containsExactly("Петър");

        // Ivan checked in at A, not B — the per-site view still reports him for B.
        assertThat(names(service.getMissingWorkers(siteB.getId(), null, day)))
                .containsExactly("Иван", "Мария", "Петър");
    }

    // ── Excel export ─────────────────────────────────────────────────────────────

    @Test
    void theExportListsTheSameWorkers_underALineNamingTheFilters() throws IOException {
        try (XSSFWorkbook workbook = open(service.exportMissingWorkersToExcel(null, null, day))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getSheetName()).isEqualTo("Липсващи работници");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Дата: 01.10.2026 · Обект: Всички обекти · Фирма: Всички фирми");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("№");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Работник");
            assertThat(sheet.getRow(3).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Мария");
            assertThat(sheet.getRow(4).getCell(0).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Петър");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
        }
    }

    @Test
    void theExportNamesTheSelectedSiteAndCompany_andAppliesBoth() throws IOException {
        UUID companyId = UUID.randomUUID();
        Set<UUID> companyWorkers = Set.of(petar.getId());
        when(companyRepository.findWorkerIdsByCompanyId(companyId)).thenReturn(companyWorkers);
        when(companyRepository.findById(companyId))
                .thenReturn(Optional.of(Company.builder().id(companyId).name("Гарант 90").build()));
        when(siteRepository.findById(siteA.getId())).thenReturn(Optional.of(siteA));

        try (XSSFWorkbook workbook = open(service.exportMissingWorkersToExcel(siteA.getId(), companyId, day))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Дата: 01.10.2026 · Обект: Ангел Кънчев · Фирма: Гарант 90");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Петър");
            assertThat(sheet.getLastRowNum()).isEqualTo(3);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static XSSFWorkbook open(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    }

    private static List<String> names(List<MissingWorkerReport> rows) {
        return rows.stream().map(MissingWorkerReport::workerName).toList();
    }

    private static User worker(String name) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getName()).thenReturn(name);
        return user;
    }

    private static Attendance checkIn(User worker, Site site, LocalDateTime at) {
        return Attendance.builder()
                .id(UUID.randomUUID())
                .worker(worker)
                .site(site)
                .clientSite(site)
                .type(AttendanceType.CHECK_IN)
                .clientType(AttendanceType.CHECK_IN)
                .source(AttendanceSource.TERMINAL_FACE)
                .lat(site.getLat()).lng(site.getLng())
                .locationValid(true)
                .recordedAt(at)
                .syncedAt(at.plusSeconds(20))
                .build();
    }
}
