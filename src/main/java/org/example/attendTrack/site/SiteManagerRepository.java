package org.example.garant.site;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SiteManagerRepository extends JpaRepository<SiteManager, SiteManagerId> {

    @Query("SELECT sm FROM SiteManager sm WHERE sm.site.id = :siteId")
    List<SiteManager> findBySiteId(@Param("siteId") UUID siteId);

    @Query("SELECT sm FROM SiteManager sm JOIN FETCH sm.user WHERE sm.site.id IN :siteIds")
    List<SiteManager> findBySiteIdIn(@Param("siteIds") List<UUID> siteIds);

    @Query("SELECT sm FROM SiteManager sm WHERE sm.user.id = :userId")
    List<SiteManager> findByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM SiteManager sm WHERE sm.site.id = :siteId AND sm.user.id = :userId")
    void deleteBySiteIdAndUserId(@Param("siteId") UUID siteId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(sm) > 0 THEN true ELSE false END FROM SiteManager sm WHERE sm.site.id = :siteId AND sm.user.id = :userId")
    boolean existsBySiteIdAndUserId(@Param("siteId") UUID siteId, @Param("userId") UUID userId);
}
