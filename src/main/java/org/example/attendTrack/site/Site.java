package org.example.attendTrack.site;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "sites")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    @Builder.Default
    private int radiusMeters = 200;

    @Column(name = "work_start_time")
    private LocalTime workStartTime;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void update(String name, String address, double lat, double lng,
                       int radiusMeters, LocalTime workStartTime, LocalTime workEndTime) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.radiusMeters = radiusMeters;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
    }

    public void deactivate() {
        this.active = false;
    }
}
