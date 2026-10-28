package de.lamamc.sift.core;

/**
 * What the player mines with. {@code speed} is the tool's speed against blocks of its kind (tier speed
 * for vanilla tools, configured for Sift tools); {@code level} as in {@link ToolTier#level()}.
 */
public record HeldTool(ToolType type, double speed, int level) {

    public static final HeldTool HAND = new HeldTool(ToolType.NONE, 1.0, -1);

    public static HeldTool of(ToolType type, ToolTier tier) {
        return new HeldTool(type, tier.speed(), tier.level());
    }
}
