package org.example.attendTrack.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.attendTrack.user.Role;

public record UserRequest(
        @NotBlank
        String name,

        // Optional for WORKER (backend auto-generates placeholder). Required for ADMIN — validated in service.
        String email,

        String phone,

        String company,

        // Required on create for ADMIN, optional on update (null = keep existing password)
        String password,

        @NotNull
        Role role
) {}
