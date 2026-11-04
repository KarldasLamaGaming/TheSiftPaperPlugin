package de.lamamc.sift.core.rift;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RiftRulesTest {

    /** Flat ground at y = 63 (solid up to 63, open above); extra blocks can be added. */
    private static final class Flat implements RiftRules.Space {
        final Set<String> blocked = new HashSet<>();

        @Override
        public boolean open(int x, int y, int z) {
            return y > 63 && !blocked.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 63 || blocked.contains(x + "," + y + "," + z);
        }

        @Override
        public boolean inside(int x, int y, int z) {
            return y < 320;
        }
    }

    @Test
    void needsNineByFiveOfOpenSpaceOnGround() {
        Flat flat = new Flat();
        assertTrue(RiftRules.fits(flat, 0, 64, 0, true));
        assertTrue(RiftRules.fits(flat, 0, 66, 0, true), "ground 3 below is fine");
        assertFalse(RiftRules.fits(flat, 0, 67, 0, true), "floating 4 above the ground");
        assertFalse(RiftRules.fits(flat, 0, 63, 0, true), "inside the ground");
        flat.blocked.add("4,68,0");
        assertFalse(RiftRules.fits(flat, 0, 64, 0, true), "a block at the end of the top row");
        assertTrue(RiftRules.fits(flat, 0, 64, 0, false), "turned along Z it fits");
        flat.blocked.add("5,68,0");
        assertTrue(RiftRules.fits(flat, 10, 64, 0, true), "elsewhere");
    }

    @Test
    void ringSearchStartsAtTheAnchor() {
        List<int[]> cols = RiftRules.ringColumns(100, 200, 16, 48, 16, 16);
        assertArrayEquals(new int[] {100, 200}, cols.getFirst());
        assertEquals(1 + 3 * 16, cols.size());
        assertArrayEquals(new int[] {116, 200}, cols.get(1), "first ring point at radius 16");
        int[] last = cols.getLast();
        double r = Math.hypot(last[0] - 100, last[1] - 200);
        assertEquals(48, r, 1.0);
    }

    @Test
    void boundsAndDangerZone() {
        double[] b = RiftRules.bounds(0, 64, 0, true);
        assertArrayEquals(new double[] {-4.0, 64.625, 0.0, 5.0, 68.875, 1.0}, b, 1e-9);
        double[] z = RiftRules.dangerZone(b, 0.9, 0.9);
        assertTrue(RiftRules.contains(z, -5.0, 64, 0.5), "next to the rift end");
        assertFalse(RiftRules.contains(z, -6.0, 64, 0.5));
        assertTrue(RiftRules.contains(z, 0.5, 63.8, 0.5), "a mob standing at its foot");
    }

    @Test
    void naturalDelay() {
        assertEquals(5400, RiftRules.nextDelay(5400, 1200, 0));
        assertEquals(6599, RiftRules.nextDelay(5400, 1200, 0.9999999));
        int[] at = RiftRules.around(0.5, 0.5, 0, 20);
        assertArrayEquals(new int[] {20, 0}, at);
    }
}
