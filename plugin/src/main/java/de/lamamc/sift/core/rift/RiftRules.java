package de.lamamc.sift.core.rift;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules of rifts (alpha.22, values of the original 1.1.1): the space a rift needs, where a counter rift is
 * searched, and the zone mobs keep out of. No Bukkit types, so it can be tested.
 */
public final class RiftRules {

    /** Half the width along the rift (9 blocks: -4..+4) and its height in blocks. */
    public static final int HALF_WIDTH = 4;
    public static final int HEIGHT = 5;
    /** A solid block must lie this many blocks below the base (1..3). */
    public static final int MAX_GROUND_GAP = 3;

    /** What the world looks like at a block. */
    public interface Space {
        /** Air or a small plant, no fluid, no leaves. */
        boolean open(int x, int y, int z);

        /** A solid block that is not a plant. */
        boolean solid(int x, int y, int z);

        /** Inside the world border and the build height. */
        boolean inside(int x, int y, int z);
    }

    private RiftRules() {
    }

    /**
     * Whether a rift fits with its base at (x, y, z): 9 wide (along X or Z) and 5 high of open space, and
     * ground 1 to 3 blocks below the middle and both ends.
     */
    public static boolean fits(Space space, int x, int y, int z, boolean alongX) {
        for (int i = -HALF_WIDTH; i <= HALF_WIDTH; i++) {
            int bx = alongX ? x + i : x;
            int bz = alongX ? z : z + i;
            for (int dy = 0; dy < HEIGHT; dy++) {
                if (!space.inside(bx, y + dy, bz) || !space.open(bx, y + dy, bz)) {
                    return false;
                }
            }
        }
        for (int i : new int[] {-HALF_WIDTH, 0, HALF_WIDTH}) {
            int bx = alongX ? x + i : x;
            int bz = alongX ? z : z + i;
            if (!grounded(space, bx, y, bz)) {
                return false;
            }
        }
        return true;
    }

    private static boolean grounded(Space space, int x, int y, int z) {
        for (int d = 1; d <= MAX_GROUND_GAP; d++) {
            if (space.solid(x, y - d, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Columns to try for a counter rift around an anchor, nearest first: the anchor itself, then rings from
     * {@code minRadius} to {@code maxRadius} in {@code step}, {@code angles} points each.
     */
    public static List<int[]> ringColumns(int x, int z, int minRadius, int maxRadius, int step, int angles) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] {x, z});
        int n = Math.max(1, angles);
        for (int r = Math.max(1, minRadius); r <= maxRadius; r += Math.max(1, step)) {
            for (int a = 0; a < n; a++) {
                double angle = Math.PI * 2 * a / n;
                out.add(new int[] {x + (int) Math.round(Math.cos(angle) * r), z + (int) Math.round(Math.sin(angle) * r)});
            }
        }
        return out;
    }

    /** A random spot for a natural rift: {@code distance} blocks away at {@code angle} (radians). */
    public static int[] around(double x, double z, double angle, double distance) {
        return new int[] {(int) Math.floor(x + Math.cos(angle) * distance), (int) Math.floor(z + Math.sin(angle) * distance)};
    }

    /**
     * Bounds of a rift with its block anchor at (x, y, z): 9 wide along the axis, 1 thick, from 0.625 to
     * 4.875 above the base (the original's box). {minX, minY, minZ, maxX, maxY, maxZ}.
     */
    public static double[] bounds(int x, int y, int z, boolean alongX) {
        double cx = x + 0.5;
        double cz = z + 0.5;
        double halfX = alongX ? 4.5 : 0.5;
        double halfZ = alongX ? 0.5 : 4.5;
        return new double[] {cx - halfX, y + 0.625, cz - halfZ, cx + halfX, y + 4.875, cz + halfZ};
    }

    /** The zone a mob of this size keeps out of (the original's path cost area around a rift). */
    public static double[] dangerZone(double[] bounds, double mobWidth, double mobHeight) {
        double grow = mobWidth * 0.5 + 1.0;
        return new double[] {bounds[0] - grow, bounds[1] - mobHeight, bounds[2] - grow,
                bounds[3] + grow, bounds[4] + 0.5, bounds[5] + grow};
    }

    public static boolean contains(double[] box, double x, double y, double z) {
        return x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5];
    }

    /** Delay until the next natural rift of a player: base plus a random part (0..spread). */
    public static long nextDelay(long base, long spread, double random) {
        return base + (long) Math.floor(Math.max(0, spread) * Math.min(0.999999, Math.max(0, random)));
    }
}
