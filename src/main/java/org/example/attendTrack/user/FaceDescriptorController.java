package org.example.attendTrack.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.user.dto.FaceDescriptorRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{id}/face")
@RequiredArgsConstructor
public class FaceDescriptorController {

    private final FaceDescriptorService faceDescriptorService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> save(
            @PathVariable UUID id,
            @Valid @RequestBody FaceDescriptorRequest request) {
        faceDescriptorService.save(id, request.descriptor());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        faceDescriptorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
