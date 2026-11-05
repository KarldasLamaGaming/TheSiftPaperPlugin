package de.lamamc.sift.core.worldgen;

import java.util.Set;

/**
 * Covered growth cleanup (alpha.30), the rules of CoveredGrowthCleanupFeature of the original 1.1.1 (feature
 * step top_layer_modification, every column from 56 below the surface up to it):
 * <ul>
 * <li>siftslate growth or dry sculk growth under a full block turns bare again (siftslate or dry sculk);</li>
 * <li>siftslate growth on dry sculk turns into dry sculk growth;</li>
 * <li>the {@code snowy} state of siftslate growth follows ichor snow on top of it;</li>
 * <li>small plants hanging in the air over ichor (a lake or basin dug out below them) are removed.</li>
 * </ul>
 */
public final class GrowthCleanup {

    public static final String GROWTH = "siftslate_growth";
    public static final String DRY_GROWTH = "dry_healthy_sculk_growth";
    public static final String DRY = "dry_healthy_sculk";
    public static final String SIFTSLATE = "siftslate";

    /** Plants the original removes over ichor: Sift ids and vanilla keys. */
    public static final Set<String> LAKE_PLANTS = Set.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds", "overgrown_lotus",
            "sunburst_plant", "whisperbloom", "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts",
            "minecraft:wildflowers", "minecraft:torchflower", "minecraft:torchflower_crop", "minecraft:pitcher_crop",
            "minecraft:pitcher_plant");

    /** What lies below a plant, block by block. */
    public enum Below {
        AIR, ICHOR, OTHER_FLUID, SOLID
    }

    /** A new block for a growth block, or null when it stays. {@code snowy} only matters for siftslate growth. */
    public record Change(String block, boolean snowy) {
    }

    private GrowthCleanup() {
    }

    public static Change growth(String block, boolean snowy, String below, boolean coverFull, boolean coverSnow) {
        if (!GROWTH.equals(block) && !DRY_GROWTH.equals(block)) {
            return null;
        }
        boolean dry = DRY_GROWTH.equals(block) || DRY.equals(below);
        if (coverFull && !coverSnow) {
            return new Change(dry ? DRY : SIFTSLATE, false);
        }
        if (dry && GROWTH.equals(block)) {
            return new Change(DRY_GROWTH, false);
        }
        if (GROWTH.equals(block) && snowy != coverSnow) {
            return new Change(GROWTH, coverSnow);
        }
        return null;
    }

    /** The first of up to 6 blocks below the plant that is not air is ichor (the plant floats over a lake). */
    public static boolean floatsOverIchor(Below... below) {
        for (int i = 0; i < Math.min(6, below.length); i++) {
            if (below[i] == Below.ICHOR) {
                return true;
            }
            if (below[i] != Below.AIR) {
                return false;
            }
        }
        return false;
    }
}
