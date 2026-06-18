package org.example.attendTrack.auth.dto;

import org.example.attendTrack.user.Role;

import java.util.UUID;

public record AuthResponse(
        String token,
        String type,
        UUID userId,
        String name,
        String email,
        Role role
) {
    public static AuthResponse of(String token, UUID userId, String name, String email, Role role) {
        return new AuthResponse(token, "Bearer", userId, name, email, role);
    }
}
