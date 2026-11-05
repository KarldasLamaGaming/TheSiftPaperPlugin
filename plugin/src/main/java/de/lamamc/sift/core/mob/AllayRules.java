package de.lamamc.sift.core.mob;

import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Allay spawning in the Sift (alpha.31), the rules of SiftAllaySpawner of the original 1.1.1: every 80 ticks,
 * for the first player in the Sift for whom it works, up to 18 points on a golden-angle spiral 34–80 blocks
 * away, 6 blocks above the surface. The first point in a biome with a profile decides: with the profile's
 * chance a group of allays spawns there, as many as the local cap (within 96 × 48 blocks) leaves room for.
 */
public final class AllayRules {

    /** chance per check, group size, cap within the local box. */
    public record Profile(double chance, int minGroup, int maxGroup, int localCap) {
    }

    /** The original's profiles: overgrown forest, sift wastes, overgrown clearing. */
    public static final Map<String, Profile> DEFAULTS = Map.of(
            "overgrown_forest", new Profile(0.72, 2, 4, 16),
            "sift_wastes", new Profile(0.5, 2, 3, 12),
            "overgrown_clearing", new Profile(0.32, 1, 3, 8));

    private AllayRules() {
    }

    /** Horizontal offset of spiral attempt {@code attempt}; draws the radius from {@code random}. */
    public static int[] offset(double phase, int attempt, int minDistance, int maxDistance, RandomGenerator random) {
        double angle = phase + attempt * SpawnRules.GOLDEN_ANGLE;
        int radius = minDistance + random.nextInt(maxDistance - minDistance + 1);
        return new int[] {(int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius)};
    }

    /**
     * Size of the group to spawn at a point of this profile, or 0: the chance failed (the original then gives up
     * for this player), or the local population leaves no room for the smallest group.
     */
    public static int groupSize(Profile profile, int localPopulation, RandomGenerator random) {
        if (random.nextDouble() >= profile.chance()) {
            return 0;
        }
        int available = profile.localCap() - localPopulation;
        if (available < profile.minGroup()) {
            return 0;
        }
        int max = Math.min(profile.maxGroup(), available);
        return profile.minGroup() + random.nextInt(max - profile.minGroup() + 1);
    }
}
