package org.example.garant.user.dto;

import org.example.garant.user.Role;
import org.example.garant.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Role role,
        boolean active,
        boolean faceRegistered,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user, boolean faceRegistered) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                faceRegistered,
                user.getCreatedAt()
        );
    }
}
