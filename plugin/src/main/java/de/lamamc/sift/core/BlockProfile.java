package de.lamamc.sift.core;

/**
 * How a custom block behaves when mined, independent of the vanilla block that stands in for it.
 *
 * @param hardness       vanilla destroy time (deepslate 3.0); ignored when unbreakable
 * @param tool           the tool kind that mines it fast
 * @param requiresTool   true if it only drops (and mines at full speed) with the right tool
 * @param minTier        lowest tool tier that harvests it (only with requiresTool)
 * @param explosionProof explosions leave it in place
 */
public record BlockProfile(String id, double hardness, ToolType tool, boolean requiresTool, ToolTier minTier,
                           boolean unbreakable, boolean explosionProof, DropSpec drops, int xpMin, int xpMax) {

    /** Whether {@code held} is the right tool to harvest this block (always true if no tool is required). */
    public boolean canHarvest(HeldTool held) {
        if (!requiresTool) {
            return true;
        }
        return held.type() == tool && held.level() >= minTier.level();
    }
}
