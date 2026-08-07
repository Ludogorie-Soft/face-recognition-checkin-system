package org.example.attendTrack.site;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "site_checkpoints")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteCheckpoint {

    public enum CheckpointType { POINT, LINE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    private String name;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "radius_meters", nullable = false)
    @Builder.Default
    private int radiusMeters = 50;

    /** Second endpoint — non-null only for LINE checkpoints. */
    @Column(name = "lat2")
    private Double lat2;

    @Column(name = "lng2")
    private Double lng2;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkpoint_type", nullable = false, length = 10)
    @Builder.Default
    private CheckpointType checkpointType = CheckpointType.POINT;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
