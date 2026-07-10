package org.example.attendTrack.company;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.attendTrack.company.dto.CompanyRequest;
import org.example.attendTrack.company.dto.CompanyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<List<CompanyResponse>> getAll() {
        return ResponseEntity.ok(companyService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(companyService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        companyService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Site assignment ───────────────────────────────────────────────────────

    @PostMapping("/{id}/sites/{siteId}")
    public ResponseEntity<Void> assignSite(
            @PathVariable UUID id, @PathVariable UUID siteId) {
        companyService.assignSite(id, siteId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sites/{siteId}")
    public ResponseEntity<Void> removeSite(
            @PathVariable UUID id, @PathVariable UUID siteId) {
        companyService.removeSite(id, siteId);
        return ResponseEntity.noContent().build();
    }

    // ── Worker assignment ─────────────────────────────────────────────────────

    @PostMapping("/{id}/workers/{workerId}")
    public ResponseEntity<Void> assignWorker(
            @PathVariable UUID id, @PathVariable UUID workerId) {
        companyService.assignWorker(id, workerId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/workers/{workerId}")
    public ResponseEntity<Void> removeWorker(
            @PathVariable UUID id, @PathVariable UUID workerId) {
        companyService.removeWorker(id, workerId);
        return ResponseEntity.noContent().build();
    }
}
