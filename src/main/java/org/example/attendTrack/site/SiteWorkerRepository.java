package org.example.attendTrack.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SiteWorkerRepository extends JpaRepository<SiteWorker, SiteWorkerId> {

    @Query("SELECT sw FROM SiteWorker sw JOIN FETCH sw.user WHERE sw.site.id = :siteId")
    List<SiteWorker> findBySiteId(@Param("siteId") UUID siteId);

    @Query("SELECT sw FROM SiteWorker sw JOIN FETCH sw.user WHERE sw.site.id IN :siteIds")
    List<SiteWorker> findBySiteIdIn(@Param("siteIds") List<UUID> siteIds);

    @Query("SELECT sw FROM SiteWorker sw WHERE sw.user.id = :userId")
    List<SiteWorker> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT sw FROM SiteWorker sw JOIN FETCH sw.site WHERE sw.user.id = :userId")
    List<SiteWorker> findByUserIdWithSite(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM SiteWorker sw WHERE sw.site.id = :siteId AND sw.user.id = :userId")
    void deleteBySiteIdAndUserId(@Param("siteId") UUID siteId, @Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM SiteWorker sw WHERE sw.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(sw) > 0 THEN true ELSE false END FROM SiteWorker sw WHERE sw.site.id = :siteId AND sw.user.id = :userId")
    boolean existsBySiteIdAndUserId(@Param("siteId") UUID siteId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(sw) FROM SiteWorker sw WHERE sw.site.id = :siteId")
    long countBySiteId(@Param("siteId") UUID siteId);
}
