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
              AND a.site.id   = :siteId
              AND a.type      = :type
              AND a.recordedAt = :recordedAt
            """)
    boolean existsDuplicate(
            @Param("workerId") UUID workerId,
            @Param("siteId") UUID siteId,
            @Param("type") AttendanceType type,
            @Param("recordedAt") LocalDateTime recordedAt
    );

    /** Exact idempotency check for device-generated events (see V9 migration). */
    boolean existsByClientEventId(UUID clientEventId);

    /** All events for one (worker, site, day) — the input to the session projection. */
    @Query("""
            SELECT a FROM Attendance a
            WHERE a.worker.id = :workerId
              AND a.site.id   = :siteId
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt
            """)
    List<Attendance> findForWorkerSiteDay(
            @Param("workerId") UUID workerId,
            @Param("siteId") UUID siteId,
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

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            WHERE a.site.id = :siteId
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt ASC
            """)
    List<Attendance> findTodayBySite(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COUNT(DISTINCT a.worker.id)
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
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
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
              AND NOT EXISTS (
                  SELECT 1 FROM Attendance co
                  WHERE co.worker.id = a.worker.id
                    AND co.site.id = a.site.id
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


    @Query("""
            SELECT a FROM Attendance a
            WHERE a.worker.id = :workerId
              AND a.site.id   = :siteId
              AND a.ignored = false
              AND a.recordedAt >= :from
              AND a.recordedAt < :to
            ORDER BY a.recordedAt DESC
            """)
    List<Attendance> findLastForWorkerOnDay(
            @Param("workerId") UUID workerId,
            @Param("siteId") UUID siteId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // ── Dashboard extended ────────────────────────────────────────────────────

    @Query("""
            SELECT COUNT(DISTINCT a.worker.id)
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE attendance SET site_id = :siteId WHERE id = :id", nativeQuery = true)
    void updateSite(@Param("id") UUID id, @Param("siteId") UUID siteId);

    @Query("""
            SELECT DISTINCT a.site.id
            FROM Attendance a
            WHERE a.recordedAt >= :from
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

    @Query("""
            SELECT a.worker.id, COUNT(DISTINCT cast(a.recordedAt as date))
            FROM Attendance a
            WHERE a.type = 'CHECK_IN'
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
