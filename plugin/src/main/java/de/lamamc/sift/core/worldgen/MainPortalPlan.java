package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geometry of the main Sift portal (SiftPortalStructure of the original): footprint 9×19×40, the ground
 * and cliff height profiles of the template, the search for a flat site near 0/0 and the terrain shapes
 * that blend the portal into the landscape. Pure; the world side lives in world.MainPortalBuilder.
 */
public final class MainPortalPlan {

    public static final int SIZE_X = 9;
    public static final int SIZE_Y = 19;
    public static final int SIZE_Z = 40;
    /** Template position of the marker block (reinforced siftslate) and of the arrival point above it. */
    public static final int MARKER_X = 3;
    public static final int MARKER_Y = 4;
    public static final int MARKER_Z = 19;
    public static final int PLAYER_Y = 5;
    /** The opening of the template (its 120 sift_portal blocks): x 3, y 5..10, z 10..29, so the frame is 22 × 8. */
    public static final int OPENING_FRAME_X = 3;
    public static final int OPENING_FRAME_Y = 4;
    public static final int OPENING_FRAME_Z = 9;
    public static final int OPENING_FRAME_WIDTH = 22;
    public static final int OPENING_FRAME_HEIGHT = 8;
    public static final int BLEND_RADIUS = 52;
    public static final int FOUNDATION_BLEND_RADIUS = 15;
    public static final int SEARCH_RADIUS = 96;
    public static final int COARSE_STEP = 16;
    public static final int REFINE_RADIUS = 16;
    public static final int REFINE_STEP = 4;
    public static final int OUTER_SAMPLE_MARGIN = 12;
    /** Plants and trees are cleared this far around the footprint. */
    public static final int FEATURE_CLEAR_MARGIN = 15;
    private static final int STRUCTURE_GROUND_RELATIVE_Y = MARKER_Y - 4;

    private static final int[][] GROUND_PROFILE = new int[][] {
            {-1, -1, -1, -1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, 1, 7, 14, 1, 6, 4, 3, 3, 3, 2, -1, -1, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 4, -1, 1, 12, 16, 14, 2, -1, -1, -1, -1},
            {-1, -1, -1, -1, 0, 17, 17, 12, 18, 6, 5, 4, 3, 3, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 6, 7, 17, 17, 17, 14, 14, -1, -1, -1, -1},
            {-1, -1, -1, -1, 0, 17, 18, 18, 18, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 16, 17, 17, 15, 14, -1, -1, -1, -1},
            {-1, -1, -1, -1, 0, 17, 17, 18, 18, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 5, 5, 6, 16, 17, 17, 15, 15, -1, -1, -1, -1},
            {-1, -1, -1, -1, 0, 17, 17, 2, 6, 4, 3, 4, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 2, 3, 3, 2, 4, 6, 17, 16, 15, 15, -1, -1, -1, -1},
            {-1, -1, -1, -1, 0, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 1, 0, 2, 2, 2, 3, 3, 5, 5, 5, 4, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, -1, 1, 2, 2, -1, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}
    };
    private static final int[][] CLIFF_PROFILE = new int[][] {
            {-1, -1, -1, -1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, 15, 17, 17, 17, 17, 17, 17, 17, 17, 16, 16, 16, 17, 16, 16, 17, 17, 17, 17, 17, 17, 17, 16, 16, 17, 17, 17, 17, 17, 16, 14, 14, -1, -1, -1, -1},
            {9, 10, 10, 9, 16, 17, 17, 17, 18, 18, 17, 17, 17, 16, 16, 16, 17, 16, 16, 17, 17, 17, 18, 17, 17, 17, 17, 17, 17, 17, 16, 17, 17, 17, 14, 14, 12, 12, 12, 11},
            {10, 10, 10, 10, 17, 17, 18, 18, 18, 18, 18, 17, 17, 16, 17, 16, 17, 17, 16, 17, 17, 18, 18, 18, 18, 18, 17, 17, 17, 16, 16, 16, 17, 17, 15, 14, 12, 12, 12, 12},
            {10, 10, 10, 10, 17, 17, 17, 18, 18, 18, 17, 17, 17, 16, 16, 16, 17, 17, 17, 17, 17, 18, 17, 17, 18, 18, 17, 17, 17, 17, 17, 16, 17, 17, 15, 15, 12, 12, 12, 12},
            {-1, -1, -1, -1, 17, 17, 17, 17, 17, 17, 17, 17, 16, 16, 16, 17, 17, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 16, 15, 15, 3, -1, -1, -1},
            {-1, -1, -1, -1, 0, 0, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 1, 0, 2, 2, 2, 3, 3, 5, 5, 5, 4, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, -1, 1, 2, 2, -1, -1, -1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}
    };
    private static final int[][] GROUND_POINTS = points(GROUND_PROFILE);
    private static final int[][] CLIFF_POINTS = points(CLIFF_PROFILE);

    private MainPortalPlan() {
    }

    /** Terrain height (y of the top terrain block) at a column, from the world or a test. */
    @FunctionalInterface
    public interface Heights {
        int surfaceY(int x, int z);
    }

    public record Site(int anchorX, int anchorZ, int groundY, double roughness) {

        /** Minimum corner of the template. */
        public int originX() {
            return anchorX - MARKER_X;
        }

        public int originY() {
            return Math.clamp(groundY - STRUCTURE_GROUND_RELATIVE_Y, 0, 256 - SIZE_Y);
        }

        public int originZ() {
            return anchorZ - MARKER_Z;
        }
    }

    record Sample(int medianHeight, int range, int outerRange, double roughness) {
    }

    /** The flattest site near 0/0: coarse search ±96 in steps of 16, then ±16 in steps of 4. */
    public static Site findSite(Heights heights) {
        Site coarse = search(heights, -SEARCH_RADIUS, SEARCH_RADIUS, -SEARCH_RADIUS, SEARCH_RADIUS, COARSE_STEP);
        if (coarse == null) {
            return new Site(0, 0, heights.surfaceY(0, 0), 999.0);
        }
        Site refined = search(heights, coarse.anchorX() - REFINE_RADIUS, coarse.anchorX() + REFINE_RADIUS,
                coarse.anchorZ() - REFINE_RADIUS, coarse.anchorZ() + REFINE_RADIUS, REFINE_STEP);
        return refined != null ? refined : coarse;
    }

    /** Columns whose height findSite reads (for loading the chunks first). */
    public static List<int[]> sampleColumns() {
        List<int[]> columns = new ArrayList<>();
        Heights recorder = (x, z) -> {
            columns.add(new int[] {x, z});
            return 64;
        };
        search(recorder, -SEARCH_RADIUS, SEARCH_RADIUS, -SEARCH_RADIUS, SEARCH_RADIUS, COARSE_STEP);
        return columns;
    }

    private static Site search(Heights heights, int minX, int maxX, int minZ, int maxZ, int step) {
        Site best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                Sample sample = sample(heights, x, z);
                double score = sample.roughness() * 18.0 + Math.sqrt((double) x * x + (double) z * z) * 0.8
                        + sample.range() * 135.0 + sample.outerRange() * 75.0;
                if (sample.medianHeight() < 12 || sample.medianHeight() > 232) {
                    score += 10000.0;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = new Site(x, z, sample.medianHeight(), sample.roughness());
                }
            }
        }
        return best;
    }

    static Sample sample(Heights h, int anchorX, int anchorZ) {
        int minX = anchorX - MARKER_X;
        int minZ = anchorZ - MARKER_Z;
        int maxX = minX + SIZE_X - 1;
        int maxZ = minZ + SIZE_Z - 1;
        int midX = (minX + maxX) / 2;
        int midZ = (minZ + maxZ) / 2;
        int[] core = {h.surfaceY(minX, minZ), h.surfaceY(midX, minZ), h.surfaceY(maxX, minZ), h.surfaceY(minX, midZ),
                h.surfaceY(midX, midZ), h.surfaceY(maxX, midZ), h.surfaceY(minX, maxZ), h.surfaceY(midX, maxZ),
                h.surfaceY(maxX, maxZ)};
        int oMinX = minX - OUTER_SAMPLE_MARGIN;
        int oMaxX = maxX + OUTER_SAMPLE_MARGIN;
        int oMinZ = minZ - OUTER_SAMPLE_MARGIN;
        int oMaxZ = maxZ + OUTER_SAMPLE_MARGIN;
        int oMidX = (oMinX + oMaxX) / 2;
        int oMidZ = (oMinZ + oMaxZ) / 2;
        int[] outer = {h.surfaceY(oMinX, oMinZ), h.surfaceY(oMidX, oMinZ), h.surfaceY(oMaxX, oMinZ), h.surfaceY(oMinX, oMidZ),
                h.surfaceY(oMaxX, oMidZ), h.surfaceY(oMinX, oMaxZ), h.surfaceY(oMidX, oMaxZ), h.surfaceY(oMaxX, oMaxZ)};
        int[] sorted = core.clone();
        Arrays.sort(sorted);
        int median = sorted[sorted.length / 2];
        int coreMin = Arrays.stream(core).min().orElse(median);
        int coreMax = Arrays.stream(core).max().orElse(median);
        int outerMin = Arrays.stream(outer).min().orElse(median);
        int outerMax = Arrays.stream(outer).max().orElse(median);
        double roughness = 0;
        for (int height : core) {
            roughness += Math.abs(height - median);
        }
        for (int height : outer) {
            roughness += Math.abs(height - median) * 0.45;
        }
        roughness /= core.length + outer.length * 0.45;
        return new Sample(median, coreMax - coreMin, Math.max(coreMax, outerMax) - Math.min(coreMin, outerMin), roughness);
    }

    /** Ground height of the template at a template column (relative y), or -1 outside the base. */
    public static int groundProfile(int localX, int localZ) {
        if (localX < 0 || localX >= SIZE_X || localZ < 0 || localZ >= SIZE_Z) {
            return -1;
        }
        return GROUND_PROFILE[localX][localZ];
    }

    public static boolean isBaseColumn(int localX, int localZ) {
        return groundProfile(localX, localZ) >= 0;
    }

    /**
     * Foundation around the base: target top y for a column within 15 blocks, or Integer.MIN_VALUE if the
     * column stays. Columns near the base become plain siftslate, farther ones keep their biome surface.
     */
    public static int foundationTargetY(int localX, int localZ, long seed, int foundationTopY) {
        boolean core = isBaseColumn(localX, localZ);
        double broadNoise = blendNoise(seed ^ 7640891576956012809L, localX * 3 + 11, localZ * 3 - 17);
        double fineNoise = blendNoise(seed ^ -4942790177534073029L, localX * 9 - 23, localZ * 9 + 31);
        double distance = core ? 0.0 : distanceToBase(localX + broadNoise * 1.45, localZ - broadNoise * 1.15);
        if (distance > FOUNDATION_BLEND_RADIUS) {
            return Integer.MIN_VALUE;
        }
        double erosion = distance * (0.7 + fineNoise * 0.08) + distance * distance * 0.016;
        return core ? foundationTopY : foundationTopY - Math.max(1, (int) Math.floor(erosion));
    }

    /** Whether a foundation column keeps the surface block of its biome. */
    public static boolean foundationKeepsSurface(int localX, int localZ, long seed) {
        if (isBaseColumn(localX, localZ)) {
            return false;
        }
        double broadNoise = blendNoise(seed ^ 7640891576956012809L, localX * 3 + 11, localZ * 3 - 17);
        return distanceToBase(localX + broadNoise * 1.45, localZ - broadNoise * 1.15) > 2.25;
    }

    static double distanceToBase(double localX, double localZ) {
        double best = Double.POSITIVE_INFINITY;
        for (int[] p : GROUND_POINTS) {
            double dx = localX - p[0];
            double dz = localZ - p[1];
            best = Math.min(best, dx * dx + dz * dz);
        }
        return Math.sqrt(best);
    }

    /**
     * Height of the cliffs around the portal relative to its origin for a column outside the footprint
     * (within 52 blocks); <= 0 means no raise. In front of the openings the terrain stays low.
     */
    public static double envelopeRelativeY(int localX, int localZ, long seed) {
        double envelope = terrainEnvelope(localX, localZ, seed);
        double approach = approachMask(localX, localZ);
        if (approach > 0) {
            double lowForecourt = 3.6 + blendNoise(seed ^ -3335678366873096957L, localX, localZ) * 0.55;
            envelope = envelope * (1.0 - approach) + Math.min(envelope, lowForecourt) * approach;
        }
        return envelope;
    }

    static double terrainEnvelope(int localX, int localZ, long seed) {
        double broadWarp = blendNoise(seed ^ 1375183133L, localX * 3 + 17, localZ * 3 - 29);
        double fineWarp = blendNoise(seed ^ 2082071971L, localX * 7 - 41, localZ * 7 + 13);
        double warpedX = localX + broadWarp * 1.65 + fineWarp * 0.35;
        double warpedZ = localZ - broadWarp * 1.1 + fineWarp * 0.45;
        double columnNoise = blendNoise(seed ^ -6752110988234923001L, localX * 5 + 31, localZ * 5 - 47);
        double erosionPerBlock = 0.405 + columnNoise * 0.022;
        double best = Double.NEGATIVE_INFINITY;
        for (int[] p : CLIFF_POINTS) {
            double dx = (warpedX - p[0]) * 0.76;
            double dz = (warpedZ - p[1]) * 1.08;
            double candidate = p[2] - Math.sqrt(dx * dx + dz * dz) * erosionPerBlock;
            best = Math.max(best, candidate);
        }
        best += columnNoise * 0.55;
        return Double.isFinite(best) ? best : -1.0;
    }

    static double approachMask(int localX, int localZ) {
        int distanceZ = localZ < 9 ? 9 - localZ : localZ > 30 ? localZ - 30 : 0;
        int distanceX;
        if (localX < 0) {
            distanceX = -localX;
        } else if (localX < SIZE_X) {
            return 0.0;
        } else {
            distanceX = localX - 8;
        }
        double zMask = 1.0 - smootherstep(distanceZ / 6.0);
        double xMask = 1.0 - smootherstep(Math.max(0.0, distanceX - 2.0) / 18.0);
        return Math.clamp(zMask * xMask, 0.0, 1.0);
    }

    /** Euclidean distance of a column to the footprint rectangle (0 inside). */
    public static double distanceOutsideRectangle(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        int dx = x < minX ? minX - x : Math.max(0, x - maxX);
        int dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /** Columns around the arrival point that stay free of plants. */
    public static boolean isApproach(int x, int z, int anchorX, int anchorZ) {
        return Math.abs(x - anchorX) <= 4 && Math.abs(z - anchorZ) <= 5;
    }

    static double blendNoise(long seed, int x, int z) {
        double phase = (seed & 65535L) * 1.73E-4;
        double a = Math.sin(x * 0.083 + z * 0.031 + phase);
        double b = Math.cos(x * 0.037 - z * 0.071 - phase * 0.73);
        double c = Math.sin((x + z) * 0.021 + phase * 1.91);
        return a * 0.5 + b * 0.33 + c * 0.17;
    }

    private static double smootherstep(double value) {
        double t = Math.clamp(value, 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static int[][] points(int[][] profile) {
        List<int[]> points = new ArrayList<>();
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                if (profile[x][z] >= 0) {
                    points.add(new int[] {x, z, profile[x][z]});
                }
            }
        }
        return points.toArray(int[][]::new);
    }
}
