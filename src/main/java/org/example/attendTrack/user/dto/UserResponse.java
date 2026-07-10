package org.example.attendTrack.user.dto;

import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        List<CompanyRef> companies,
        Role role,
        boolean active,
        boolean faceRegistered,
        LocalDateTime createdAt
) {
    public record CompanyRef(UUID id, String name) {}

    public static UserResponse from(User user, boolean faceRegistered, List<CompanyRef> companies) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                companies,
                user.getRole(),
                user.isActive(),
                faceRegistered,
                user.getCreatedAt()
        );
    }

    public static UserResponse from(User user, boolean faceRegistered) {
        return from(user, faceRegistered, List.of());
    }
}
