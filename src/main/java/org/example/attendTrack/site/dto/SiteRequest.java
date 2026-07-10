package org.example.attendTrack.site.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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
        List<CheckpointDto> checkpoints,

        /** Required on create, ignored on update (company managed via CompanyService). */
        UUID companyId
) {}
