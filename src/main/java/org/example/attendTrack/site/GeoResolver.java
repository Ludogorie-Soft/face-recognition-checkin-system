package org.example.attendTrack.site;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Decides which site a scan belongs to, from its coordinates.
 *
 * <p>The terminal reports a site with every scan, but that value is only a hint: the device works
 * from a cached site list that may be stale or incomplete, and when no zone matched it used to fall
 * back to the worker's first site assignment — which has no relation to where the device is. The
 * server has every site's checkpoints and is therefore the only place that can answer the question
 * properly.
 *
 * <p>Distances are expressed <b>relative to the zone boundary</b>: negative means inside, positive
 * means that many metres outside. That single number orders candidate sites correctly whether the
 * point is inside one of them or outside all of them.
 *
 * <p>No Spring or JPA dependencies, so the geometry can be tested exhaustively in isolation. The
 * formulas mirror {@code frontend/lib/geo.ts} exactly — the two must agree, because the terminal
 * evaluates the same zones offline.
 */
public final class GeoResolver {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /**
     * A candidate site and how far the scan is from its zone boundary. A non-finite distance means
     * the position was unusable (no GPS fix), in which case the site is a fallback, not a finding.
     */
    public record SiteMatch(Site site, double distanceToZone) {
        /** True when the point lies inside the zone (after any accuracy tolerance). */
        public boolean inside() {
            return distanceToZone <= 0;
        }

        /** Metres outside the zone — 0 when inside, null when the position was unusable. */
        public Integer metresOutside() {
            if (!Double.isFinite(distanceToZone)) return null;
            return (int) Math.round(Math.max(0, distanceToZone));
        }
    }

    /** Marks a site kept without evidence, because the scan carried no usable position. */
    public static SiteMatch unlocated(Site site) {
        return new SiteMatch(site, Double.POSITIVE_INFINITY);
    }

    private GeoResolver() {}

    /**
     * Picks the site a scan at {@code (lat, lng)} belongs to.
     *
     * <p>A site the point actually falls inside always wins over one it does not. Among equally
     * qualified sites the nearest boundary wins, with {@code claimed} breaking exact ties so a
     * correct terminal choice is never churned into an equivalent one.
     *
     * <p>When the point is outside every candidate the nearest is still returned — an approximate
     * site plus a recorded distance is far more useful than an arbitrary one, and
     * {@link SiteMatch#inside()} tells the caller not to trust it as an in-zone scan.
     *
     * @param candidates        sites the worker is assigned to; empty yields {@link Optional#empty()}
     * @param toleranceMeters   GPS accuracy allowance added to every zone radius; pass 0 for none
     */
    public static Optional<SiteMatch> resolve(double lat, double lng,
                                              Site claimed,
                                              List<Site> candidates,
                                              Map<UUID, List<SiteCheckpoint>> checkpointsBySite,
                                              double toleranceMeters) {
        if (candidates.isEmpty()) return Optional.empty();

        UUID claimedId = claimed != null ? claimed.getId() : null;

        return candidates.stream()
                .map(site -> new SiteMatch(site, distanceToZone(
                        lat, lng, site,
                        checkpointsBySite.getOrDefault(site.getId(), List.of()),
                        toleranceMeters)))
                .min(Comparator
                        .comparing((SiteMatch m) -> !m.inside())          // inside first
                        .thenComparingDouble(SiteMatch::distanceToZone)   // then nearest boundary
                        .thenComparing(m -> !m.site().getId().equals(claimedId)));  // tie → keep the claim
    }

    /**
     * Signed distance in metres from a point to a site's zone boundary — negative inside, positive
     * outside. Checkpoints define the zone when present; otherwise the site's own centre and radius
     * do, which is the legacy shape for sites that were never given checkpoints.
     */
    public static double distanceToZone(double lat, double lng, Site site,
                                        List<SiteCheckpoint> checkpoints, double toleranceMeters) {
        if (checkpoints.isEmpty()) {
            return haversineMeters(lat, lng, site.getLat(), site.getLng())
                    - site.getRadiusMeters() - toleranceMeters;
        }
        return checkpoints.stream()
                .mapToDouble(cp -> distanceToZone(lat, lng, cp) - toleranceMeters)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /** Signed distance in metres from a point to one checkpoint's boundary. Negative = inside. */
    public static double distanceToZone(double lat, double lng, SiteCheckpoint cp) {
        double distance = cp.getCheckpointType() == SiteCheckpoint.CheckpointType.LINE
                && cp.getLat2() != null && cp.getLng2() != null
                ? distanceToSegmentMeters(lat, lng, cp.getLat(), cp.getLng(), cp.getLat2(), cp.getLng2())
                : haversineMeters(lat, lng, cp.getLat(), cp.getLng());
        return distance - cp.getRadiusMeters();
    }

    /** Great-circle distance in metres between two coordinates. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Minimum distance in metres from point P to segment AB, using a planar approximation that is
     * accurate well beyond the corridor widths in use (tens to hundreds of metres).
     */
    public static double distanceToSegmentMeters(double pLat, double pLng,
                                                 double aLat, double aLng,
                                                 double bLat, double bLng) {
        double cosLat = Math.cos(Math.toRadians((aLat + bLat) / 2.0));
        double bx = Math.toRadians(bLng - aLng) * EARTH_RADIUS_METERS * cosLat;
        double by = Math.toRadians(bLat - aLat) * EARTH_RADIUS_METERS;
        double px = Math.toRadians(pLng - aLng) * EARTH_RADIUS_METERS * cosLat;
        double py = Math.toRadians(pLat - aLat) * EARTH_RADIUS_METERS;

        double len2 = bx * bx + by * by;
        if (len2 < 1e-10) {
            return Math.hypot(px, py);  // degenerate segment — both endpoints identical
        }
        double t = Math.max(0, Math.min(1, (px * bx + py * by) / len2));
        return Math.hypot(px - t * bx, py - t * by);
    }
}
