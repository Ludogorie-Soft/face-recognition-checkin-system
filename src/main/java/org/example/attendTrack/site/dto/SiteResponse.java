package org.example.attendTrack.site.dto;

import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.dto.UserResponse;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SiteResponse(
        UUID id,
        String name,
        String address,
        double lat,
        double lng,
        int radiusMeters,
        LocalTime workStartTime,
        LocalTime workEndTime,
        boolean active,
        LocalDateTime createdAt,
        List<UserResponse> managers,
        List<UserResponse> workers
) {
    public static SiteResponse from(Site site, List<UserResponse> managers, List<UserResponse> workers) {
        return new SiteResponse(
                site.getId(),
                site.getName(),
                site.getAddress(),
                site.getLat(),
                site.getLng(),
                site.getRadiusMeters(),
                site.getWorkStartTime(),
                site.getWorkEndTime(),
                site.isActive(),
                site.getCreatedAt(),
                managers,
                workers
        );
    }

    public static SiteResponse summary(Site site) {
        return from(site, List.of(), List.of());
    }
}
