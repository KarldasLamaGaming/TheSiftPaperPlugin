package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class IchorLakePlanTest {

    /** Terrain up to the given height (exclusive surface = height + 1), 3 × 3 chunks around chunk 0/0. */
    private static IchorLakePlan.World world(IntBinaryOperator top) {
        return new IchorLakePlan.World() {
            @Override
            public int surface(int x, int z) {
                return top.applyAsInt(x, z) + 1;
            }

            @Override
            public boolean terrain(int x, int y, int z) {
                return y <= top.applyAsInt(x, z);
            }

            @Override
            public boolean ichor(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean contains(int x, int z) {
                return x >= -15 && x <= 30 && z >= -15 && z <= 30;
            }

            @Override
            public boolean nearPortal(int minX, int minZ, int maxX, int maxZ) {
                return false;
            }

            @Override
            public boolean overgrown() {
                return true;
            }

            @Override
            public int minY() {
                return 0;
            }

            @Override
            public int maxY() {
                return 256;
            }
        };
    }

    @Test
    void flatGroundGetsALakeWithIchorBelowTheSurface() {
        IchorLakePlan.Result r = IchorLakePlan.plan(world((x, z) -> 63), new Random(3), 0, 0);
        assertTrue(r.built(), r.reason());
        assertEquals(IchorLakePlan.Variant.MEGA, r.variant(), "relief 0 → MEGA");
        assertEquals(63, r.waterY(), "water one below the first air");
        long ichor = r.edits().stream().filter(e -> e.block().equals(IchorLakePlan.ICHOR)).count();
        assertTrue(ichor >= IchorLakePlan.Variant.MEGA.minArea, "ichor blocks: " + ichor);
        assertTrue(r.edits().stream().filter(e -> e.block().equals(IchorLakePlan.ICHOR)).allMatch(e -> e.y() <= 63));
        assertFalse(r.shoreFloors().isEmpty());
        // deterministic for the same random seed
        assertEquals(r.edits(), IchorLakePlan.plan(world((x, z) -> 63), new Random(3), 0, 0).edits());
    }

    @Test
    void steepGroundGetsNoLake() {
        IchorLakePlan.Result r = IchorLakePlan.plan(world((x, z) -> 63 + x), new Random(1), 0, 0);
        assertFalse(r.built());
    }

    @Test
    void aLedgeLeadsToTheCascadeVariant() {
        // plateau at 80 up to x = 6, a ramp down to 63 at x = 11, flat below: drop 17 between upper and lower spot
        IntBinaryOperator ledge = (x, z) -> x <= 6 ? 80 : x >= 11 ? 63 : (int) Math.round(80 - (x - 6) * 3.4);
        int cascades = 0;
        for (int seed = 0; seed < 20; seed++) {
            IchorLakePlan.Result r = IchorLakePlan.plan(world(ledge), new Random(seed), 0, 0);
            if (r.cascade()) {
                cascades++;
                assertEquals("CASCADE", r.kind());
                if (r.built()) {
                    assertTrue(r.edits().stream().anyMatch(e -> e.block().equals(IchorLakePlan.FALLING)), "a falling curtain");
                    assertTrue(r.waterCells().stream().anyMatch(c -> c[1] > 70), "an upper pond");
                }
            }
        }
        assertTrue(cascades >= 10, "82 % of the tries take the cascade path: " + cascades + " of 20");
    }

    @Test
    void largeOvergrownLakesSometimesGetAWillowIsland() {
        int islands = 0;
        for (int seed = 0; seed < 60; seed++) {
            IchorLakePlan.Result r = IchorLakePlan.plan(world((x, z) -> 63), new Random(seed), 0, 0);
            if (r.built() && r.islandRoot() != null) {
                islands++;
                assertTrue(r.islandRoot()[1] > r.waterY(), "the willow stands above the water");
                int[] root = r.islandRoot();
                assertTrue(r.waterCells().stream().noneMatch(c -> c[0] == root[0] && c[2] == root[2]), "no ichor under the tree");
            }
        }
        assertTrue(islands > 0, "some of 60 MEGA lakes in an overgrown biome get an island");
    }

    @Test
    void variantsByRelief() {
        assertEquals(IchorLakePlan.Variant.MEGA, IchorLakePlan.variant(99, 3));
        assertEquals(IchorLakePlan.Variant.LARGE, IchorLakePlan.variant(71, 5));
        assertEquals(IchorLakePlan.Variant.MEDIUM, IchorLakePlan.variant(72, 5));
        assertEquals(IchorLakePlan.Variant.MEDIUM, IchorLakePlan.variant(81, 7));
        assertEquals(IchorLakePlan.Variant.SMALL, IchorLakePlan.variant(82, 7));
        assertEquals(IchorLakePlan.Variant.SMALL, IchorLakePlan.variant(0, 12));
    }

    @Test
    void shoreRisesFromTheWaterToTheGround() {
        assertEquals(60, IchorLakePlan.shoreTop(64, 60, 1, 4));
        assertEquals(64, IchorLakePlan.shoreTop(64, 60, 4, 4));
        int mid = IchorLakePlan.shoreTop(64, 60, 2, 4);
        assertTrue(mid > 60 && mid < 64);
    }

    @Test
    void springNeedsFourRockSidesAndOneOpening() {
        Set<String> air = new HashSet<>();
        IchorLakePlan.SpringWorld w = new IchorLakePlan.SpringWorld() {
            @Override
            public boolean rock(int x, int y, int z) {
                return !air.contains(x + "," + y + "," + z);
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return air.contains(x + "," + y + "," + z);
            }

            @Override
            public int surface(int x, int z) {
                return 100;
            }
        };
        assertFalse(IchorLakePlan.spring(w, 0, 50, 0), "closed rock: no opening");
        air.add("1,50,0");
        assertTrue(IchorLakePlan.spring(w, 0, 50, 0), "one open side");
        air.add("0,49,0");
        assertFalse(IchorLakePlan.spring(w, 0, 50, 0), "two openings");
        air.remove("0,49,0");
        assertFalse(IchorLakePlan.spring(w, 0, 99, 0), "opening right under the surface");

        // alpha.36: the glow sits above the opening, never in the spring block or in the outflow
        assertNull(IchorLakePlan.springGlow(w, 0, 50, 0), "rock above the opening: no light");
        air.add("1,51,0");
        assertArrayEquals(new int[] {1, 51, 0}, IchorLakePlan.springGlow(w, 0, 50, 0));
    }
}
