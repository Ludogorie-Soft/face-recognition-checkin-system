package org.example.attendTrack.sync;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.site.SiteRepository;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.sync.dto.SiteSyncResponse;
import org.example.attendTrack.sync.dto.SiteWorkerSyncResponse;
import org.example.attendTrack.user.FaceDescriptorRepository;
import org.example.attendTrack.user.User;
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
    private final FaceDescriptorRepository faceDescriptorRepository;

    @Transactional(readOnly = true)
    public SiteSyncResponse getSiteSync(UUID siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId));

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
