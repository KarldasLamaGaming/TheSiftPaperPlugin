package de.lamamc.sift.core.worldgen;

import java.util.List;

/**
 * Geometry of the abandoned portals (AbandonedMainPortalFeature of the original, version 1.1.1): the
 * template's natural blocks are centred on the chunk centre, the columns under them may differ by at
 * most 18 blocks, the template sits one block above their median, and a foundation within about 12
 * blocks moves the surrounding terrain at most 8 blocks towards the template floor.
 */
public final class AbandonedPortalPlan {

    public static final int CELL_CHUNKS = 20;
    public static final int MAX_RELIEF = 18;
    public static final int FOUNDATION_BLEND_RADIUS = 12;
    public static final int MAX_EARTHWORK = 8;
    public static final int TEMPLATE_VARIANTS = 5;
    /**
     * Share of the candidates that may try to build. 1.0 is the original; until the phase 3 features
     * (soul canyons, ichor lakes, lava floors, monoliths, spikes) take their space, 0.25 brings the count
     * close to it (seed "lamakingdom", 49 candidates: original 6 built, plugin 24 without this chance).
     */
    public static final double DEFAULT_CHANCE = 0.25;
    private static final long CHANCE_SALT = 0x5A1F7C0DE1A5B0B5L;
    /** Half size of the square the portal reserves against other solid features (centre −23 … +22). */
    public static final int RESERVE_BELOW = 23;
    public static final int RESERVE_ABOVE = 22;

    private AbandonedPortalPlan() {
    }

    /** Template origin on one axis so that the natural blocks {@code min … max} are centred on {@code center}. */
    public static int centredOrigin(int center, int min, int max) {
        return center - Math.floorDiv(min + max + 1, 2);
    }

    /** Height difference of the sorted surface heights under the template. */
    public static int relief(List<Integer> sortedHeights) {
        return sortedHeights.getLast() - sortedHeights.getFirst();
    }

    /** Floor of the template: one block above the median surface. */
    public static int groundY(List<Integer> sortedHeights) {
        return sortedHeights.get(sortedHeights.size() / 2) + 1;
    }

    /** Noisy edge of the foundation around the template footprint. */
    public static double foundationEdge(int x, int z, long noiseSalt) {
        return FOUNDATION_BLEND_RADIUS + blendNoise(x, z, noiseSalt) * 1.35;
    }

    /**
     * New top of a foundation column. Under the template ({@code core}) the floor itself; around it the
     * natural top moved towards the floor by at most {@link #MAX_EARTHWORK} blocks, fading out with a
     * smoothstep towards the edge and within 3 blocks of the generation bounds.
     */
    public static int foundationTop(boolean core, double distance, double edge, int floorY, int naturalTop, int distanceToEdge) {
        if (core) {
            return floorY;
        }
        double t = Math.max(0.0, 1.0 - distance / edge);
        double weight = t * t * (3.0 - 2.0 * t);
        weight *= Math.min(1.0, distanceToEdge / 3.0);
        int adjustment = Math.max(-MAX_EARTHWORK, Math.min(MAX_EARTHWORK, floorY - naturalTop));
        return naturalTop + (int) Math.round(adjustment * weight);
    }

    /** Under the template the natural ground may be at most this far from the floor. */
    public static boolean coreFits(int floorY, int naturalTop) {
        return Math.abs(floorY - naturalTop) <= MAX_EARTHWORK;
    }

    /** Whether the candidate in this chunk may try to build; fixed per seed and chunk. */
    public static boolean passesChance(long seed, int chunkX, int chunkZ, double chance) {
        if (chance >= 1.0) {
            return true;
        }
        long value = seed ^ CHANCE_SALT ^ (long) chunkX * -7046029254386353131L ^ (long) chunkZ * -4417276706812531889L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53 < chance;
    }

    /** Vanilla BlockPos#asLong (26 bits x, 26 bits z, 12 bits y); seeds the foundation noise. */
    public static long packPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) | ((long) z & 0x3FFFFFFL) << 12;
    }

    static double blendNoise(int x, int z, long salt) {
        double phase = (salt & 65535L) * 1.91E-4;
        double broad = Math.sin(x * 0.087 + z * 0.039 + phase);
        double cross = Math.cos(x * 0.043 - z * 0.074 - phase * 0.71);
        double detail = Math.sin((x + z) * 0.023 + phase * 1.83);
        return broad * 0.5 + cross * 0.33 + detail * 0.17;
    }
}
