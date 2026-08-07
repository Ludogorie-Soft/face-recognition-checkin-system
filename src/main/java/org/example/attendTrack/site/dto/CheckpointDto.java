package org.example.attendTrack.site.dto;

import jakarta.validation.constraints.Positive;
import org.example.attendTrack.site.SiteCheckpoint;

import java.util.UUID;

public record CheckpointDto(
        UUID id,
        String name,
        double lat,
        double lng,
        @Positive int radiusMeters,
        /** Second endpoint — non-null only for LINE checkpoints. */
        Double lat2,
        Double lng2,
        /** "POINT" or "LINE" — defaults to "POINT" when null. */
        String checkpointType
) {
    public static CheckpointDto from(SiteCheckpoint cp) {
        SiteCheckpoint.CheckpointType type = cp.getCheckpointType() != null
                ? cp.getCheckpointType()
                : SiteCheckpoint.CheckpointType.POINT;
        return new CheckpointDto(
                cp.getId(), cp.getName(),
                cp.getLat(), cp.getLng(), cp.getRadiusMeters(),
                cp.getLat2(), cp.getLng2(),
                type.name());
    }
}
