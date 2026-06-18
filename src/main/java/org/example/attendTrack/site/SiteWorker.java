package org.example.attendTrack.site;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.attendTrack.user.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "site_workers")
@Getter
@NoArgsConstructor
public class SiteWorker {

    @EmbeddedId
    private SiteWorkerId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("siteId")
    @JoinColumn(name = "site_id")
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    public SiteWorker(Site site, User user) {
        this.id = new SiteWorkerId(site.getId(), user.getId());
        this.site = site;
        this.user = user;
        this.assignedAt = LocalDateTime.now();
    }
}
