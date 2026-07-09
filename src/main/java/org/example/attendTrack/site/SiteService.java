package org.example.attendTrack.site;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.common.exception.ApiException;
import org.example.attendTrack.common.exception.ErrorCode;
import org.example.attendTrack.site.dto.SiteRequest;
import org.example.attendTrack.site.dto.SiteResponse;
import org.example.attendTrack.user.FaceDescriptorRepository;
import org.example.attendTrack.user.Role;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.UserRepository;
import org.example.attendTrack.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SiteRepository siteRepository;
    private final SiteWorkerRepository siteWorkerRepository;
    private final SiteManagerRepository siteManagerRepository;
    private final UserRepository userRepository;
    private final FaceDescriptorRepository faceDescriptorRepository;

    public List<SiteResponse> getAll() {
        List<Site> sites = siteRepository.findAllByActiveTrue();
        if (sites.isEmpty()) return List.of();

        List<UUID> siteIds = sites.stream().map(Site::getId).toList();
        Set<UUID> faceIds = new HashSet<>(faceDescriptorRepository.findAllUserIdsWithFace());

        // Batch-load all managers and workers for all sites in 2 queries
        Map<UUID, List<UserResponse>> managersBySite = siteManagerRepository.findBySiteIdIn(siteIds)
                .stream()
                .collect(Collectors.groupingBy(
                        sm -> sm.getId().getSiteId(),
                        Collectors.mapping(sm -> UserResponse.from(sm.getUser(), faceIds.contains(sm.getUser().getId())),
                                Collectors.toList())
                ));

        Map<UUID, List<UserResponse>> workersBySite = siteWorkerRepository.findBySiteIdIn(siteIds)
                .stream()
                .collect(Collectors.groupingBy(
                        sw -> sw.getId().getSiteId(),
                        Collectors.mapping(sw -> UserResponse.from(sw.getUser(), faceIds.contains(sw.getUser().getId())),
                                Collectors.toList())
                ));

        return sites.stream()
                .map(s -> SiteResponse.from(
                        s,
                        managersBySite.getOrDefault(s.getId(), List.of()),
                        workersBySite.getOrDefault(s.getId(), List.of())
                ))
                .toList();
    }

    public SiteResponse getById(UUID id) {
        return toDetailResponse(findOrThrow(id));
    }

    @Transactional
    public SiteResponse create(SiteRequest request) {
        Site site = Site.builder()
                .name(request.name())
                .address(request.address())
                .lat(request.lat())
                .lng(request.lng())
                .radiusMeters(request.radiusMeters() != null ? request.radiusMeters() : 200)
                .workStartTime(request.workStartTime())
                .workEndTime(request.workEndTime())
                .build();

        return SiteResponse.from(siteRepository.save(site), List.of(), List.of());
    }

    @Transactional
    public SiteResponse update(UUID id, SiteRequest request) {
        Site site = findOrThrow(id);
        site.update(
                request.name(),
                request.address(),
                request.lat(),
                request.lng(),
                request.radiusMeters() != null ? request.radiusMeters() : site.getRadiusMeters(),
                request.workStartTime(),
                request.workEndTime()
        );
        return SiteResponse.summary(siteRepository.save(site));
    }

    @Transactional
    public void deactivate(UUID id) {
        Site site = findOrThrow(id);
        site.deactivate();
        siteRepository.save(site);
    }

    @Transactional
    public void assignManager(UUID siteId, UUID userId) {
        Site site = findOrThrow(siteId);
        User user = findUserOrThrow(userId, Role.ADMIN);
        if (siteManagerRepository.existsBySiteIdAndUserId(siteId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_ASSIGNED, "User is already a manager of this site");
        }
        siteManagerRepository.save(new SiteManager(site, user));
    }

    @Transactional
    public void removeManager(UUID siteId, UUID userId) {
        findOrThrow(siteId);
        if (!siteManagerRepository.existsBySiteIdAndUserId(siteId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_FOUND, "Assignment not found");
        }
        siteManagerRepository.deleteBySiteIdAndUserId(siteId, userId);
    }

    @Transactional
    public void assignWorker(UUID siteId, UUID userId) {
        Site site = findOrThrow(siteId);
        User user = findUserOrThrow(userId, Role.WORKER);
        if (siteWorkerRepository.existsBySiteIdAndUserId(siteId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_ASSIGNED, "User is already a worker on this site");
        }
        siteWorkerRepository.save(new SiteWorker(site, user));
    }

    @Transactional
    public void removeWorker(UUID siteId, UUID userId) {
        findOrThrow(siteId);
        if (!siteWorkerRepository.existsBySiteIdAndUserId(siteId, userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_FOUND, "Assignment not found");
        }
        siteWorkerRepository.deleteBySiteIdAndUserId(siteId, userId);
    }

    public List<UserResponse> getWorkers(UUID siteId) {
        findOrThrow(siteId);
        Set<UUID> faceIds = new HashSet<>(faceDescriptorRepository.findAllUserIdsWithFace());
        return siteWorkerRepository.findBySiteId(siteId).stream()
                .map(sw -> UserResponse.from(sw.getUser(), faceIds.contains(sw.getUser().getId())))
                .toList();
    }

    public List<SiteResponse> getSitesByUser(UUID userId) {
        List<UUID> siteIds = siteWorkerRepository.findByUserId(userId)
                .stream().map(sw -> sw.getId().getSiteId()).toList();
        if (siteIds.isEmpty()) return List.of();
        return siteRepository.findAllById(siteIds).stream()
                .map(SiteResponse::summary)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SiteResponse toDetailResponse(Site site) {
        Set<UUID> faceIds = new HashSet<>(faceDescriptorRepository.findAllUserIdsWithFace());
        List<UserResponse> managers = siteManagerRepository.findBySiteId(site.getId())
                .stream().map(sm -> UserResponse.from(sm.getUser(), faceIds.contains(sm.getUser().getId()))).toList();
        List<UserResponse> workers = siteWorkerRepository.findBySiteId(site.getId())
                .stream().map(sw -> UserResponse.from(sw.getUser(), faceIds.contains(sw.getUser().getId()))).toList();
        return SiteResponse.from(site, managers, workers);
    }

    private Site findOrThrow(UUID id) {
        return siteRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SITE_NOT_FOUND, "Site not found: " + id));
    }

    private User findUserOrThrow(UUID userId, Role expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found: " + userId));
        if (user.getRole() != expectedRole) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WRONG_ROLE, "User must have role " + expectedRole.name());
        }
        return user;
    }
}
