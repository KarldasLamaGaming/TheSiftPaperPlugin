package de.lamamc.sift.core;

/**
 * Vanilla mining speed (Player#getDestroySpeed and BlockBehaviour#getDestroyProgress), used to make a
 * stand-in block (e.g. a mushroom stem, hardness 0.2) take as long to mine as the custom block it shows.
 *
 * <p>Per tick progress = speed / hardness / (30 if harvestable, else 100), where speed is the tool speed
 * if the tool suits the block (plus the mining efficiency attribute, Efficiency, when above 1). Haste,
 * mining fatigue, water and air time multiply both blocks alike, so they cancel out in the ratio.
 */
public final class BreakTimeCalculator {

    /** Hardness of the vanilla stand-in blocks. */
    public static final double MUSHROOM_BLOCK_HARDNESS = 0.2;
    public static final double CHORUS_PLANT_HARDNESS = 0.4;

    private BreakTimeCalculator() {
    }

    /** Tool speed against a block mined best with {@code blockTool}, including mining efficiency. */
    public static double toolSpeed(HeldTool held, ToolType blockTool, double miningEfficiency) {
        double speed = held.type() == blockTool && blockTool != ToolType.NONE ? held.speed() : 1.0;
        if (speed > 1.0) {
            speed += miningEfficiency;
        }
        return speed;
    }

    /** Per tick progress (1.0 = broken) without the factors shared by all blocks. */
    public static double progress(double toolSpeed, double hardness, boolean harvestable) {
        if (hardness < 0) {
            return 0;
        }
        if (hardness == 0) {
            return 1.0;
        }
        return toolSpeed / hardness / (harvestable ? 30.0 : 100.0);
    }

    /**
     * Factor for the block break speed attribute, so mining the stand-in takes as long as mining the
     * custom block would. 0 for unbreakable blocks.
     *
     * @param standInTool     tool kind that mines the stand-in fast (axe for mushroom blocks and chorus)
     * @param standInHardness hardness of the stand-in
     */
    public static double speedFactor(BlockProfile profile, HeldTool held, double miningEfficiency,
                                     ToolType standInTool, double standInHardness) {
        if (profile.unbreakable()) {
            return 0.0;
        }
        double wanted = progress(toolSpeed(held, profile.tool(), miningEfficiency), profile.hardness(),
                profile.canHarvest(held));
        double actual = progress(toolSpeed(held, standInTool, miningEfficiency), standInHardness, true);
        return actual <= 0 ? 1.0 : wanted / actual;
    }

    /** Ticks to break the block, as vanilla rounds it (instant if the progress reaches 1 in one tick). */
    public static int ticks(double progressPerTick) {
        if (progressPerTick <= 0) {
            return Integer.MAX_VALUE;
        }
        if (progressPerTick >= 1.0) {
            return 0;
        }
        // the epsilon keeps exact quotients like 45.000000001 from rounding up to the next tick
        return (int) Math.ceil(1.0 / progressPerTick - 1e-9);
    }
}
