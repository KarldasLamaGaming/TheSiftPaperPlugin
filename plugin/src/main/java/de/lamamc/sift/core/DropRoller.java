package de.lamamc.sift.core;

import java.util.List;
import java.util.random.RandomGenerator;

/** Rolls the drops and experience of a mined custom block. */
public final class DropRoller {

    private DropRoller() {
    }

    /**
     * @param blockItem item id of the block itself (what Silk Touch gives)
     * @param harvested whether the tool may harvest the block (see {@link BlockProfile#canHarvest})
     */
    public static List<ItemDrop> roll(DropSpec spec, String blockItem, boolean harvested, boolean silkTouch,
                                      int fortune, ToolType tool, RandomGenerator random) {
        if (!harvested) {
            return List.of();
        }
        return switch (spec.kind()) {
            case NONE -> List.of();
            case SELF -> List.of(new ItemDrop(spec.item() != null ? spec.item() : blockItem, 1));
            case SILK_ONLY -> silkTouch ? List.of(new ItemDrop(blockItem, 1)) : List.of();
            case SHEARS_OR_SILK -> silkTouch || tool == ToolType.SHEARS ? List.of(new ItemDrop(blockItem, 1)) : List.of();
            case SILK_OR -> silkTouch ? List.of(new ItemDrop(blockItem, 1))
                    : List.of(new ItemDrop(spec.item(), uniform(spec.min(), spec.max(), random)));
            case ORE -> silkTouch ? List.of(new ItemDrop(blockItem, 1))
                    : List.of(new ItemDrop(spec.item(), oreDrops(uniform(spec.min(), spec.max(), random), fortune, random)));
            case CHANCE -> {
                if (silkTouch) {
                    yield List.of(new ItemDrop(blockItem, 1));
                }
                List<Double> chances = spec.chances();
                double chance = chances.isEmpty() ? 0 : chances.get(Math.min(Math.max(fortune, 0), chances.size() - 1));
                yield List.of(new ItemDrop(random.nextDouble() < chance ? spec.item() : blockItem, 1));
            }
        };
    }

    /** Vanilla apply_bonus "ore_drops": count * (max(0, rand(fortune + 2) - 1) + 1). */
    public static int oreDrops(int count, int fortune, RandomGenerator random) {
        if (fortune <= 0) {
            return count;
        }
        int bonus = Math.max(0, random.nextInt(fortune + 2) - 1);
        return count * (bonus + 1);
    }

    /** Experience for a mined block; none with Silk Touch or without the right tool, as in vanilla. */
    public static int experience(BlockProfile profile, boolean harvested, boolean silkTouch, RandomGenerator random) {
        if (!harvested || silkTouch || profile.xpMax() <= 0) {
            return 0;
        }
        return uniform(profile.xpMin(), profile.xpMax(), random);
    }

    private static int uniform(int min, int max, RandomGenerator random) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }
}
