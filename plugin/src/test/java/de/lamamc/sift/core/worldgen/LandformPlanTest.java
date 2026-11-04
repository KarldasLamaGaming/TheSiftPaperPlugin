package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class LandformPlanTest {

    /** Siftslate (top block {@code topBlock}) up to top(x, z), 3 × 3 chunks around chunk 0/0 minus one block. */
    static final class TestWorld implements LandformWorld {
        final IntBinaryOperator top;
        final String topBlock;
        final Map<Long, String> changed = new HashMap<>();
        final Set<Long> water = new HashSet<>();
        final List<int[]> reservations = new ArrayList<>();
        final Set<String> outside = new HashSet<>();
        boolean portal;
        int decorations;

        TestWorld(IntBinaryOperator top, String topBlock) {
            this.top = top;
            this.topBlock = topBlock;
        }

        static long key(int x, int y, int z) {
            return ArchPlan.pack(x, y, z);
        }

        @Override
        public int height(int x, int z) {
            if (!contains(x, z)) {
                return Integer.MIN_VALUE;
            }
            for (int y = maxY() - 1; y >= minY(); y--) {
                if (!AIR.equals(block(x, y, z))) {
                    return y + 1;
                }
            }
            return minY();
        }

        @Override
        public String block(int x, int y, int z) {
            String c = changed.get(key(x, y, z));
            if (c != null) {
                return c;
            }
            if (water.contains(key(x, y, z))) {
                return OTHER;
            }
            int t = top.applyAsInt(x, z);
            return y > t ? AIR : y == t ? topBlock : MonolithPlan.SIFTSLATE;
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return water.contains(key(x, y, z));
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= -15 && x <= 30 && z >= -15 && z <= 30;
        }

        @Override
        public boolean writable(int x, int y, int z) {
            return contains(x, z) && y >= minY() && y < maxY();
        }

        @Override
        public void set(int x, int y, int z, String block) {
            if (!writable(x, y, z)) {
                outside.add(x + "," + y + "," + z);
            }
            changed.put(key(x, y, z), block);
        }

        @Override
        public void decorate(int x, int floorY, int z, float density) {
            decorations++;
        }

        @Override
        public boolean nearPortal(int x, int z, int radius) {
            return portal;
        }

        @Override
        public boolean reserve(int minX, int minZ, int maxX, int maxZ) {
            reservations.add(new int[] {minX, minZ, maxX, maxZ});
            return true;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }

    @Test
    void lushMonolithStandsOnFlatGround() {
        TestWorld w = new TestWorld((x, z) -> 63, "siftslate_growth");
        MonolithPlan.Result r = MonolithPlan.place(w, new Random(5), 0, 0, true, 22, 22);
        assertTrue(r.built(), r.reason());
        assertEquals(64, r.y(), "foot on the first free y");
        assertTrue(r.radius() >= 5 && r.radius() <= 7);
        assertTrue(r.height() >= 31 && r.height() <= 55);
        assertEquals(r.y() - 2 + r.height(), r.topY(), "cap at the top of the shaft");
        assertTrue(Math.abs(r.topX() - r.x()) <= 2 && Math.abs(r.topZ() - r.z()) <= 2, "lean of at most 2");
        assertEquals(MonolithPlan.GROWTH, w.block(r.topX(), r.topY(), r.topZ()), "lush cap of siftslate growth");
        assertEquals(MonolithPlan.SIFTSLATE, w.block(r.x(), r.y() + 5, r.z()), "the shaft is siftslate");
        assertTrue(w.decorations > 0, "plants on the cap");
        assertEquals(1, w.reservations.size());
        assertEquals(r.x() - 22, w.reservations.get(0)[0], "reserved ±22 like the original");
        assertTrue(r.satellites() >= 0 && r.satellites() <= 3);
        assertTrue(w.outside.isEmpty(), "no writes outside the region: " + w.outside);
        // deterministic
        TestWorld again = new TestWorld((x, z) -> 63, "siftslate_growth");
        MonolithPlan.place(again, new Random(5), 0, 0, true, 22, 22);
        assertEquals(w.changed, again.changed);
    }

    @Test
    void bareMonolithHasABareCapAndFewSatellites() {
        for (int seed = 0; seed < 20; seed++) {
            TestWorld w = new TestWorld((x, z) -> 70, "siftslate");
            MonolithPlan.Result r = MonolithPlan.place(w, new Random(seed), 0, 0, false, 22, 22);
            assertTrue(r.built(), r.reason());
            assertTrue(r.radius() >= 4 && r.radius() <= 6);
            assertTrue(r.height() >= 24 && r.height() <= 44);
            assertTrue(r.satellites() <= 1);
            assertEquals(MonolithPlan.SIFTSLATE, w.block(r.topX(), r.topY(), r.topZ()));
            assertEquals(0, w.decorations, "no plants on bare monoliths");
        }
    }

    @Test
    void monolithNeedsDryEvenGroundAwayFromThePortal() {
        TestWorld steep = new TestWorld((x, z) -> 40 + 2 * x, "siftslate");
        assertEquals("Gelände uneben", MonolithPlan.place(steep, new Random(1), 0, 0, true, 22, 22).reason());

        TestWorld wet = new TestWorld((x, z) -> 63, "siftslate");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                wet.water.add(TestWorld.key(x, 64, z));
            }
        }
        assertFalse(MonolithPlan.place(wet, new Random(1), 0, 0, true, 22, 22).built(), "ichor on the ground");

        TestWorld portal = new TestWorld((x, z) -> 63, "siftslate");
        portal.portal = true;
        assertEquals("zu nah am Haupt-Portal", MonolithPlan.place(portal, new Random(1), 0, 0, true, 22, 22).reason());
        assertTrue(portal.changed.isEmpty());

        TestWorld high = new TestWorld((x, z) -> 300, "siftslate");
        assertEquals("zu nah an der Bauhöhe", MonolithPlan.place(high, new Random(1), 0, 0, true, 22, 22).reason());
    }

    @Test
    void terracesWidenTheMainPillarMore() {
        assertEquals(1.35, MonolithPlan.terraceBoost(0.28, true));
        assertEquals(0.75, MonolithPlan.terraceBoost(0.28, false));
        assertEquals(0.0, MonolithPlan.terraceBoost(0.5, true));
    }

    /** A valley along x: low at the chunk centre (x 8), rising 1 per block to both sides. */
    private static int valley(int x, int z) {
        return 60 + Math.min(20, Math.abs(x - 8));
    }

    @Test
    void archBridgesAValley() {
        int built = 0;
        for (int seed = 0; seed < 10; seed++) {
            TestWorld w = new TestWorld(LandformPlanTest::valley, "siftslate");
            ArchPlan.Result r = ArchPlan.place(w, new Random(seed), 0, 0, 23);
            assertTrue(r.built(), r.reason());
            built++;
            assertTrue(r.halfSpan() >= 13 && r.halfSpan() <= 15);
            assertTrue(r.rise() >= 6);
            assertTrue(w.outside.isEmpty(), "no writes outside the region");
            // a bridge: arch blocks high over the valley floor, air between floor and arch
            int column = r.crownX();
            int z = r.crownZ();
            int floor = valley(column, z);
            boolean body = false;
            for (int y = floor + 10; y < r.crownY(); y++) {
                body |= !LandformWorld.AIR.equals(w.block(column, y, z));
            }
            assertTrue(body, "arch body under the crown");
            assertEquals(LandformWorld.AIR, w.block(column, floor + 3, z), "open under the arch");
        }
        assertEquals(10, built);
    }

    @Test
    void archNeedsADip() {
        TestWorld flat = new TestWorld((x, z) -> 63, "siftslate");
        assertEquals("keine Senke", ArchPlan.place(flat, new Random(2), 0, 0, 23).reason());
        assertTrue(flat.changed.isEmpty());
        TestWorld portal = new TestWorld(LandformPlanTest::valley, "siftslate");
        portal.portal = true;
        assertEquals("zu nah am Haupt-Portal", ArchPlan.place(portal, new Random(2), 0, 0, 23).reason());
    }

    @Test
    void archOnHealthyGroundIsMostlyHealthySculk() {
        int healthy = 0;
        for (int seed = 0; seed < 40; seed++) {
            TestWorld w = new TestWorld(LandformPlanTest::valley, "healthy_sculk");
            ArchPlan.Result r = ArchPlan.place(w, new Random(seed), 0, 0, 23);
            assertTrue(r.built());
            if (r.healthy()) {
                healthy++;
                assertTrue(w.changed.containsValue(ArchPlan.HEALTHY));
            }
        }
        assertTrue(healthy > 20 && healthy < 40, "78 % healthy on healthy ground: " + healthy + " of 40");
    }

    @Test
    void archOnGrowthGroundGetsAGrowthCrownWhenOfSiftslate() {
        boolean coated = false;
        for (int seed = 0; seed < 20 && !coated; seed++) {
            TestWorld w = new TestWorld(LandformPlanTest::valley, "siftslate_growth");
            ArchPlan.Result r = ArchPlan.place(w, new Random(seed), 0, 0, 23);
            if (!r.healthy()) {
                coated = w.changed.containsValue(MonolithPlan.GROWTH);
            }
        }
        assertTrue(coated);
    }

    @Test
    void curveStartsAndEndsOnTheFeet() {
        ArchPlan.Candidate c = new ArchPlan.Candidate(1, 0, 14, 70, 74, 10);
        ArchPlan.CurvePoint start = ArchPlan.curvePoint(c, 8, 8, 0, 10, 99L);
        ArchPlan.CurvePoint end = ArchPlan.curvePoint(c, 8, 8, 1, 10, 99L);
        assertEquals(-6, start.x(), 1e-9);
        assertEquals(70, start.y(), 1e-9);
        assertEquals(22, end.x(), 1e-9);
        assertEquals(74, end.y(), 1e-9);
        assertTrue(ArchPlan.curvePoint(c, 8, 8, 0.5, 10, 99L).y() > 78, "risen in the middle");
        assertEquals(0.5, ArchPlan.smootherstep(0.5), 1e-9);
    }
}
