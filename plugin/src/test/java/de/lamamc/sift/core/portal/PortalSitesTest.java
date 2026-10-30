package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PortalSitesTest {

    private static final long SEED = "lamakingdom".hashCode();
    private static final PortalSites OVERWORLD = new PortalSites(64, 0, PortalSites.OVERWORLD_SALT);
    private static final PortalSites END = new PortalSites(32, 1100, PortalSites.END_SALT);

    @Test
    void oneSiteInTheMiddleThirdOfEveryCell() {
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                PortalSites.Site site = OVERWORLD.site(SEED, cx, cz);
                int ox = site.chunkX() - cx * 64;
                int oz = site.chunkZ() - cz * 64;
                assertTrue(ox >= 21 && ox < 43 && oz >= 21 && oz < 43, ox + "/" + oz);
                assertTrue(OVERWORLD.isSite(SEED, site.chunkX(), site.chunkZ()));
                assertFalse(OVERWORLD.isSite(SEED, site.chunkX() + 1, site.chunkZ()));
                assertEquals(site, OVERWORLD.site(SEED, cx, cz)); // deterministic
            }
        }
        assertFalse(OVERWORLD.site(SEED, 0, 0).equals(new PortalSites(64, 0, PortalSites.NETHER_SALT).site(SEED, 0, 0)));
    }

    @Test
    void theEndKeepsTheMainIslandFree() {
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                PortalSites.Site site = END.site(SEED, cx, cz);
                if (site != null) {
                    assertTrue(Math.hypot(site.blockX(), site.blockZ()) >= 1099);
                }
            }
        }
        assertNull(END.site(SEED, 0, 0));
        assertFalse(END.isSite(SEED, 0, 0));
        PortalSites.Site fromCentre = END.nearest(SEED, 0, 0, 16);
        assertNotNull(fromCentre);
        assertTrue(Math.hypot(fromCentre.blockX(), fromCentre.blockZ()) >= 1099);
    }

    @Test
    void nearestMatchesABruteForceSearch() {
        Random random = new Random(5);
        Map<String, PortalSites.Site> sites = new HashMap<>();
        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                PortalSites.Site s = END.site(SEED, cx, cz);
                if (s != null) {
                    sites.put(cx + "," + cz, s);
                }
            }
        }
        for (int i = 0; i < 300; i++) {
            double x = (random.nextDouble() - 0.5) * 5000;
            double z = (random.nextDouble() - 0.5) * 5000;
            PortalSites.Site best = null;
            for (PortalSites.Site s : sites.values()) {
                if (best == null || s.distanceTo(x, z) < best.distanceTo(x, z)) {
                    best = s;
                }
            }
            assertEquals(best, END.nearest(SEED, x, z, 12), x + " " + z);
        }
    }

    @Test
    void shrineHasAFrameTheScannerFindsAndEightConsoles() {
        List<ShrineLayout.Placement> layout = ShrineLayout.build(true, 42);
        Map<String, ShrineLayout.Role> blocks = new HashMap<>();
        layout.forEach(p -> blocks.put(p.x() + "," + p.y() + "," + p.z(), p.role()));
        PortalFrame.Grid grid = new PortalFrame.Grid() {
            @Override
            public boolean isFrame(int x, int y, int z) {
                return blocks.get(x + "," + y + "," + z) == ShrineLayout.Role.FRAME;
            }

            @Override
            public boolean isOpen(int x, int y, int z) {
                return !blocks.containsKey(x + "," + y + "," + z);
            }
        };
        assertEquals(ShrineLayout.frame(), PortalFrame.scan(grid, ShrineLayout.FRAME_X, 1, ShrineLayout.FRAME_Z).orElseThrow());
        assertEquals(5 * 6, ShrineLayout.frame().interiorSize());
        assertEquals(8, ShrineLayout.consoles().size());
        for (int[] c : ShrineLayout.consoles()) {
            assertEquals(ShrineLayout.Role.CONSOLE, blocks.get(c[0] + "," + c[1] + "," + c[2]));
            assertEquals(ShrineLayout.Role.NOTE_BLOCK, blocks.get(c[0] + "," + (c[1] + 1) + "," + c[2]));
            assertTrue(ShrineLayout.FRAME_Z - c[2] <= 10, "console within the frame radius");
        }
        layout.forEach(p -> assertTrue(p.x() >= 0 && p.x() < 15 && p.z() >= 0 && p.z() < 15 && p.y() <= ShrineLayout.CLEAR_HEIGHT));
        assertEquals(ShrineLayout.frameAt(100, 64, -30), new PortalFrame(PortalFrame.Axis.X, 104, 65, -20, 7, 8));
        assertEquals(ShrineLayout.build(true, 42), ShrineLayout.build(true, 42));
    }
}
