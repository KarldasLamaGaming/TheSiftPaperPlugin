package de.lamamc.sift.core;

/**
 * Vanilla tool materials: mining speed and the level used for "needs_x_tool" checks
 * (wood/gold 0, stone/copper 1, iron 2, diamond 3, netherite 4).
 */
public enum ToolTier {
    NONE(1.0, -1),
    WOOD(2.0, 0),
    GOLD(12.0, 0),
    STONE(4.0, 1),
    COPPER(5.0, 1),
    IRON(6.0, 2),
    DIAMOND(8.0, 3),
    NETHERITE(9.0, 4);

    private final double speed;
    private final int level;

    ToolTier(double speed, int level) {
        this.speed = speed;
        this.level = level;
    }

    public double speed() {
        return speed;
    }

    public int level() {
        return level;
    }

    /** Whether a tool of this tier may harvest a block that needs {@code required}. */
    public boolean atLeast(ToolTier required) {
        return level >= required.level;
    }
}
