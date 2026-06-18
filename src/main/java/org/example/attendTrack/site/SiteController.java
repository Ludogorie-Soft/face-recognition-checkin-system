package org.example.attendTrack.site;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.site.dto.SiteRequest;
import org.example.attendTrack.site.dto.SiteResponse;
import org.example.attendTrack.user.User;
import org.example.attendTrack.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<SiteResponse>> getAll(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(siteService.getAll(currentUser));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SiteResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(siteService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SiteResponse> create(@Valid @RequestBody SiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SiteResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody SiteRequest request) {
        return ResponseEntity.ok(siteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        siteService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Assignments ───────────────────────────────────────────────────────────

    @PostMapping("/{siteId}/managers/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignManager(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        siteService.assignManager(siteId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{siteId}/managers/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeManager(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        siteService.removeManager(siteId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{siteId}/workers/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignWorker(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        siteService.assignWorker(siteId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{siteId}/workers/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeWorker(
            @PathVariable UUID siteId, @PathVariable UUID userId) {
        siteService.removeWorker(siteId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{siteId}/workers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<UserResponse>> getWorkers(@PathVariable UUID siteId) {
        return ResponseEntity.ok(siteService.getWorkers(siteId));
    }
}
