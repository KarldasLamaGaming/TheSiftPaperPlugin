package de.lamamc.sift.core.worldgen;

/**
 * Geometry of the abandoned portals (AbandonedMainPortalFeature of the original): one per 32×32 chunks,
 * only on terrain with at most 20 blocks of relief along the template, with a foundation that slopes
 * from the template base into the surrounding terrain within 15 blocks.
 */
public final class AbandonedPortalPlan {

    public static final int CELL_CHUNKS = 32;
    public static final int PIVOT_X = 4;
    public static final int PIVOT_Z = 19;
    public static final int MAX_RELIEF = 20;
    public static final int FOUNDATION_BLEND_RADIUS = 15;
    public static final int TEMPLATE_VARIANTS = 5;

    private AbandonedPortalPlan() {
    }

    /** Relief check along the template (3 × 5 samples, rotated with it). */
    public static boolean terrainFits(MainPortalPlan.Heights heights, int centerX, int centerZ,
                                      TemplateTransform.Rotation rotation) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int lateral = -4; lateral <= 4; lateral += 4) {
            for (int lengthwise = -16; lengthwise <= 16; lengthwise += 8) {
                int[] offset = rotateOffset(lateral, lengthwise, rotation);
                int height = heights.surfaceY(centerX + offset[0], centerZ + offset[1]);
                min = Math.min(min, height);
                max = Math.max(max, height);
                if (max - min > MAX_RELIEF) {
                    return false;
                }
            }
        }
        return true;
    }

    static int[] rotateOffset(int x, int z, TemplateTransform.Rotation rotation) {
        return switch (rotation) {
            case NONE -> new int[] {x, z};
            case CLOCKWISE_90 -> new int[] {-z, x};
            case CLOCKWISE_180 -> new int[] {-x, -z};
            case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
        };
    }

    /**
     * Top y of the foundation for a column at {@code distance} from the template base (0 = under the
     * base), or Integer.MIN_VALUE beyond the noisy blend edge.
     */
    public static int foundationTargetY(int x, int z, double distance, boolean core, int baseY, long noiseSalt) {
        double edge = FOUNDATION_BLEND_RADIUS + blendNoise(x, z, noiseSalt) * 1.35;
        if (distance > edge) {
            return Integer.MIN_VALUE;
        }
        double fineNoise = blendNoise(x * 3 + 17, z * 3 - 29, noiseSalt ^ -7046029254386353131L);
        double erosion = distance * (0.62 + fineNoise * 0.075) + distance * distance * 0.021;
        return core ? baseY - 1 : baseY - 1 - Math.max(1, (int) Math.floor(erosion));
    }

    static double blendNoise(int x, int z, long salt) {
        double phase = (salt & 65535L) * 1.91E-4;
        double broad = Math.sin(x * 0.087 + z * 0.039 + phase);
        double cross = Math.cos(x * 0.043 - z * 0.074 - phase * 0.71);
        double detail = Math.sin((x + z) * 0.023 + phase * 1.83);
        return broad * 0.5 + cross * 0.33 + detail * 0.17;
    }
}
