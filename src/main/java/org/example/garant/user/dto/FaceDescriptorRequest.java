package org.example.garant.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceDescriptorRequest(
        @NotNull
        @Size(min = 128, max = 128, message = "Descriptor must contain exactly 128 values")
        double[] descriptor
) {}
