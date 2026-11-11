package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TempleSitesTest {

    private static final TempleSites.Ring RING = new TempleSites.Ring(10, 1500, 3000, 15);
    private static final double NO_BORDER = 29_999_984;

    @Test
    void exactlyTheConfiguredNumberOnTheRingAndDeterministic() {
        List<TempleSites.Site> sites = TempleSites.sites(42L, RING, 0, 0, NO_BORDER);
        assertEquals(10, sites.size());
        assertEquals(sites, TempleSites.sites(42L, RING, 0, 0, NO_BORDER), "same seed, same places");
        assertNotEquals(sites, TempleSites.sites(43L, RING, 0, 0, NO_BORDER), "other seed, other places");
        for (var site : sites) {
            double r = Math.sqrt(site.distanceSquared(0, 0));
            assertTrue(r >= 1488 && r <= 3012, site + " at " + r);
            assertEquals(8, Math.floorMod(site.x(), 16), "chunk centre");
        }
        assertEquals("temple-1", sites.get(0).id());
        assertEquals(3, TempleSites.sites(42L, new TempleSites.Ring(3, 1500, 3000, 15), 0, 0, NO_BORDER).size());
    }

    @Test
    void sitesKeepTheirDistance() {
        for (long seed = 0; seed < 50; seed++) {
            List<TempleSites.Site> sites = TempleSites.sites(seed, RING, 0, 0, NO_BORDER);
            for (int i = 0; i < sites.size(); i++) {
                for (int j = i + 1; j < sites.size(); j++) {
                    // 36° apart minus twice the jitter (6°) on a ring of at least 1500 blocks
                    assertTrue(Math.sqrt(sites.get(i).distanceSquared(sites.get(j).x(), sites.get(j).z())) > 150,
                            "seed " + seed + ": " + sites.get(i) + " / " + sites.get(j));
                }
            }
        }
    }

    @Test
    void aSmallWorldBorderPullsTheRingIn() {
        assertArrayEquals(new int[] {1500, 3000}, TempleSites.effectiveRadii(RING, NO_BORDER));
        assertArrayEquals(new int[] {1500, 2300}, TempleSites.effectiveRadii(RING, 2500));
        assertArrayEquals(new int[] {800, 800}, TempleSites.effectiveRadii(RING, 1000));
        for (var site : TempleSites.sites(7L, RING, 0, 0, 1000)) {
            assertTrue(Math.abs(site.x()) <= 812 && Math.abs(site.z()) <= 812, site.toString());
        }
    }

    @Test
    void landmarksAskWhetherTheyHitATemple() {
        List<TempleSites.Site> sites = List.of(new TempleSites.Site(0, 2000, 0));
        assertTrue(TempleSites.intersects(sites, 24, 1970, -10, 1980, 10));
        assertFalse(TempleSites.intersects(sites, 24, 1900, -10, 1975, 10));
        var nearest = TempleSites.nearest(List.of(new TempleSites.Site(0, 2000, 0), new TempleSites.Site(1, -2000, 0)),
                -100, 0, s -> s.index() != 1);
        assertEquals(0, nearest.index(), "the defeated one is skipped");
    }
}
