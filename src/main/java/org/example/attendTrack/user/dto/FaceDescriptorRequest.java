package org.example.attendTrack.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceDescriptorRequest(
        @NotNull
        @Size(min = 512, max = 512, message = "Descriptor must contain exactly 512 values")
        double[] descriptor
) {}
