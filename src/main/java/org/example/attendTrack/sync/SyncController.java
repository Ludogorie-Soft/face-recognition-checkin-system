package org.example.attendTrack.sync;

import lombok.RequiredArgsConstructor;
import org.example.attendTrack.sync.dto.SiteSyncResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @GetMapping("/site/{siteId}")
    public ResponseEntity<SiteSyncResponse> getSiteSync(@PathVariable UUID siteId) {
        return ResponseEntity.ok(syncService.getSiteSync(siteId));
    }
}
