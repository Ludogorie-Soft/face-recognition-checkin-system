package org.example.garant.sync;

import lombok.RequiredArgsConstructor;
import org.example.garant.sync.dto.SiteSyncResponse;
import org.example.garant.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/site/{siteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<SiteSyncResponse> getSiteSync(
            @PathVariable UUID siteId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(syncService.getSiteSync(siteId, currentUser));
    }
}
