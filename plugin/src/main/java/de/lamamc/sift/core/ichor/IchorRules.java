package de.lamamc.sift.core.ichor;

/**
 * Rules of ichor (alpha.21, values of the original 1.1.1). In the Sift all water is ichor, so these apply
 * to water there.
 */
public final class IchorRules {

    /** Movement in ichor per tick (horizontal, vertical), on top of water drag. */
    public static final double DRAG_HORIZONTAL = 0.92;
    public static final double DRAG_VERTICAL = 0.94;
    /** Regeneration I for 101 ticks, refreshed while inside (the original refreshes every tick). */
    public static final int REGENERATION_TICKS = 101;

    private IchorRules() {
    }

    /**
     * What lava touching ichor turns into: a lava source becomes obsidian (vanilla already does), flowing
     * lava becomes cobbled siftslate, lava flowing onto ichor makes siftslate. Input is the vanilla block id
     * the game would form ("cobblestone", "stone", "obsidian"); the result is a Sift block id, or null for
     * "keep the vanilla result".
     */
    public static String lavaResult(String vanillaFormed) {
        return switch (vanillaFormed) {
            case "cobblestone" -> "cobbled_siftslate";
            case "stone" -> "siftslate";
            default -> null;
        };
    }

    /** The drag factor for a check every {@code interval} ticks (the original applies it every tick). */
    public static double drag(double perTick, int interval) {
        return Math.pow(perTick, Math.max(1, interval));
    }

    /** Duration to apply so the effect never runs out between checks every {@code interval} ticks. */
    public static int regenerationDuration(int configured, int interval) {
        return Math.max(configured, interval + 1);
    }
}
