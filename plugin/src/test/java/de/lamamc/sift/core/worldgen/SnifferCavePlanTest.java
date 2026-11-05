package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class SnifferCavePlanTest {

    /** Siftslate up to top(x, z), 3 × 3 chunks around chunk 0/0 minus one block. */
    static final class TestWorld implements SnifferCavePlan.World {
        final IntBinaryOperator top;
        final Map<Long, String> blocks = new HashMap<>();
        final Map<String, Integer> plants = new HashMap<>();
        final Set<String> outside = new HashSet<>();
        boolean portal;

        TestWorld(IntBinaryOperator top) {
            this.top = top;
        }

        @Override
        public int height(int x, int z) {
            for (int y = 200; y >= 0; y--) {
                if (!SnifferCavePlan.AIR.equals(block(x, y, z))) {
                    return y + 1;
                }
            }
            return 0;
        }

        @Override
        public String block(int x, int y, int z) {
            String b = blocks.get(SnifferCavePlan.pack(x, y, z));
            return b != null ? b : y <= top.applyAsInt(x, z) ? "siftslate" : SnifferCavePlan.AIR;
        }

        @Override
        public boolean contains(int x, int z) {
            return x >= -15 && x <= 30 && z >= -15 && z <= 30;
        }

        @Override
        public boolean writable(int x, int y, int z) {
            return contains(x, z) && y >= 0 && y < 320;
        }

        @Override
        public void set(int x, int y, int z, String block) {
            if (!writable(x, y, z)) {
                outside.add(x + "," + y + "," + z);
            }
            blocks.put(SnifferCavePlan.pack(x, y, z), block);
        }

        @Override
        public boolean plant(int x, int y, int z, String plant, int age) {
            String floor = block(x, y - 1, z);
            if (!SnifferCavePlan.TERRAIN.contains(floor) && !SnifferCavePlan.FOLIAGE.equals(floor)) {
                return false;
            }
            set(x, y, z, plant);
            if (SnifferCavePlan.PITCHER.equals(plant)) {
                assertTrue(age == 3 || age == 4);
                set(x, y + 1, z, plant + "_top");
            }
            plants.merge(plant, 1, Integer::sum);
            return true;
        }

        @Override
        public boolean nearPortal(int x, int z, int radius) {
            return portal;
        }

        @Override
        public int minY() {
            return 0;
        }

        long count(String block) {
            return blocks.values().stream().filter(block::equals).count();
        }
    }

    @Test
    void caveUnderFlatGroundHasTunnelNestPoolAndPlants() {
        int built = 0;
        for (int seed = 0; seed < 20; seed++) {
            TestWorld w = new TestWorld((x, z) -> 80);
            SnifferCavePlan.Result r = SnifferCavePlan.place(w, new Random(seed), 0, 0, 12);
            if (!r.built()) {
                continue;
            }
            built++;
            assertEquals(81, r.entrance()[1], "entrance on the surface at the chunk centre");
            assertTrue(r.nest()[1] < 81 - 5, "nest well below the surface: " + r.nest()[1]);
            assertTrue(w.count(SnifferCavePlan.AIR) > 200, "tunnel and chambers carved");
            assertFalse(r.ichor().isEmpty(), "an ichor basin");
            assertTrue(w.count(SnifferCavePlan.HEALTHY) + w.count(SnifferCavePlan.DRY) > 50, "sculk lining");
            assertFalse(r.nestFloors().isEmpty(), "a nest");
            for (int[] f : r.nestFloors()) {
                assertEquals(SnifferCavePlan.FOLIAGE, w.block(f[0], f[1], f[2]));
            }
            assertTrue(r.eggs() <= 2);
            assertTrue(w.outside.isEmpty(), "no writes outside the region");
            // deterministic
            TestWorld again = new TestWorld((x, z) -> 80);
            SnifferCavePlan.place(again, new Random(seed), 0, 0, 12);
            assertEquals(w.blocks, again.blocks);
        }
        assertTrue(built >= 5, "most tries find a direction under flat ground: " + built);
    }

    @Test
    void caveNeedsDeepSiftGroundAndRespectsPortalAndTrunks() {
        assertEquals("kein Sift-Boden am Eingang", SnifferCavePlan.place(new TestWorld((x, z) -> 10), new Random(1), 0, 0, 12).reason());
        // a peak at the centre: every tunnel end is in the open
        TestWorld peak = new TestWorld((x, z) -> 120 - 6 * Math.max(Math.abs(x - 8), Math.abs(z - 8)));
        assertEquals("keine Richtung mit genug Deckung", SnifferCavePlan.place(peak, new Random(1), 0, 0, 12).reason());

        TestWorld portal = new TestWorld((x, z) -> 80);
        portal.portal = true;
        for (int seed = 0; seed < 5; seed++) {
            SnifferCavePlan.Result r = SnifferCavePlan.place(portal, new Random(seed), 0, 0, 12);
            assertFalse(r.built());
        }
        assertTrue(portal.blocks.isEmpty(), "nothing written when rejected");

        TestWorld trunks = new TestWorld((x, z) -> 80);
        for (int x = -15; x <= 30; x++) {
            for (int z = -15; z <= 30; z++) {
                trunks.blocks.put(SnifferCavePlan.pack(x, 72, z), "overgrown_willow_log");
            }
        }
        for (int seed = 0; seed < 5; seed++) {
            SnifferCavePlan.Result r = SnifferCavePlan.place(trunks, new Random(seed), 0, 0, 12);
            assertFalse(r.built(), "never cuts willow trunks");
        }
    }

    @Test
    void basinHasFluidLinedOnAllSides() {
        for (int variant = 0; variant < 4; variant++) {
            SnifferCavePlan.PoolPlan pool = new SnifferCavePlan.PoolPlan(new int[] {0, 60, 0}, 1, variant, 4, 2.7, 3);
            SnifferCavePlan.Basin basin = SnifferCavePlan.basin(pool);
            assertFalse(basin.fluid().isEmpty());
            for (long wet : basin.fluid()) {
                long below = SnifferCavePlan.pack(SnifferCavePlan.x(wet), SnifferCavePlan.y(wet) - 1, SnifferCavePlan.z(wet));
                assertTrue(basin.fluid().contains(below) || basin.lining().contains(below), "ichor never hangs in the air");
            }
            assertTrue(basin.lining().stream().noneMatch(basin.fluid()::contains));
        }
    }

    @Test
    void plantPatchOnHealthySculk() {
        TestWorld w = new TestWorld((x, z) -> 70);
        for (int x = -15; x <= 30; x++) {
            for (int z = -15; z <= 30; z++) {
                w.blocks.put(SnifferCavePlan.pack(x, 70, z), SnifferCavePlan.HEALTHY);
            }
        }
        int placed = SnifferCavePlan.plantPatch(w, new Random(4), 0, 0);
        assertTrue(placed >= 5 && placed <= 9, "5–9 plants: " + placed);
        assertEquals(1, w.plants.size(), "one kind per patch");
        assertEquals(0, SnifferCavePlan.plantPatch(new TestWorld((x, z) -> 70), new Random(4), 0, 0), "not on bare siftslate");
    }

    @Test
    void severalCandidatesPerCell() {
        long seed = 12345L;
        assertEquals(LandmarkPlacement.isSelectedChunk(seed, 3, 7, 16, SnifferCavePlan.OVERGROWN_SALT),
                LandmarkPlacement.isSelectedChunk(seed, 3, 7, 16, SnifferCavePlan.OVERGROWN_SALT, 1));
        int selected = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (SnifferCavePlan.selected(seed, x, z, 16, SnifferCavePlan.OVERGROWN_SALT, 4)) {
                    selected++;
                }
            }
        }
        assertTrue(selected >= 1 && selected <= 4, "up to 4 candidates in one cell: " + selected);
        assertNotNull(LandmarkPlacement.candidateChunk(seed, 0, 0, 28, SnifferCavePlan.WASTES_SALT, 2));
    }
}
