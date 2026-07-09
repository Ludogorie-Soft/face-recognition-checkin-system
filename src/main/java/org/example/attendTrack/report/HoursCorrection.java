package org.example.attendTrack.report;

import jakarta.persistence.*;
import lombok.*;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "hours_corrections")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoursCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "corrected_hours", nullable = false)
    private double correctedHours;

    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void update(double correctedHours, String note, User updatedBy) {
        this.correctedHours = correctedHours;
        this.note = note;
        this.createdBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }
}
