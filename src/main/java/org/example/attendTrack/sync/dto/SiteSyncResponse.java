package org.example.garant.sync.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SiteSyncResponse(
        SiteInfo site,
        List<SiteWorkerSyncResponse> workers
) {
    public record SiteInfo(
            UUID id,
            String name,
            double lat,
            double lng,
            int radiusMeters,
            LocalTime workStartTime,
            LocalTime workEndTime
    ) {}
}
