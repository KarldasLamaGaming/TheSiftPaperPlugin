package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.worldgen.SculkRegions;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DarkSnifferRulesTest {

    private final DarkSnifferRules rules = DarkSnifferRules.defaults();

    @Test
    void meleeAndSonicConditions() {
        assertTrue(rules.inReach(9.9225));
        assertFalse(rules.inReach(10.0));
        assertTrue(rules.sonicReady(0, 8, 25, true));
        assertFalse(rules.sonicReady(1, 8, 25, true));
        assertFalse(rules.sonicReady(0, 7, 25, true));
        assertFalse(rules.sonicReady(0, 8, 24.9, true));
        assertFalse(rules.sonicReady(0, 8, 100, false));
    }

    @Test
    void nearerOfPlayerAndSniffer() {
        assertEquals(1, DarkSnifferRules.chooseTarget(100, 400));
        assertEquals(2, DarkSnifferRules.chooseTarget(400, 100));
        assertEquals(2, DarkSnifferRules.chooseTarget(-1, 100));
        assertEquals(0, DarkSnifferRules.chooseTarget(-1, -1));
    }

    @Test
    void waveLeadsAMovingTargetAndOvershoots() {
        DarkSnifferRules.Wave still = rules.aim(new double[] {0, 1, 0}, new double[] {0, 1, 15}, new double[] {0, 0, 0});
        assertArrayEquals(new double[] {0, 0, 1}, still.direction(), 1e-9);
        assertEquals((int) Math.ceil((15 + 7.5) / 1.5), still.flightTicks());
        DarkSnifferRules.Wave moving = rules.aim(new double[] {0, 1, 0}, new double[] {0, 1, 15}, new double[] {0.2, 0, 0});
        assertEquals(2 / Math.hypot(2, 15), moving.direction()[0], 1e-9);
        assertTrue(moving.direction()[0] > 0.1, "aims ahead of a target moving sideways");
        assertNull(rules.aim(new double[] {0, 0, 0}, new double[] {0, 0, 0}, new double[] {0, 0, 0}));
        double[] p = still.position(4, 1.5);
        assertArrayEquals(new double[] {0, 1, 6}, p, 1e-9);
    }

    @Test
    void waveHitsWithinItsRadiusAndPulls() {
        double[] player = {-0.3, 0, 9.7, 0.3, 1.8, 10.3};
        assertTrue(rules.hits(player, new double[] {0, 1, 9}, new double[] {0, 1, 10.5}));
        assertTrue(rules.hits(player, new double[] {1.0, 1, 9}, new double[] {1.0, 1, 10.5}), "within 0.8 of the box");
        assertFalse(rules.hits(player, new double[] {1.2, 1, 9}, new double[] {1.2, 1, 10.5}));
        assertArrayEquals(new double[] {0, 0, -3}, rules.pull(new double[] {0, 0, 0}, new double[] {0, 0, 10}), 1e-9);
    }

    @Test
    void oneOrTwoDarkSnifferBlobsPerRegion() {
        for (int cell = 0; cell < 40; cell++) {
            SculkRegions.Region region = SculkRegions.create(12345L, cell, -cell);
            int[] blobs = SculkRegions.darkSnifferBlobs(region);
            assertTrue(blobs.length == 1 || blobs.length == 2);
            Set<Integer> distinct = new HashSet<>();
            for (int b : blobs) {
                assertTrue(b >= 0 && b < region.blobs().length);
                distinct.add(b);
            }
            assertEquals(blobs.length, distinct.size());
        }
    }
}
