package org.example.attendTrack.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SiteCheckpointRepository extends JpaRepository<SiteCheckpoint, UUID> {

    List<SiteCheckpoint> findBySiteId(UUID siteId);

    @Query("SELECT sc FROM SiteCheckpoint sc WHERE sc.site.id IN :siteIds")
    List<SiteCheckpoint> findBySiteIdIn(@Param("siteIds") List<UUID> siteIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SiteCheckpoint sc WHERE sc.site.id = :siteId")
    void deleteBySiteId(@Param("siteId") UUID siteId);
}
