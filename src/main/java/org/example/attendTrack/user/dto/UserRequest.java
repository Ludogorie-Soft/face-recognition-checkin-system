package org.example.attendTrack.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.attendTrack.user.Role;

import java.util.List;
import java.util.UUID;

public record UserRequest(
        @NotBlank
        String name,

        // Optional for WORKER (backend auto-generates placeholder). Required for ADMIN — validated in service.
        String email,

        String phone,

        // Company IDs this worker belongs to (only for WORKER role)
        List<UUID> companyIds,

        // Required on create for ADMIN, optional on update (null = keep existing password)
        String password,

        @NotNull
        Role role
) {}
