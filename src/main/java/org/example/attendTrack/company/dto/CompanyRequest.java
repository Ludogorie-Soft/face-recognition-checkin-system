package org.example.attendTrack.company.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyRequest(
        @NotBlank String name,
        String address,
        String phone,
        String email,
        String registrationNumber,
        String mol
) {}
