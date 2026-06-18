package org.example.attendTrack.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "face_descriptors")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceDescriptor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "descriptor", columnDefinition = "jsonb", nullable = false)
    private double[] descriptor;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    public void updateDescriptor(double[] descriptor) {
        this.descriptor = descriptor;
        this.updatedAt = LocalDateTime.now();
    }
}
