package org.example.attendTrack.site;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.attendTrack.user.User;

@Entity
@Table(name = "site_managers")
@Getter
@NoArgsConstructor
public class SiteManager {

    @EmbeddedId
    private SiteManagerId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("siteId")
    @JoinColumn(name = "site_id")
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    public SiteManager(Site site, User user) {
        this.id = new SiteManagerId(site.getId(), user.getId());
        this.site = site;
        this.user = user;
    }
}
