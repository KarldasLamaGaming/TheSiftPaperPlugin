package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BreakTimeCalculatorTest {

    private static final BlockProfile DEEPSLATE_LIKE = new BlockProfile("siftslate", 3.0, ToolType.PICKAXE, true,
            ToolTier.WOOD, false, false, DropSpec.of(DropSpec.Kind.SELF), 0, 0);

    private static int ticks(BlockProfile profile, HeldTool held, double efficiency) {
        double speed = BreakTimeCalculator.toolSpeed(held, profile.tool(), efficiency);
        return BreakTimeCalculator.ticks(BreakTimeCalculator.progress(speed, profile.hardness(), profile.canHarvest(held)));
    }

    @Test
    void matchesVanillaBreakTimesOfDeepslate() {
        // minecraft.wiki: deepslate takes 15 s by hand, 2.25 s with wood, 0.6 s with diamond pickaxe
        assertEquals(300, ticks(DEEPSLATE_LIKE, HeldTool.HAND, 0));
        assertEquals(45, ticks(DEEPSLATE_LIKE, HeldTool.of(ToolType.PICKAXE, ToolTier.WOOD), 0));
        assertEquals(12, ticks(DEEPSLATE_LIKE, HeldTool.of(ToolType.PICKAXE, ToolTier.DIAMOND), 0));
        // Efficiency V adds 26 to the tool speed: (8 + 26) / 3 / 30 per tick
        assertEquals(3, ticks(DEEPSLATE_LIKE, HeldTool.of(ToolType.PICKAXE, ToolTier.DIAMOND), 26));
    }

    @Test
    void speedFactorMakesTheMushroomStemTakeAsLongAsTheCustomBlock() {
        HeldTool pickaxe = HeldTool.of(ToolType.PICKAXE, ToolTier.DIAMOND);
        double factor = BreakTimeCalculator.speedFactor(DEEPSLATE_LIKE, pickaxe, 0, ToolType.AXE,
                BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS);
        // the pickaxe does not suit the mushroom stem: speed 1, hardness 0.2, harvestable
        double standIn = BreakTimeCalculator.progress(1.0, 0.2, true);
        assertEquals(12, BreakTimeCalculator.ticks(standIn * factor));
    }

    @Test
    void axeOnTheStandInIsCompensated() {
        HeldTool axe = HeldTool.of(ToolType.AXE, ToolTier.NETHERITE);
        double factor = BreakTimeCalculator.speedFactor(DEEPSLATE_LIKE, axe, 0, ToolType.AXE,
                BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS);
        double standIn = BreakTimeCalculator.progress(9.0, 0.2, true);
        // an axe is the wrong tool for siftslate: like by hand
        assertEquals(300, BreakTimeCalculator.ticks(standIn * factor));
    }

    @Test
    void tooLowTierMinesAtToolSpeedButAsNotHarvestable() {
        BlockProfile diamondOre = new BlockProfile("ore", 4.5, ToolType.PICKAXE, true, ToolTier.IRON, false, false,
                DropSpec.of(DropSpec.Kind.SELF), 3, 7);
        HeldTool stone = HeldTool.of(ToolType.PICKAXE, ToolTier.STONE);
        assertEquals(false, diamondOre.canHarvest(stone));
        // 4 / 4.5 / 100 per tick
        assertEquals(113, ticks(diamondOre, stone, 0));
    }

    @Test
    void unbreakableBlocksGetFactorZero() {
        BlockProfile reinforced = new BlockProfile("r", -1, ToolType.PICKAXE, true, ToolTier.WOOD, true, true,
                DropSpec.of(DropSpec.Kind.NONE), 0, 0);
        assertEquals(0.0, BreakTimeCalculator.speedFactor(reinforced, HeldTool.of(ToolType.PICKAXE, ToolTier.NETHERITE),
                0, ToolType.AXE, BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS));
    }

    /**
     * alpha.35: the stand-in alone (mushroom stem, hardness 0.2) breaks instantly with an iron or better axe; only
     * with the factor applied before digging starts does siftslate take its real time with an axe.
     */
    @Test
    void axeInstantlyMinesTheStandInWithoutTheFactor() {
        for (ToolTier tier : new ToolTier[] {ToolTier.IRON, ToolTier.DIAMOND, ToolTier.NETHERITE, ToolTier.GOLD}) {
            HeldTool axe = HeldTool.of(ToolType.AXE, tier);
            double standIn = BreakTimeCalculator.progress(BreakTimeCalculator.toolSpeed(axe, ToolType.AXE, 0),
                    BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS, true);
            assertEquals(0, BreakTimeCalculator.ticks(standIn), tier + " axe: vanilla insta-mines the mushroom stem");
            double factor = BreakTimeCalculator.speedFactor(DEEPSLATE_LIKE, axe, 0, ToolType.AXE,
                    BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS);
            assertEquals(300, BreakTimeCalculator.ticks(standIn * factor), tier + " axe on siftslate: like the hand (wrong tool)");
        }
        HeldTool stoneAxe = HeldTool.of(ToolType.AXE, ToolTier.STONE);
        assertTrue(BreakTimeCalculator.ticks(BreakTimeCalculator.progress(BreakTimeCalculator.toolSpeed(stoneAxe, ToolType.AXE, 0),
                BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS, true)) > 0, "stone axe: not instant, the late factor still caught it");
    }
}
