package org.example.attendTrack.company;

import jakarta.persistence.*;
import lombok.*;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.User;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;
    private String email;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String mol;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_sites",
            joinColumns = @JoinColumn(name = "company_id"),
            inverseJoinColumns = @JoinColumn(name = "site_id")
    )
    @Builder.Default
    @BatchSize(size = 50)
    private Set<Site> sites = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "company_workers",
            joinColumns = @JoinColumn(name = "company_id"),
            inverseJoinColumns = @JoinColumn(name = "worker_id")
    )
    @Builder.Default
    @BatchSize(size = 50)
    private Set<User> workers = new HashSet<>();

    public void update(String name, String address, String phone,
                       String email, String registrationNumber, String mol) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.registrationNumber = registrationNumber;
        this.mol = mol;
    }

    public void deactivate() {
        this.active = false;
    }
}
