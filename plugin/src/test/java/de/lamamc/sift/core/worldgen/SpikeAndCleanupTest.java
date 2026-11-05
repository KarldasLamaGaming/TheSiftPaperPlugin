package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.worldgen.GrowthCleanup.Below;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpikeAndCleanupTest {

    @Test
    void spikeOfDrySculkOnFlatGround() {
        int built = 0;
        for (int seed = 0; seed < 10; seed++) {
            LandformPlanTest.TestWorld w = new LandformPlanTest.TestWorld((x, z) -> 63, "siftslate");
            DrySpikePlan.Result r = DrySpikePlan.place(w, new Random(seed), 0, 0, 18, 18);
            assertTrue(r.built(), r.reason());
            built++;
            assertTrue(r.topY() - r.y() >= 19 && r.topY() - r.y() <= 31, "main spike 19–31 high: " + (r.topY() - r.y()));
            assertTrue(r.spikes() >= 1 && r.spikes() <= 5);
            assertEquals(DrySpikePlan.DRY, w.block(r.x(), r.y() + 2, r.z()), "the spike is dry sculk");
            assertTrue(w.changed.values().stream().allMatch(DrySpikePlan.DRY::equals), "only dry sculk");
            assertEquals(r.x() - 18, w.reservations.get(0)[0], "reserved ±18");
            assertTrue(w.outside.isEmpty());
        }
        assertEquals(10, built);
    }

    @Test
    void spikeNeedsEvenDryGroundAwayFromThePortal() {
        assertEquals("Gelände uneben", DrySpikePlan.place(new LandformPlanTest.TestWorld((x, z) -> 40 + 3 * x, "siftslate"),
                new Random(1), 0, 0, 18, 18).reason());
        LandformPlanTest.TestWorld portal = new LandformPlanTest.TestWorld((x, z) -> 63, "siftslate");
        portal.portal = true;
        assertEquals("zu nah am Haupt-Portal", DrySpikePlan.place(portal, new Random(1), 0, 0, 18, 18).reason());
        assertTrue(portal.changed.isEmpty());
    }

    @Test
    void coveredGrowthTurnsBare() {
        assertEquals(new GrowthCleanup.Change("siftslate", false), GrowthCleanup.growth("siftslate_growth", false, "siftslate", true, false));
        assertEquals(new GrowthCleanup.Change("dry_healthy_sculk", false),
                GrowthCleanup.growth("dry_healthy_sculk_growth", false, "siftslate", true, false));
        assertEquals(new GrowthCleanup.Change("dry_healthy_sculk", false),
                GrowthCleanup.growth("siftslate_growth", false, "dry_healthy_sculk", true, false), "growth on dry sculk");
    }

    @Test
    void growthOnDrySculkAndSnow() {
        assertEquals(new GrowthCleanup.Change("dry_healthy_sculk_growth", false),
                GrowthCleanup.growth("siftslate_growth", false, "dry_healthy_sculk", false, false));
        assertEquals(new GrowthCleanup.Change("siftslate_growth", true), GrowthCleanup.growth("siftslate_growth", false, "siftslate", false, true),
                "ichor snow on top: snowy");
        assertEquals(new GrowthCleanup.Change("siftslate_growth", false), GrowthCleanup.growth("siftslate_growth", true, "siftslate", false, false),
                "snow gone: not snowy");
        assertNull(GrowthCleanup.growth("siftslate_growth", true, "siftslate", true, true), "snow counts as cover but keeps growth");
        assertNull(GrowthCleanup.growth("siftslate_growth", false, "siftslate", false, false), "open growth stays");
        assertNull(GrowthCleanup.growth("siftslate", false, "siftslate", true, false), "only growth blocks");
    }

    @Test
    void plantsOverIchor() {
        assertTrue(GrowthCleanup.floatsOverIchor(Below.AIR, Below.AIR, Below.ICHOR));
        assertFalse(GrowthCleanup.floatsOverIchor(Below.SOLID), "standing on ground");
        assertFalse(GrowthCleanup.floatsOverIchor(Below.AIR, Below.OTHER_FLUID), "lava is no ichor");
        assertFalse(GrowthCleanup.floatsOverIchor(Below.AIR, Below.AIR, Below.AIR, Below.AIR, Below.AIR, Below.AIR, Below.ICHOR),
                "only 6 blocks deep");
        assertTrue(GrowthCleanup.LAKE_PLANTS.contains("minecraft:torchflower"));
    }
}
