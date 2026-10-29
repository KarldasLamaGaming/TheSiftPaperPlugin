package de.lamamc.sift.core;

import java.util.Map;

/** Rules of the willow wood set: log axis from the clicked face, axe stripping. */
public final class WoodRules {

    private static final Map<String, String> STRIPPED = Map.of(
            "overgrown_willow_log", "stripped_overgrown_willow_log",
            "overgrown_willow_wood", "stripped_overgrown_willow_wood");

    private WoodRules() {
    }

    /** Axis of a log placed against a face (Bukkit BlockFace name), as vanilla RotatedPillarBlock does. */
    public static String axisFor(String face) {
        return switch (face) {
            case "EAST", "WEST" -> "x";
            case "NORTH", "SOUTH" -> "z";
            default -> "y";
        };
    }

    /** The stripped variant of a block id, or null if it cannot be stripped. */
    public static String strippedOf(String id) {
        return STRIPPED.get(id);
    }
}
