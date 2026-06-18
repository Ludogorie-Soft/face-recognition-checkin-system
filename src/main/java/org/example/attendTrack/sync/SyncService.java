package org.example.garant.sync;

import lombok.RequiredArgsConstructor;
import org.example.garant.common.exception.ApiException;
import org.example.garant.common.exception.ErrorCode;
import org.example.garant.site.Site;
import org.example.garant.site.SiteManagerRepository;
import org.example.garant.site.SiteRepository;
import org.example.garant.site.SiteWorkerRepository;
import org.example.garant.sync.dto.SiteSyncResponse;
import org.example.garant.sync.dto.SiteWorkerSyncResponse;
import org.example.garant.user.FaceDescriptorRepository;
import org.example.garant.user.Role;
import org.example.garant.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final FaceDescriptorRepository faceDescriptorRepository;

    @Transactional(readOnly = true)
    public SiteSyncResponse getSiteSync(UUID siteId, User currentUser) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId));

        // Managers can only sync their assigned sites
        if (currentUser.getRole() == Role.MANAGER
                && !siteManagerRepository.existsBySiteIdAndUserId(siteId, currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.SITE_NOT_ASSIGNED, "You are not assigned to this site");
        }

        List<SiteWorkerSyncResponse> workers = siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> {
                    User worker = sw.getUser();
                    double[] descriptor = faceDescriptorRepository
                            .findByUserId(worker.getId())
                            .map(fd -> fd.getDescriptor())
                            .orElse(null);
                    return new SiteWorkerSyncResponse(worker.getId(), worker.getName(), descriptor);
                })
                .toList();

        SiteSyncResponse.SiteInfo siteInfo = new SiteSyncResponse.SiteInfo(
                site.getId(),
                site.getName(),
                site.getLat(),
                site.getLng(),
                site.getRadiusMeters(),
                site.getWorkStartTime(),
                site.getWorkEndTime()
        );

        return new SiteSyncResponse(siteInfo, workers);
    }
}
