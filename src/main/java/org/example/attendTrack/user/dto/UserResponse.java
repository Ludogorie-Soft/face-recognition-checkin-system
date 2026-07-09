package org.example.attendTrack.user.dto;

import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        String company,
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
                user.getCompany(),
                user.getRole(),
                user.isActive(),
                faceRegistered,
                user.getCreatedAt()
        );
    }
}
