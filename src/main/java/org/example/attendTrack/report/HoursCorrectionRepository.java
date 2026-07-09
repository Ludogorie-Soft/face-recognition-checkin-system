package org.example.attendTrack.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoursCorrectionRepository extends JpaRepository<HoursCorrection, UUID> {

    Optional<HoursCorrection> findByWorkerIdAndSiteIdAndDate(UUID workerId, UUID siteId, LocalDate date);

    @Query("""
            SELECT c FROM HoursCorrection c
            WHERE c.site.id = :siteId
              AND c.date BETWEEN :from AND :to
            """)
    List<HoursCorrection> findBySiteAndPeriod(
            @Param("siteId") UUID siteId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
            SELECT c FROM HoursCorrection c
            WHERE c.date BETWEEN :from AND :to
            """)
    List<HoursCorrection> findAllInPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
