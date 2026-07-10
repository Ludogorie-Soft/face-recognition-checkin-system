package org.example.attendTrack.company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findAllByActiveTrueOrderByNameAsc();

    // Returns [workerId, companyId, companyName] rows — used to bulk-load companies per user
    @Query("""
            SELECT w.id, c.id, c.name
            FROM Company c JOIN c.workers w
            WHERE w.id IN :workerIds AND c.active = true
            ORDER BY c.name
            """)
    List<Object[]> findWorkerCompanyPairs(@Param("workerIds") List<UUID> workerIds);

    @Query("""
            SELECT c FROM Company c JOIN c.workers w
            WHERE w.id = :workerId AND c.active = true
            ORDER BY c.name
            """)
    List<Company> findActiveByWorkerId(@Param("workerId") UUID workerId);

    @Query("SELECT w.id FROM Company c JOIN c.workers w WHERE c.id = :companyId AND w.active = true")
    Set<UUID> findWorkerIdsByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT c.id FROM Company c JOIN c.sites s WHERE s.id = :siteId AND c.active = true")
    Set<UUID> findCompanyIdsBySiteId(@Param("siteId") UUID siteId);

    @Query("SELECT c.id FROM Company c JOIN c.workers w WHERE w.id = :workerId AND c.active = true")
    Set<UUID> findCompanyIdsByWorkerId(@Param("workerId") UUID workerId);

    // ── Join-table helpers (native SQL to avoid JPA entity graph issues) ──────

    @Modifying
    @Query(value = "DELETE FROM company_workers WHERE worker_id = :workerId", nativeQuery = true)
    void removeWorkerFromAllCompanies(@Param("workerId") UUID workerId);

    @Modifying
    @Query(value = "DELETE FROM company_workers WHERE company_id = :companyId AND worker_id = :workerId", nativeQuery = true)
    void removeWorkerFromCompany(@Param("companyId") UUID companyId, @Param("workerId") UUID workerId);

    @Modifying
    @Query(value = """
            INSERT INTO company_workers (company_id, worker_id)
            VALUES (:companyId, :workerId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addWorkerToCompany(@Param("companyId") UUID companyId, @Param("workerId") UUID workerId);

    @Modifying
    @Query(value = "DELETE FROM company_sites WHERE company_id = :companyId AND site_id = :siteId", nativeQuery = true)
    void removeSiteFromCompany(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId);

    @Modifying
    @Query(value = """
            INSERT INTO company_sites (company_id, site_id)
            VALUES (:companyId, :siteId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void addSiteToCompany(@Param("companyId") UUID companyId, @Param("siteId") UUID siteId);
}
