package org.example.attendTrack.site.dto;

import jakarta.validation.constraints.Positive;
import org.example.attendTrack.site.SiteCheckpoint;

import java.util.UUID;

public record CheckpointDto(
        UUID id,
        String name,
        double lat,
        double lng,
        @Positive int radiusMeters
) {
    public static CheckpointDto from(SiteCheckpoint cp) {
        return new CheckpointDto(cp.getId(), cp.getName(), cp.getLat(), cp.getLng(), cp.getRadiusMeters());
    }
}
