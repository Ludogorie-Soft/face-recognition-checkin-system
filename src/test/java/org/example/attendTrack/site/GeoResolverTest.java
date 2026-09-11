package org.example.attendTrack.site;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Geometry and site-resolution tests.
 *
 * <p>The fixtures are the real production sites and the real coordinates of the scans that were
 * filed at the wrong site on 8–10 September 2026. If this class goes green, that incident cannot
 * repeat; if someone changes the geometry and these break, it is filing scans 76 km from the
 * worker again.
 */
class GeoResolverTest {

    // ── Production fixtures ──────────────────────────────────────────────────────

    /** Three 10 m LINE corridors along ул. "Ангел Кънчев", Враца — where the crew actually was. */
    private static Site angelKanchev() {
        return site("Основен ремонт на ул. \"Ангел Кънчев\" гр. Враца", 43.212620113478, 23.545242543418524, 10);
    }

    private static List<SiteCheckpoint> angelKanchevCheckpoints(Site site) {
        return List.of(
                line(site, 43.212620113478, 23.545242543418524, 43.21086874555744, 23.546608686447147, 10),
                line(site, 43.210822, 23.546630, 43.210016404122015, 23.546533566531618, 10),
                line(site, 43.210172, 23.546569, 43.209089762534035, 23.546442377446134, 10));
    }

    /** 76 km away. The terminal filed six scans here because it was the worker's first assignment. */
    private static Site obshtinskiPat() {
        return site("ОБЩИНСКИ ПЪТ VRC 1128", 43.68953086779656, 24.276040792465214, 10);
    }

    /** 163 km away, and its stored centre is wrong on top of that — the zone lives in checkpoints. */
    private static Site kostalevo() {
        return site("АСФАЛТОВА БАЗА КОСТАЛЕВО", 42.768919259032806, 25.461502075195316, 200);
    }

    // ── Geometry ─────────────────────────────────────────────────────────────────

    @Nested
    class Geometry {

        @Test
        void haversineMatchesKnownDistance() {
            // Ангел Кънчев centre → ОБЩИНСКИ ПЪТ centre: the two sites the terminal confused.
            double d = GeoResolver.haversineMeters(43.212620113478, 23.545242543418524,
                    43.68953086779656, 24.276040792465214);
            assertThat(d).isCloseTo(79_300, within(200.0));
        }

        @Test
        void pointOnSegmentHasZeroDistance() {
            double d = GeoResolver.distanceToSegmentMeters(
                    43.21174442951772, 23.545925614932836,   // exact midpoint of the corridor below
                    43.212620113478, 23.545242543418524,
                    43.21086874555744, 23.546608686447147);
            assertThat(d).isLessThan(1);
        }

        @Test
        void distanceIsMeasuredToTheSegment_notToItsEndpoints() {
            // A point beside the middle of a long corridor is far from both ends but close to the line.
            double toSegment = GeoResolver.distanceToSegmentMeters(
                    43.2117, 23.5459,
                    43.212620113478, 23.545242543418524,
                    43.21086874555744, 23.546608686447147);
            double toStart = GeoResolver.haversineMeters(43.2117, 23.5459, 43.212620113478, 23.545242543418524);

            assertThat(toSegment).isLessThan(toStart);
        }

        @Test
        void degenerateSegmentFallsBackToPointDistance() {
            double d = GeoResolver.distanceToSegmentMeters(43.2130, 23.5460, 43.2120, 23.5460, 43.2120, 23.5460);
            assertThat(d).isCloseTo(
                    GeoResolver.haversineMeters(43.2130, 23.5460, 43.2120, 23.5460), within(1.0));
        }

        @Test
        void siteWithoutCheckpointsFallsBackToItsOwnRadius() {
            Site s = site("no checkpoints", 43.2120, 23.5460, 100);

            assertThat(GeoResolver.distanceToZone(43.2120, 23.5460, s, List.of(), 0)).isNegative();
            assertThat(GeoResolver.distanceToZone(43.2220, 23.5460, s, List.of(), 0)).isPositive();
        }

        @Test
        void toleranceWidensTheZone() {
            Site s = angelKanchev();
            List<SiteCheckpoint> cps = angelKanchevCheckpoints(s);
            double lat = 43.2093897, lng = 23.5465668;

            double exact = GeoResolver.distanceToZone(lat, lng, s, cps, 0);
            double lenient = GeoResolver.distanceToZone(lat, lng, s, cps, 25);

            assertThat(lenient).isCloseTo(exact - 25, within(0.001));
        }
    }

    // ── The real incident ────────────────────────────────────────────────────────

    @Nested
    class WrongSiteIncident {

        /**
         * ВЕНЕЛИН КРЪСТЕВ ДИМИТРОВ, 10 Sep 2026 17:02. The terminal filed this at ОБЩИНСКИ ПЪТ,
         * 76 km away, because that is his first site assignment. He was standing on Ангел Кънчев.
         */
        @Test
        void resolvesToTheSiteTheDeviceIsActuallyInside_notTheClaimedOne() {
            Site angel = angelKanchev();
            Site claimed = obshtinskiPat();

            Optional<GeoResolver.SiteMatch> match = GeoResolver.resolve(
                    43.2093897, 23.5465668,
                    claimed,
                    List.of(claimed, kostalevo(), angel),
                    Map.of(angel.getId(), angelKanchevCheckpoints(angel)),
                    0);

            assertThat(match).isPresent();
            assertThat(match.get().site().getName()).isEqualTo(angel.getName());
            assertThat(match.get().inside()).isTrue();
            assertThat(match.get().metresOutside()).isZero();
        }

        /** The 09-09 17:03 group — filed at КОСТАЛЕВО, 163 km away, 6 m inside Ангел Кънчев. */
        @Test
        void resolvesTheCrewFiledAtKostalevo() {
            Site angel = angelKanchev();
            Site claimed = kostalevo();

            Optional<GeoResolver.SiteMatch> match = GeoResolver.resolve(
                    43.2119448, 23.5457141,
                    claimed,
                    List.of(claimed, obshtinskiPat(), angel),
                    Map.of(angel.getId(), angelKanchevCheckpoints(angel)),
                    0);

            assertThat(match).isPresent();
            assertThat(match.get().site().getName()).isEqualTo(angel.getName());
            assertThat(match.get().inside()).isTrue();
        }

        @Test
        void keepsTheClaimedSiteWhenTheScanIsGenuinelyInsideIt() {
            Site angel = angelKanchev();

            Optional<GeoResolver.SiteMatch> match = GeoResolver.resolve(
                    43.2093897, 23.5465668,
                    angel,
                    List.of(obshtinskiPat(), angel, kostalevo()),
                    Map.of(angel.getId(), angelKanchevCheckpoints(angel)),
                    0);

            assertThat(match).isPresent();
            assertThat(match.get().site().getName()).isEqualTo(angel.getName());
        }

        /**
         * The end-of-day scan made in the van, outside every zone. The nearest site is still a far
         * better answer than an arbitrary one — and the distance says how much to trust it.
         */
        @Test
        void outsideEveryZone_picksTheNearestAndReportsTheDistance() {
            Site angel = angelKanchev();
            Site far = obshtinskiPat();

            Optional<GeoResolver.SiteMatch> match = GeoResolver.resolve(
                    43.2200, 23.5500,   // ~1 km north of Ангел Кънчев, 70+ km from the other
                    far,
                    List.of(far, angel),
                    Map.of(angel.getId(), angelKanchevCheckpoints(angel)),
                    0);

            assertThat(match).isPresent();
            assertThat(match.get().site().getName()).isEqualTo(angel.getName());
            assertThat(match.get().inside()).isFalse();
            assertThat(match.get().metresOutside()).isBetween(500, 2_000);
        }

        @Test
        void workerWithNoAssignmentsResolvesToNothing() {
            assertThat(GeoResolver.resolve(43.21, 23.54, null, List.of(), Map.of(), 0)).isEmpty();
        }

        @Test
        void insideBeatsNearerButOutside() {
            // A tight zone the point is inside, versus a huge zone whose boundary happens to be closer.
            Site tight = site("tight", 43.2120, 23.5460, 50);
            Site wide = site("wide", 43.2300, 23.5460, 100);

            Optional<GeoResolver.SiteMatch> match = GeoResolver.resolve(
                    43.2120, 23.5460, wide, List.of(wide, tight), Map.of(), 0);

            assertThat(match).isPresent();
            assertThat(match.get().site().getName()).isEqualTo("tight");
            assertThat(match.get().inside()).isTrue();
        }
    }

    // ── Builders ─────────────────────────────────────────────────────────────────

    private static Site site(String name, double lat, double lng, int radius) {
        return Site.builder()
                .id(UUID.randomUUID())
                .name(name)
                .lat(lat)
                .lng(lng)
                .radiusMeters(radius)
                .build();
    }

    private static SiteCheckpoint line(Site site, double lat, double lng,
                                       double lat2, double lng2, int radius) {
        return SiteCheckpoint.builder()
                .id(UUID.randomUUID())
                .site(site)
                .lat(lat).lng(lng)
                .lat2(lat2).lng2(lng2)
                .checkpointType(SiteCheckpoint.CheckpointType.LINE)
                .radiusMeters(radius)
                .build();
    }
}
