package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DropRollerTest {

    private static final String ORE = "the_sift:siftslate_charoite_ore";

    @Test
    void silkOrGivesTheBlockOnlyWithSilkTouch() {
        DropSpec spec = new DropSpec(DropSpec.Kind.SILK_OR, "the_sift:cobbled_siftslate", 1, 1, List.of());
        assertEquals(List.of(new ItemDrop("the_sift:cobbled_siftslate", 1)),
                DropRoller.roll(spec, "the_sift:siftslate", true, false, 0, ToolType.PICKAXE, new Random(1)));
        assertEquals(List.of(new ItemDrop("the_sift:siftslate", 1)),
                DropRoller.roll(spec, "the_sift:siftslate", true, true, 0, ToolType.PICKAXE, new Random(1)));
    }

    @Test
    void nothingDropsWithoutTheRightTool() {
        DropSpec spec = DropSpec.of(DropSpec.Kind.SELF);
        assertTrue(DropRoller.roll(spec, ORE, false, false, 0, ToolType.NONE, new Random(1)).isEmpty());
    }

    @Test
    void oreCountStaysInRangeWithoutFortuneAndGrowsWithFortune() {
        DropSpec spec = new DropSpec(DropSpec.Kind.ORE, "the_sift:charoite", 4, 9, List.of());
        Random random = new Random(42);
        int maxWithFortune = 0;
        for (int i = 0; i < 2000; i++) {
            int plain = DropRoller.roll(spec, ORE, true, false, 0, ToolType.PICKAXE, random).getFirst().amount();
            assertTrue(plain >= 4 && plain <= 9, "count " + plain);
            int fortune = DropRoller.roll(spec, ORE, true, false, 3, ToolType.PICKAXE, random).getFirst().amount();
            assertTrue(fortune >= 4 && fortune <= 36, "count " + fortune);
            maxWithFortune = Math.max(maxWithFortune, fortune);
        }
        assertTrue(maxWithFortune > 9);
    }

    @Test
    void chanceUsesTheFortuneLevelAndFallsBackToTheBlock() {
        DropSpec spec = new DropSpec(DropSpec.Kind.CHANCE, "the_sift:ichor_shard", 1, 1, List.of(0.1, 0.14285715, 0.25, 1.0));
        Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            assertEquals("the_sift:ichor_shard",
                    DropRoller.roll(spec, "the_sift:sift_rubble", true, false, 3, ToolType.SHOVEL, random).getFirst().item());
            assertEquals("the_sift:ichor_shard",
                    DropRoller.roll(spec, "the_sift:sift_rubble", true, false, 9, ToolType.SHOVEL, random).getFirst().item());
        }
        int shards = 0;
        for (int i = 0; i < 10_000; i++) {
            if (DropRoller.roll(spec, "the_sift:sift_rubble", true, false, 0, ToolType.SHOVEL, random)
                    .getFirst().item().equals("the_sift:ichor_shard")) {
                shards++;
            }
        }
        assertTrue(shards > 800 && shards < 1200, "shards " + shards);
    }

    @Test
    void shearsOrSilkTouch() {
        DropSpec spec = DropSpec.of(DropSpec.Kind.SHEARS_OR_SILK);
        assertTrue(DropRoller.roll(spec, "x", true, false, 0, ToolType.NONE, new Random(1)).isEmpty());
        assertEquals(1, DropRoller.roll(spec, "x", true, false, 0, ToolType.SHEARS, new Random(1)).size());
        assertEquals(1, DropRoller.roll(spec, "x", true, true, 0, ToolType.NONE, new Random(1)).size());
    }

    @Test
    void experienceOnlyWithoutSilkTouch() {
        BlockProfile profile = new BlockProfile(ORE, 4.5, ToolType.PICKAXE, true, ToolTier.STONE, false, false,
                DropSpec.of(DropSpec.Kind.SELF), 2, 5);
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            int xp = DropRoller.experience(profile, true, false, random);
            assertTrue(xp >= 2 && xp <= 5);
        }
        assertEquals(0, DropRoller.experience(profile, true, true, random));
        assertEquals(0, DropRoller.experience(profile, false, false, random));
    }
}
