package org.example.garant.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SiteRequest(
        @NotBlank
        String name,

        String address,

        @NotNull
        Double lat,

        @NotNull
        Double lng,

        Integer radiusMeters,

        LocalTime workStartTime,

        LocalTime workEndTime
) {}
