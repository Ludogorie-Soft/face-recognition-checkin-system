package org.example.attendTrack.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.attendTrack.user.Role;

public record UserRequest(
        @NotBlank
        String name,

        @NotBlank @Email
        String email,

        String phone,

        // Required on create, optional on update (null = keep existing password)
        String password,

        @NotNull
        Role role
) {}
