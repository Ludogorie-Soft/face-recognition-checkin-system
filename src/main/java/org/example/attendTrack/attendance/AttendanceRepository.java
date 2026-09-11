package org.example.attendTrack.attendance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Attendance a
            WHERE a.worker.id = :workerId
              AND a.clientSite.id = :siteId
              AND a.clientType = :type
              AND a.recordedAt = :recordedAt
            """)
    /**
     * Fallback dedup for terminals that predate client event ids.
     *
     * <p>Matches on {@code clientSite} and {@code clientType} — what the terminal actually reported —
     * never on {@code site} or {@code type}. Both of those are derived: the projection rewrites the
     * direction and the resolver rewrites the site, so a re-sent record compared against them looks
     * like a new event and gets stored twice. Every writer therefore sets {@code clientSite} and
     * {@code clientType}, including the scheduler and admin entries that have no terminal behind them.
     */
    boolean existsDuplicate(
            @Param("workerId") UUID workerId,
            @Param("siteId") UUID siteId,
            @Param("type") AttendanceType type,
            @Param("recordedAt") LocalDateTime recordedAt
    );

    /** Exact idempotency check for device-generated events (see V9 migration). */
    boolean existsByClientEventId(UUID clientEventId);

    /**
     * Last non-ignored event before a moment, across every site — seeds the projection for shifts
     * crossing midnight. Not scoped to a site: a worker has one session at a time, wherever it
     * was opened.
     */
    @Query("""
            SELECT a FROM Attendance a
            WHERE a.worker.id = :workerId
              AND a.ignored = false
              AND a.recordedAt < :before
              AND a.recordedAt >= :notBefore
            ORDER BY a.recordedAt DESC
            """)
    List<Attendance> findLastKeptBefore(
            @Param("workerId") UUID workerId,
            @Param("before") LocalDateTime before,
            @Param("notBefore") LocalDateTime notBefore,
            Pageable pageable
    );

    /**
     * All of one worker's events on one day, across every site — the input to the session
     * projection. Deliberately not per-site: a worker who checks in at one site and scans at
     * another is closing that session, not opening a second one.
     */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.site
            WHERE a.worker.id = :workerId
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt
            """)
    List<Attendance> findForWorkerDay(
            @Param("workerId") UUID workerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            JOIN FETCH a.site
            WHERE a.site.id = :siteId
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.worker.name, a.recordedAt
            """)
    List<Attendance> findBySiteAndDateRange(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Today's kept events for the workers assigned to a site, <b>wherever they scanned</b>.
     *
     * <p>Drives the terminal's check-in / check-out button. Restricting it to the site's own records
     * is what let the terminal at site B offer a check-in to someone whose session was already open
     * at site A — the scan that should have closed the session opened a second one instead.
     */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            WHERE a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
              AND a.worker.id IN (
                  SELECT sw.user.id FROM SiteWorker sw WHERE sw.site.id = :siteId
              )
            ORDER BY a.recordedAt ASC
            """)
    List<Attendance> findTodayForSiteWorkers(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Every kept record of a site's workers in a range, <b>including their scans at other sites</b>.
     *
     * <p>A session can open at one site and close at another, so a site report cannot be built from
     * that site's own rows alone — the closing scan would be missing and the shift would read as
     * open. Callers fetch the full picture and then keep the sessions that belong to their site.
     */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            JOIN FETCH a.site
            WHERE a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
              AND a.worker.id IN (
                  SELECT sw.user.id FROM SiteWorker sw WHERE sw.site.id = :siteId
              )
            ORDER BY a.worker.name, a.recordedAt
            """)
    List<Attendance> findForSiteWorkersInRange(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COUNT(DISTINCT a.worker.id)
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            """)
    long countDistinctWorkersPresentBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            JOIN FETCH a.site
            WHERE a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.worker.name, a.site.id, a.recordedAt
            """)
    List<Attendance> findAllInDateRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            JOIN FETCH a.site
            WHERE a.type = 'CHECK_IN'
              AND a.ignored = false
              AND a.worker.shiftType <> org.example.attendTrack.user.ShiftType.SHIFT_24H
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
              AND NOT EXISTS (
                  SELECT 1 FROM Attendance co
                  WHERE co.worker.id = a.worker.id
                    AND co.type = 'CHECK_OUT'
                    AND co.ignored = false
                    AND co.recordedAt > a.recordedAt
                    AND co.recordedAt < :checkOutTo
              )
            """)
    List<Attendance> findUnclosedCheckIns(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("checkOutTo") LocalDateTime checkOutTo
    );


    /**
     * The worker's last kept event on a day, across every site. Site-scoping this is what made the
     * terminal at site B offer a check-in to someone already checked in at site A.
     */
    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.site
            WHERE a.worker.id = :workerId
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt DESC
            """)
    List<Attendance> findLastForWorkerOnDay(
            @Param("workerId") UUID workerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // ── Dashboard extended ────────────────────────────────────────────────────

    @Query("""
            SELECT COUNT(DISTINCT a.worker.id)
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
              AND a.ignored = false
              AND a.site.id = :siteId
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            """)
    long countDistinctWorkersPresentBySite(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Attendance a SET a.locationValid = :valid WHERE a.id IN :ids")
    void updateLocationValidBulk(@Param("ids") List<UUID> ids, @Param("valid") boolean valid);

    /**
     * Files a record at a site and stores how far the recorded position was from it. Used both by
     * the admin "change site" action and by the retroactive re-resolution. {@code client_site_id} is
     * never touched — it is what the terminal claimed, which stays true whatever we decide here.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE attendance
               SET site_id = :siteId, location_valid = :valid, distance_meters = :distance
             WHERE id = :id
            """, nativeQuery = true)
    void updateResolvedSite(@Param("id") UUID id, @Param("siteId") UUID siteId,
                            @Param("valid") boolean valid, @Param("distance") Integer distance);

    @Query("""
            SELECT DISTINCT a.site.id
            FROM Attendance a
            WHERE a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            """)
    List<UUID> findSiteIdsWithActivity(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT new org.example.attendTrack.dashboard.ActivityEntry(
                a.worker.name, a.site.name, a.type, a.recordedAt
            )
            FROM Attendance a
            WHERE a.ignored = false
            ORDER BY a.recordedAt DESC
            """)
    List<org.example.attendTrack.dashboard.ActivityEntry> findRecentActivity(Pageable pageable);

    @Query("""
            SELECT COUNT(a)
            FROM Attendance a
            WHERE a.type = 'CHECK_OUT'
              AND a.manualOverride = true
              AND a.manager IS NULL
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            """)
    long countAutoCheckouts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Guard shifts still open past a plausible shift length. These are never auto-closed — the
     * admin is alerted so the real times can be entered instead of the system inventing hours.
     */
    @Query("""
            SELECT new org.example.attendTrack.dashboard.OpenShiftEntry(
                a.worker.name, a.site.name, a.recordedAt
            )
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
              AND a.ignored = false
              AND a.worker.shiftType = org.example.attendTrack.user.ShiftType.SHIFT_24H
              AND a.recordedAt < :openSince
              AND NOT EXISTS (
                  SELECT 1 FROM Attendance co
                  WHERE co.worker.id = a.worker.id
                    AND co.type = 'CHECK_OUT'
                    AND co.ignored = false
                    AND co.recordedAt > a.recordedAt
              )
            ORDER BY a.recordedAt
            """)
    List<org.example.attendTrack.dashboard.OpenShiftEntry> findOpenShiftsOlderThan(
            @Param("openSince") LocalDateTime openSince
    );

    @Query("""
            SELECT a.worker.id, COUNT(DISTINCT cast(a.recordedAt as date))
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            GROUP BY a.worker.id
            """)
    List<Object[]> countDistinctDaysPresentPerWorker(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT new org.example.attendTrack.dashboard.OutOfZoneEntry(
                a.worker.name, a.site.name, a.recordedAt
            )
            FROM Attendance a
            WHERE a.locationValid = false
              AND a.ignored = false
              AND a.type = 'CHECK_IN'
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt DESC
            """)
    List<org.example.attendTrack.dashboard.OutOfZoneEntry> findOutOfZoneCheckInsToday(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
