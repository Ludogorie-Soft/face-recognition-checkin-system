package org.example.attendTrack.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    List<Site> findAllByActiveTrue();

    @Query("SELECT s FROM Site s WHERE s.active = true AND s.workEndTime = :time")
    List<Site> findByWorkEndTime(@Param("time") java.time.LocalTime time);
}
