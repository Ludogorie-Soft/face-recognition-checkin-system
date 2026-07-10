package org.example.attendTrack.company;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.company.dto.CompanyRequest;
import org.example.attendTrack.company.dto.CompanyResponse;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorker;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final SiteWorkerRepository siteWorkerRepository;

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAll() {
        return companyRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(CompanyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID id) {
        return CompanyResponse.from(findOrThrow(id));
    }

    public CompanyResponse create(CompanyRequest request) {
        Company company = Company.builder()
                .name(request.name())
                .address(request.address())
                .phone(request.phone())
                .email(request.email())
                .registrationNumber(request.registrationNumber())
                .mol(request.mol())
                .build();
        return CompanyResponse.from(companyRepository.save(company));
    }

    public CompanyResponse update(UUID id, CompanyRequest request) {
        Company company = findOrThrow(id);
        company.update(request.name(), request.address(), request.phone(),
                request.email(), request.registrationNumber(), request.mol());
        return CompanyResponse.from(companyRepository.save(company));
    }

    public void deactivate(UUID id) {
        Company company = findOrThrow(id);
        // Collect workers before deactivation so we can cascade-clean their site assignments
        Set<UUID> workerIds = companyRepository.findWorkerIdsByCompanyId(id);
        company.deactivate();
        companyRepository.save(company);
        // D1: cascade — for each worker, remove them from sites they accessed only through this company
        for (UUID workerId : workerIds) {
            Set<UUID> remainingWorkerCompanyIds = companyRepository.findCompanyIdsByWorkerId(workerId);
            List<SiteWorker> workerSites = siteWorkerRepository.findByUserIdWithSite(workerId);
            for (SiteWorker sw : workerSites) {
                UUID siteId = sw.getSite().getId();
                Set<UUID> siteCompanyIds = companyRepository.findCompanyIdsBySiteId(siteId);
                if (Collections.disjoint(siteCompanyIds, remainingWorkerCompanyIds)) {
                    siteWorkerRepository.deleteBySiteIdAndUserId(siteId, workerId);
                }
            }
        }
    }

    // ── Site assignment ───────────────────────────────────────────────────────

    public void assignSite(UUID companyId, UUID siteId) {
        findOrThrow(companyId);
        findSiteOrThrow(siteId);
        companyRepository.addSiteToCompany(companyId, siteId);
    }

    public void removeSite(UUID companyId, UUID siteId) {
        findOrThrow(companyId);
        companyRepository.removeSiteFromCompany(companyId, siteId);
        // C2: cascade — remove workers from the site who no longer have company access to it
        Set<UUID> remainingCompanyIds = companyRepository.findCompanyIdsBySiteId(siteId);
        List<SiteWorker> siteWorkers = siteWorkerRepository.findBySiteId(siteId);
        for (SiteWorker sw : siteWorkers) {
            UUID workerId = sw.getUser().getId();
            Set<UUID> workerCompanyIds = companyRepository.findCompanyIdsByWorkerId(workerId);
            if (Collections.disjoint(remainingCompanyIds, workerCompanyIds)) {
                siteWorkerRepository.deleteBySiteIdAndUserId(siteId, workerId);
            }
        }
    }

    // ── Worker assignment ─────────────────────────────────────────────────────

    public void assignWorker(UUID companyId, UUID workerId) {
        findOrThrow(companyId);
        User worker = findWorkerOrThrow(workerId);
        if (worker.getRole() != Role.WORKER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WRONG_ROLE,
                    "Only users with role WORKER can be assigned to a company");
        }
        companyRepository.addWorkerToCompany(companyId, workerId);
    }

    public void removeWorker(UUID companyId, UUID workerId) {
        findOrThrow(companyId);
        companyRepository.removeWorkerFromCompany(companyId, workerId);
        // C3: cascade — remove worker from sites where they no longer have company access
        Set<UUID> remainingWorkerCompanyIds = companyRepository.findCompanyIdsByWorkerId(workerId);
        List<SiteWorker> workerSites = siteWorkerRepository.findByUserIdWithSite(workerId);
        for (SiteWorker sw : workerSites) {
            UUID siteId = sw.getSite().getId();
            Set<UUID> siteCompanyIds = companyRepository.findCompanyIdsBySiteId(siteId);
            if (Collections.disjoint(siteCompanyIds, remainingWorkerCompanyIds)) {
                siteWorkerRepository.deleteBySiteIdAndUserId(siteId, workerId);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    Company findOrThrow(UUID id) {
        return companyRepository.findById(id)
                .filter(Company::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.COMPANY_NOT_FOUND, "Company not found: " + id));
    }

    private Site findSiteOrThrow(UUID id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.SITE_NOT_FOUND, "Site not found: " + id));
    }

    private User findWorkerOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.USER_NOT_FOUND, "Worker not found: " + id));
    }
}
