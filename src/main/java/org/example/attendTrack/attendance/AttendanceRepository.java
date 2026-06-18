package org.example.attendTrack.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
            SELECT a FROM Attendance a
            JOIN FETCH a.worker
            JOIN FETCH a.site
            WHERE a.site.id = :siteId
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
}
