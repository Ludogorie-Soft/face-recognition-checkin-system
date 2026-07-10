package org.example.attendTrack.user;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.company.CompanyRepository;
import org.example.attendTrack.site.SiteWorker;
import org.example.attendTrack.site.SiteWorkerRepository;
import org.example.attendTrack.user.dto.UserRequest;
import org.example.attendTrack.user.dto.UserResponse;
import org.example.attendTrack.user.dto.UserResponse.CompanyRef;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final FaceDescriptorRepository faceDescriptorRepository;
    private final CompanyRepository companyRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> getAll(Role role) {
        List<User> users = (role != null)
                ? userRepository.findAllByRoleAndActiveTrue(role)
                : userRepository.findAllByActiveTrue();

        Set<UUID> faceUserIds = new HashSet<>(faceDescriptorRepository.findAllUserIdsWithFace());

        // Bulk-load company memberships to avoid N+1
        List<UUID> userIds = users.stream().map(User::getId).toList();
        Map<UUID, List<CompanyRef>> companyMap = buildCompanyMap(userIds);

        return users.stream()
                .map(u -> UserResponse.from(u, faceUserIds.contains(u.getId()),
                        companyMap.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        User user = findOrThrow(id);
        boolean faceRegistered = faceDescriptorRepository.findByUserId(id).isPresent();
        List<CompanyRef> companies = buildCompanyMap(List.of(id)).getOrDefault(id, List.of());
        return UserResponse.from(user, faceRegistered, companies);
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
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(request.role())
                .build();

        User saved = userRepository.save(user);
        updateCompanyMemberships(saved.getId(), request);

        List<CompanyRef> companies = buildCompanyMap(List.of(saved.getId()))
                .getOrDefault(saved.getId(), List.of());
        return UserResponse.from(saved, false, companies);
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = findOrThrow(id);

        String email = resolveEmailOnUpdate(request, user);

        user.update(
                request.name(),
                email,
                StringUtils.hasText(request.phone()) ? request.phone() : null,
                request.role(),
                StringUtils.hasText(request.password()) ? passwordEncoder.encode(request.password()) : null
        );

        User saved = userRepository.save(user);
        updateCompanyMemberships(saved.getId(), request);

        boolean faceRegistered = faceDescriptorRepository.findByUserId(id).isPresent();
        List<CompanyRef> companies = buildCompanyMap(List.of(saved.getId()))
                .getOrDefault(saved.getId(), List.of());
        return UserResponse.from(saved, faceRegistered, companies);
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = findOrThrow(id);
        user.deactivate();
        userRepository.save(user);
        faceDescriptorRepository.deleteByUserId(id);
        companyRepository.removeWorkerFromAllCompanies(id);
        siteWorkerRepository.deleteByUserId(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateCompanyMemberships(UUID userId, UserRequest request) {
        // Capture old memberships before clearing — needed for cascade site cleanup
        Set<UUID> oldCompanyIds = companyRepository.findCompanyIdsByWorkerId(userId);
        companyRepository.removeWorkerFromAllCompanies(userId);

        Set<UUID> newCompanyIds = new HashSet<>();
        if (request.role() == Role.WORKER
                && request.companyIds() != null
                && !request.companyIds().isEmpty()) {
            newCompanyIds.addAll(request.companyIds());
            for (UUID companyId : newCompanyIds) {
                companyRepository.addWorkerToCompany(companyId, userId);
            }
        }

        // C4: cascade — remove from sites that now belong to no remaining company of the worker
        if (!oldCompanyIds.isEmpty()) {
            List<SiteWorker> assignments = siteWorkerRepository.findByUserIdWithSite(userId);
            for (SiteWorker sw : assignments) {
                UUID siteId = sw.getSite().getId();
                Set<UUID> siteCompanyIds = companyRepository.findCompanyIdsBySiteId(siteId);
                if (Collections.disjoint(siteCompanyIds, newCompanyIds)) {
                    siteWorkerRepository.deleteBySiteIdAndUserId(siteId, userId);
                }
            }
        }
    }

    private Map<UUID, List<CompanyRef>> buildCompanyMap(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();

        Map<UUID, List<CompanyRef>> map = new HashMap<>();
        companyRepository.findWorkerCompanyPairs(userIds).forEach(row -> {
            UUID workerId  = (UUID) row[0];
            UUID companyId = (UUID) row[1];
            String name    = (String) row[2];
            map.computeIfAbsent(workerId, k -> new ArrayList<>())
               .add(new CompanyRef(companyId, name));
        });
        return map;
    }

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
