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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
        String email = resolveEmailOnCreate(request);

        String rawPassword;
        if (request.role() == Role.WORKER) {
            rawPassword = UUID.randomUUID().toString();
        } else {
            if (!StringUtils.hasText(request.password())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.PASSWORD_REQUIRED, "Password is required");
            }
            rawPassword = request.password();
        }

        User user = User.builder()
                .name(request.name())
                .email(email)
                .phone(StringUtils.hasText(request.phone()) ? request.phone() : null)
                .company(request.role() == Role.WORKER && StringUtils.hasText(request.company()) ? request.company() : null)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(request.role())
                .build();

        return UserResponse.from(userRepository.save(user), false);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = findOrThrow(id);

        String email = resolveEmailOnUpdate(request, user);

        user.update(
                request.name(),
                email,
                StringUtils.hasText(request.phone()) ? request.phone() : null,
                request.role() == Role.WORKER && StringUtils.hasText(request.company()) ? request.company() : null,
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
        faceDescriptorRepository.deleteByUserId(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * For WORKER: email is optional — generates a placeholder if not provided.
     * For ADMIN: email is required and must be unique.
     */
    private String resolveEmailOnCreate(UserRequest request) {
        if (StringUtils.hasText(request.email())) {
            validateEmailFormat(request.email());
            if (userRepository.existsByEmail(request.email())) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email already in use");
            }
            return request.email();
        }
        if (request.role() == Role.WORKER) {
            return UUID.randomUUID() + "@worker.local";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Email is required for admin users");
    }

    /**
     * On update: if no email is provided, keeps the existing one (handles workers with placeholder emails).
     * If a new email is provided, validates format and checks uniqueness.
     */
    private String resolveEmailOnUpdate(UserRequest request, User existing) {
        if (!StringUtils.hasText(request.email())) {
            return existing.getEmail();
        }
        validateEmailFormat(request.email());
        if (!existing.getEmail().equals(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email already in use");
        }
        return request.email();
    }

    private void validateEmailFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid email format");
        }
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + id));
    }
}
