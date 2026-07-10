package org.example.attendTrack.company.dto;

import org.example.attendTrack.company.Company;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String address,
        String phone,
        String email,
        String registrationNumber,
        String mol,
        boolean active,
        LocalDateTime createdAt,
        List<SiteRef> sites,
        List<WorkerRef> workers
) {
    public record SiteRef(UUID id, String name) {}
    public record WorkerRef(UUID id, String name) {}

    public static CompanyResponse from(Company c) {
        List<SiteRef> sites = c.getSites().stream()
                .filter(s -> s.isActive())
                .map(s -> new SiteRef(s.getId(), s.getName()))
                .sorted(java.util.Comparator.comparing(SiteRef::name))
                .toList();

        List<WorkerRef> workers = c.getWorkers().stream()
                .filter(w -> w.isActive())
                .map(w -> new WorkerRef(w.getId(), w.getName()))
                .sorted(java.util.Comparator.comparing(WorkerRef::name))
                .toList();

        return new CompanyResponse(
                c.getId(), c.getName(), c.getAddress(), c.getPhone(),
                c.getEmail(), c.getRegistrationNumber(), c.getMol(),
                c.isActive(), c.getCreatedAt(), sites, workers
        );
    }
}
