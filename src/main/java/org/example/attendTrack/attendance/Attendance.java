package org.example.attendTrack.attendance;

import jakarta.persistence.*;
import lombok.*;
import org.example.attendTrack.site.Site;
import org.example.attendTrack.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private User worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceType type;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "location_valid", nullable = false)
    private boolean locationValid;

    @Column(name = "face_confidence")
    private Double faceConfidence;

    @Column(name = "manual_override", nullable = false)
    @Builder.Default
    private boolean manualOverride = false;

    /** Stable UUID generated on the device; enables idempotent dedup. Null for legacy records. */
    @Column(name = "client_event_id", unique = true)
    private UUID clientEventId;

    /** True when the sync reconciliation flagged this record as violating the check-in/out state machine. */
    @Column(name = "anomaly", nullable = false)
    @Builder.Default
    private boolean anomaly = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_reason", length = 40)
    private AnomalyReason anomalyReason;

    /** True when the device had no connectivity (navigator.onLine == false) at scan time. */
    @Column(name = "created_offline", nullable = false)
    @Builder.Default
    private boolean createdOffline = false;

    /** Where this record originated. Null only for legacy (pre-V10) rows. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    private AttendanceSource source;

    /** Client IP of the uploading terminal (from nginx X-Real-IP / X-Forwarded-For). */
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    /** Stable per-device identifier from the terminal's localStorage. */
    @Column(name = "client_device_id", length = 64)
    private String clientDeviceId;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
