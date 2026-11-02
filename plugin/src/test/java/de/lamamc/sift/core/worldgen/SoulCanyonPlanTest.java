package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SoulCanyonPlanTest {

    private static final SoulCanyonPlan.Settings SETTINGS = SoulCanyonPlan.Settings.defaults();

    /** Terrain over the 3 × 3 chunks around chunk 0/0, with the given surface. */
    private static SoulCanyonPlan.Terrain terrain(IntBinaryOperator surface) {
        return new SoulCanyonPlan.Terrain() {
            @Override
            public int surfaceY(int x, int z) {
                return surface.applyAsInt(x, z);
            }

            @Override
            public boolean contains(int x, int z) {
                return x >= -16 && x <= 31 && z >= -16 && z <= 31;
            }

            @Override
            public int minY() {
                return -64;
            }
        };
    }

    private static final SoulCanyonPlan.Terrain FLAT = terrain((x, z) -> 100);

    private static SoulCanyonPlan.Result plan(SoulCanyonPlan.Terrain terrain, long seed) {
        return SoulCanyonPlan.plan(terrain, columns -> true, 8, 8, new Random(seed), SETTINGS);
    }

    @Test
    void flatTerrainGivesACanyonWithEnoughSouls() {
        int built = 0;
        for (long seed = 0; seed < 40; seed++) {
            SoulCanyonPlan.Result result = plan(FLAT, seed);
            if (result != null) {
                built++;
                assertTrue(result.souls().size() >= 8, "seed " + seed);
                assertTrue(result.length() >= 45 && result.length() <= 47);
            }
        }
        assertTrue(built >= 36, "most seeds build on flat ground: " + built);
    }

    @Test
    void deterministicPerSeed() {
        SoulCanyonPlan.Result a = plan(FLAT, 7);
        SoulCanyonPlan.Result b = plan(FLAT, 7);
        assertNotNull(a);
        assertEquals(a.souls(), b.souls());
        assertEquals(a.entrance(), b.entrance());
        assertEquals(a.columns().keySet(), b.columns().keySet());
    }

    @Test
    void shapeStaysInsideAndWithinDepth() {
        for (long seed = 0; seed < 20; seed++) {
            SoulCanyonPlan.Result result = plan(FLAT, seed);
            if (result == null) {
                continue;
            }
            for (SoulCanyonPlan.Column column : result.columns().values()) {
                assertTrue(column.x() >= -16 && column.x() <= 31 && column.z() >= -16 && column.z() <= 31);
                assertTrue(column.depth() <= 20 + 14, "depth " + column.depth());
                assertTrue(column.floorY() > -64 + 3);
            }
            // the entrance is at ground level, the souls lie deep in the floor
            assertTrue(result.entrance().y() >= 99, "entrance " + result.entrance());
            for (SoulCanyonPlan.Pos soul : result.souls()) {
                assertTrue(soul.y() <= result.entrance().y() - 5, "soul " + soul);
                assertEquals(soul.y(), result.columns().get(SoulCanyonPlan.key(soul.x(), soul.z())).floorY());
            }
            assertTrue(result.midpoint().y() < result.entrance().y());
        }
    }

    @Test
    void soulsAreReachableOverSmallSteps() {
        SoulCanyonPlan.Result result = plan(FLAT, 3);
        assertNotNull(result);
        // walk from the entrance over neighbouring columns whose floors differ by at most 1
        Set<Long> seen = new HashSet<>();
        java.util.ArrayDeque<SoulCanyonPlan.Column> queue = new java.util.ArrayDeque<>();
        SoulCanyonPlan.Column start = result.columns().get(SoulCanyonPlan.key(result.entrance().x(), result.entrance().z()));
        queue.add(start);
        seen.add(SoulCanyonPlan.key(start.x(), start.z()));
        while (!queue.isEmpty()) {
            SoulCanyonPlan.Column c = queue.removeFirst();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long k = SoulCanyonPlan.key(c.x() + d[0], c.z() + d[1]);
                SoulCanyonPlan.Column n = result.columns().get(k);
                if (n != null && !seen.contains(k) && Math.abs(n.floorY() - c.floorY()) <= 1) {
                    seen.add(k);
                    queue.add(n);
                }
            }
        }
        for (SoulCanyonPlan.Pos soul : result.souls()) {
            assertTrue(seen.contains(SoulCanyonPlan.key(soul.x(), soul.z())), "soul " + soul + " reachable");
        }
    }

    @Test
    void cliffsAndMissingGroundGiveNoCanyon() {
        // a 20-block cliff across the area: the surface jumps more than 8 between steps
        assertNull(plan(terrain((x, z) -> x + z > 16 ? 120 : 100), 1));
        // no landmark ground (trees, ichor) anywhere
        assertNull(plan(terrain((x, z) -> Integer.MIN_VALUE), 1));
        // carving refused
        assertNull(SoulCanyonPlan.plan(FLAT, columns -> false, 8, 8, new Random(1), SETTINGS));
    }

    @Test
    void tooFewSoulsAreRefused() {
        assertNull(SoulCanyonPlan.plan(FLAT, columns -> true, 8, 8, new Random(3), SETTINGS.withMinSouls(10_000)));
    }

    @Test
    void hashUnitIsInUnitRange() {
        for (int i = -50; i < 50; i++) {
            double v = SoulCanyonPlan.hashUnit(i, i * 7, 18);
            assertTrue(v >= 0 && v < 1, "hash " + v);
        }
        assertEquals(SoulCanyonPlan.hashUnit(3, 4, 5), SoulCanyonPlan.hashUnit(3, 4, 5));
    }
}
