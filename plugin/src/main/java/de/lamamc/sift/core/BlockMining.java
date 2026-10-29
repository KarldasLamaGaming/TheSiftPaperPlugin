package de.lamamc.sift.core;

import java.util.List;
import java.util.random.RandomGenerator;

/** Drops and experience when a player mines a custom block; used by the break listener and /sift debug. */
public final class BlockMining {

    public record Result(List<ItemDrop> drops, int experience) {
    }

    private BlockMining() {
    }

    /**
     * @param blockItem item id of the block itself ("the_sift:<id>")
     * @param silkTouch the tool has Silk Touch (vanilla predicate minecraft:tool/can_silk_touch)
     * @param fortune   Fortune level of the tool
     */
    public static Result mine(BlockProfile profile, String blockItem, HeldTool held, boolean silkTouch, int fortune,
                              RandomGenerator random) {
        boolean harvested = profile.canHarvest(held);
        List<ItemDrop> drops = DropRoller.roll(profile.drops(), blockItem, harvested, silkTouch, fortune, held.type(),
                random);
        return new Result(drops, DropRoller.experience(profile, harvested, silkTouch, random));
    }
}
