package org.example.attendTrack.attendance;

import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteWorker;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.ShiftType;
import org.example.attendTrack.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every attendance query against a real persistence context.
 *
 * <p>The rest of the suite mocks the repository, so a malformed {@code @Query} would compile, pass,
 * and fail for the first time in production — these are JPQL strings, and only Hibernate can tell
 * whether they parse. This class exists to make that impossible, and to pin the behaviour of the
 * queries that stopped being site-scoped: a session belongs to a worker, so a check-out at one site
 * closes a check-in made at another.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",           // migrations are PostgreSQL-specific
        "spring.jpa.hibernate.ddl-auto=create-drop",
})
class AttendanceRepositoryQueryTest {

    @Autowired
    private AttendanceRepository repository;

    @Autowired
    private TestEntityManager em;

    private User worker;
    private Site siteA;
    private Site siteB;

    private final LocalDate day = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() {
        worker = em.persist(User.builder()
                .name("ВЕНЕЛИН КРЪСТЕВ ДИМИТРОВ")
                .email("venelin@example.com")
                .passwordHash("x")
                .role(Role.WORKER)
                .shiftType(ShiftType.DAY)
                .build());

        siteA = em.persist(Site.builder().name("Ангел Кънчев")
                .lat(43.2126).lng(23.5452).radiusMeters(10).build());
        siteB = em.persist(Site.builder().name("ОБЩИНСКИ ПЪТ")
                .lat(43.6895).lng(24.2760).radiusMeters(10).build());

        em.persist(new SiteWorker(siteA, worker));
        em.persist(new SiteWorker(siteB, worker));
        em.flush();
    }

    // ── The queries that stopped being site-scoped ───────────────────────────────

    @Test
    void findForWorkerDay_returnsEverySiteTheWorkerScannedAt() {
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        save(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();

        List<Attendance> found = repository.findForWorkerDay(
                worker.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(found).hasSize(2);
        assertThat(found).extracting(a -> a.getSite().getName())
                .containsExactly("Ангел Кънчев", "ОБЩИНСКИ ПЪТ");
    }

    @Test
    void findUnclosedCheckIns_treatsACheckOutAtAnotherSiteAsClosingTheSession() {
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        save(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();

        assertThat(repository.findUnclosedCheckIns(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now())).isEmpty();
    }

    @Test
    void findUnclosedCheckIns_stillFindsAGenuinelyOpenSession() {
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        em.flush();

        assertThat(repository.findUnclosedCheckIns(
                day.atStartOfDay(), day.plusDays(1).atStartOfDay(), LocalDateTime.now()))
                .hasSize(1);
    }

    @Test
    void findLastForWorkerOnDay_seesTheScanMadeAtTheOtherSite() {
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        save(siteB, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();

        List<Attendance> last = repository.findLastForWorkerOnDay(
                worker.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay(), PageRequest.of(0, 1));

        // This is what the terminal's button reads: already checked out, wherever that happened.
        assertThat(last).hasSize(1);
        assertThat(last.get(0).getType()).isEqualTo(AttendanceType.CHECK_OUT);
        assertThat(last.get(0).getSite().getName()).isEqualTo("ОБЩИНСКИ ПЪТ");
    }

    @Test
    void findLastKeptBefore_looksAcrossSitesAndSkipsIgnoredRows() {
        Attendance ignored = save(siteB, day.minusDays(1).atTime(19, 5), AttendanceType.CHECK_IN);
        ignored.applyProjection(AttendanceType.CHECK_IN, true, SessionProjector.IgnoreReason.RESCAN);
        save(siteA, day.minusDays(1).atTime(19, 0), AttendanceType.CHECK_IN);
        em.flush();

        List<Attendance> previous = repository.findLastKeptBefore(
                worker.getId(), day.atStartOfDay(), day.minusDays(2).atStartOfDay(), PageRequest.of(0, 1));

        assertThat(previous).hasSize(1);
        assertThat(previous.get(0).getSite().getName()).isEqualTo("Ангел Кънчев");
    }

    @Test
    void findTodayForSiteWorkers_includesScansMadeAtOtherSites() {
        save(siteB, day.atTime(17, 2), AttendanceType.CHECK_IN);
        em.flush();

        // Asked about site A, answered with the worker's record from site B — which is the point.
        assertThat(repository.findTodayForSiteWorkers(
                siteA.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay())).hasSize(1);
    }

    @Test
    void findForSiteWorkersInRange_doesNotLeakWorkersFromOtherSites() {
        User stranger = em.persist(User.builder()
                .name("Друг Работник").email("other@example.com").passwordHash("x")
                .role(Role.WORKER).shiftType(ShiftType.DAY).build());
        Site siteC = em.persist(Site.builder().name("Трети обект")
                .lat(42.0).lng(25.0).radiusMeters(100).build());
        em.persist(new SiteWorker(siteC, stranger));
        em.persist(Attendance.builder()
                .worker(stranger).site(siteC).clientSite(siteC)
                .type(AttendanceType.CHECK_IN).clientType(AttendanceType.CHECK_IN)
                .source(AttendanceSource.TERMINAL_FACE)
                .lat(42.0).lng(25.0).locationValid(true)
                .recordedAt(day.atTime(8, 0)).syncedAt(LocalDateTime.now())
                .build());
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        em.flush();

        List<Attendance> found = repository.findForSiteWorkersInRange(
                siteA.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getWorker().getName()).isEqualTo(worker.getName());
    }

    // ── Dedup matches what the terminal claimed, not what the server derived ─────

    @Test
    void existsDuplicate_recognisesAResentRecordEvenAfterTheServerMovedIt() {
        // Stored resolved to site A while the terminal claimed site B, and re-typed by the
        // projection. A re-send must still be recognised, or it lands in the database twice.
        Attendance stored = save(siteA, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();
        assertThat(stored.getClientSite().getName()).isEqualTo("ОБЩИНСКИ ПЪТ");

        assertThat(repository.existsDuplicate(
                worker.getId(), siteB.getId(), AttendanceType.CHECK_IN, day.atTime(17, 2)))
                .as("re-sent record, matched on the terminal's own claim")
                .isTrue();
    }

    @Test
    void existsDuplicate_doesNotMatchADifferentEvent() {
        save(siteA, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();

        assertThat(repository.existsDuplicate(
                worker.getId(), siteB.getId(), AttendanceType.CHECK_IN, day.atTime(18, 30))).isFalse();
    }

    // ── Remaining queries: parsed and executed, so a malformed one cannot ship ───

    @Test
    void everyOtherQueryParsesAndRuns() {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        save(siteA, day.atTime(7, 47), AttendanceType.CHECK_IN);
        em.flush();

        assertThat(repository.findBySiteAndDateRange(siteA.getId(), from, to)).hasSize(1);
        assertThat(repository.findAllInDateRange(from, to)).hasSize(1);
        assertThat(repository.countDistinctWorkersPresentBetween(from, to)).isEqualTo(1);
        assertThat(repository.countDistinctWorkersPresentBySite(siteA.getId(), from, to)).isEqualTo(1);
        assertThat(repository.findSiteIdsWithActivity(from, to)).containsExactly(siteA.getId());
        assertThat(repository.countAutoCheckouts(from, to)).isZero();
        assertThat(repository.countDistinctDaysPresentPerWorker(from, to)).hasSize(1);
        assertThat(repository.findOutOfZoneCheckInsToday(from, to)).isEmpty();
        assertThat(repository.findOpenShiftsOlderThan(LocalDateTime.now())).isEmpty();
        assertThat(repository.findRecentActivity(PageRequest.of(0, 10))).hasSize(1);
        assertThat(repository.existsByClientEventId(java.util.UUID.randomUUID())).isFalse();
    }

    @Test
    void updateResolvedSite_movesTheRecordAndLeavesTheClaimAlone() {
        Attendance stored = save(siteA, day.atTime(17, 2), AttendanceType.CHECK_OUT);
        em.flush();

        repository.updateResolvedSite(stored.getId(), siteB.getId(), true, 0);
        em.clear();

        Attendance reloaded = repository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getSite().getName()).isEqualTo("ОБЩИНСКИ ПЪТ");
        assertThat(reloaded.isLocationValid()).isTrue();
        assertThat(reloaded.getDistanceMeters()).isZero();
        assertThat(reloaded.getClientSite().getName()).isEqualTo("ОБЩИНСКИ ПЪТ");
    }

    /** Stored at {@code site}, claimed by the terminal to be site B — the shape of the real bug. */
    private Attendance save(Site site, LocalDateTime at, AttendanceType type) {
        return em.persist(Attendance.builder()
                .worker(worker)
                .site(site)
                .clientSite(siteB)
                .clientType(AttendanceType.CHECK_IN)
                .type(type)
                .source(AttendanceSource.TERMINAL_FACE)
                .lat(43.21).lng(23.54)
                .locationValid(true)
                .distanceMeters(0)
                .recordedAt(at)
                .syncedAt(LocalDateTime.now())
                .build());
    }
}
