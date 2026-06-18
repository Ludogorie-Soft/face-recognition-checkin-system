package org.example.attendTrack.user;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.user.dto.UserRequest;
import org.example.attendTrack.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FaceDescriptorRepository faceDescriptorRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAll(Role role) {
        List<User> users = (role != null)
                ? userRepository.findAllByRoleAndActiveTrue(role)
                : userRepository.findAllByActiveTrue();

        Set<UUID> faceUserIds = new HashSet<>(faceDescriptorRepository.findAllUserIdsWithFace());
        return users.stream()
                .map(u -> UserResponse.from(u, faceUserIds.contains(u.getId())))
                .toList();
    }

    public UserResponse getById(UUID id) {
        User user = findOrThrow(id);
        boolean faceRegistered = faceDescriptorRepository.findByUserId(id).isPresent();
        return UserResponse.from(user, faceRegistered);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }
        if (!StringUtils.hasText(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PASSWORD_REQUIRED, "Password is required");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        return UserResponse.from(userRepository.save(user), false);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = findOrThrow(id);

        if (!user.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }

        user.update(
                request.name(),
                request.email(),
                request.phone(),
                request.role(),
                StringUtils.hasText(request.password()) ? passwordEncoder.encode(request.password()) : null
        );

        boolean faceRegistered = faceDescriptorRepository.findByUserId(id).isPresent();
        return UserResponse.from(userRepository.save(user), faceRegistered);
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = findOrThrow(id);
        user.deactivate();
        userRepository.save(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + id));
    }
}
