package org.example.attendTrack.sync.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SiteSyncResponse(
        SiteInfo site,
        List<SiteWorkerSyncResponse> workers
) {
    public record CheckpointInfo(
            UUID id,
            String name,
            double lat,
            double lng,
            int radiusMeters,
            /** Second endpoint — non-null for LINE checkpoints only. */
            Double lat2,
            Double lng2,
            /** "POINT" or "LINE" */
            String checkpointType
    ) {}

    public record SiteInfo(
            UUID id,
            String name,
            double lat,
            double lng,
            int radiusMeters,
            LocalTime workStartTime,
            LocalTime workEndTime,
            List<CheckpointInfo> checkpoints
    ) {}
}
