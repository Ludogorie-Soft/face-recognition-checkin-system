package org.example.attendTrack.site.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

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

        LocalTime workEndTime,

        @Valid
        List<CheckpointDto> checkpoints
) {}
