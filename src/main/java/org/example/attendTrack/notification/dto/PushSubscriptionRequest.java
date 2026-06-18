package org.example.attendTrack.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PushSubscriptionRequest(
        @NotBlank String endpoint,
        @NotNull Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
