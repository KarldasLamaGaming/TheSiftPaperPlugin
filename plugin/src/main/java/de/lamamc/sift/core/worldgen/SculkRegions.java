package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Surface sculk regions of the original (SiftSurfaceSculkRegionFeature): at most one region per 512×512
 * cell, an ellipse of 50–120 blocks made of 6–11 noisy blobs. Pure and deterministic per world seed, so
 * every chunk computes the same regions.
 */
public final class SculkRegions {

    public static final int CELL_SIZE = 512;
    public static final int MAX_RADIUS = 60;
    private static final int CENTER_MARGIN = 76;
    private static final int MIN_DIAMETER = 50;
    private static final int MAX_DIAMETER = 120;
    private static final int MIN_SCULK_DEPTH = 6;
    private static final int SCULK_DEPTH_VARIATION = 4;
    private static final long REGION_SALT = 5999732914757190983L;
    private static final long DECORATION_SALT = 4922508400677442629L;

    /** What sits on top of a core column. */
    public enum Decoration { NONE, CATALYST, SHRIEKER, SENSOR, VEIN }

    private SculkRegions() {
    }

    /** Regions that may reach into the chunk whose minimum corner is (minX, minZ). */
    public static List<Region> regionsTouching(long worldSeed, int minX, int minZ) {
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        List<Region> regions = new ArrayList<>();
        for (int cellX = Math.floorDiv(minX - MAX_RADIUS, CELL_SIZE); cellX <= Math.floorDiv(maxX + MAX_RADIUS, CELL_SIZE); cellX++) {
            for (int cellZ = Math.floorDiv(minZ - MAX_RADIUS, CELL_SIZE); cellZ <= Math.floorDiv(maxZ + MAX_RADIUS, CELL_SIZE); cellZ++) {
                Region region = create(worldSeed, cellX, cellZ);
                if (region.intersects(minX, minZ, maxX, maxZ)) {
                    regions.add(region);
                }
            }
        }
        return regions;
    }

    public static Region create(long worldSeed, int cellX, int cellZ) {
        long seed = mix(worldSeed ^ REGION_SALT ^ (long) cellX * -7046029254386353131L ^ (long) cellZ * -4417276706812531889L);
        Random random = new Random(seed);
        int availableCenterRange = 360; // CELL_SIZE - 2 * CENTER_MARGIN
        double centerX = (double) cellX * CELL_SIZE + CENTER_MARGIN + random.nextInt(availableCenterRange + 1);
        double centerZ = (double) cellZ * CELL_SIZE + CENTER_MARGIN + random.nextInt(availableCenterRange + 1);
        int diameter = MIN_DIAMETER + random.nextInt(MAX_DIAMETER - MIN_DIAMETER + 1);
        double majorRadius = diameter * 0.5;
        double minorRadius = majorRadius * (0.72 + random.nextDouble() * 0.23);
        double angle = random.nextDouble() * Math.PI;
        int blobCount = 6 + random.nextInt(6);
        Blob[] blobs = new Blob[blobCount];
        for (int index = 0; index < blobCount; index++) {
            double direction = random.nextDouble() * Math.PI * 2.0;
            double distance = index == 0 ? 0.0 : Math.sqrt(random.nextDouble()) * majorRadius * 0.68;
            double localX = Math.cos(direction) * distance;
            double localZ = Math.sin(direction) * distance * (minorRadius / majorRadius);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double blobCenterX = centerX + localX * cos - localZ * sin;
            double blobCenterZ = centerZ + localX * sin + localZ * cos;
            double blobRadius = majorRadius * (0.18 + random.nextDouble() * 0.18);
            double blobAspect = 0.62 + random.nextDouble() * 0.36;
            double blobAngle = random.nextDouble() * Math.PI;
            blobs[index] = new Blob(blobCenterX, blobCenterZ, blobRadius, blobRadius * blobAspect,
                    Math.cos(blobAngle), Math.sin(blobAngle), random.nextLong());
        }
        return new Region(centerX, centerZ, majorRadius, minorRadius, Math.cos(angle), Math.sin(angle), seed, blobs);
    }

    public record Region(double centerX, double centerZ, double majorRadius, double minorRadius, double cos, double sin,
                         long seed, Blob[] blobs) {

        boolean intersects(int minX, int minZ, int maxX, int maxZ) {
            return centerX + majorRadius >= minX && centerX - majorRadius <= maxX
                    && centerZ + majorRadius >= minZ && centerZ - majorRadius <= maxZ;
        }

        /** Column of solid sculk. */
        public boolean contains(int x, int z) {
            if (insideEnvelope(x, z)) {
                for (Blob blob : blobs) {
                    if (blob.contains(x, z)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Column next to the core (up to two blocks): gets a sculk vein. */
        public boolean isVeinFringe(int x, int z) {
            return insideEnvelope(x, z) && (contains(x + 1, z) || contains(x - 1, z) || contains(x, z + 1) || contains(x, z - 1)
                    || contains(x + 2, z) || contains(x - 2, z) || contains(x, z + 2) || contains(x, z - 2));
        }

        boolean touchesBlobEdge(int x, int z) {
            return !contains(x + 1, z) || !contains(x - 1, z) || !contains(x, z + 1) || !contains(x, z - 1);
        }

        /** How many blocks of the surface become sculk (6–9). */
        public int sculkDepth(int x, int z) {
            return MIN_SCULK_DEPTH + (int) Math.floorMod(mix(seed ^ positionSalt(x, z)), (long) SCULK_DEPTH_VARIATION);
        }

        /** Block on top of a core column (catalyst 0.15 %, shrieker 0.4 %, sensor 1.55 %, vein at blob edges). */
        public Decoration decoration(int x, int z) {
            double roll = unit(mix(seed ^ positionSalt(x, z) ^ DECORATION_SALT));
            if (roll < 0.0015) {
                return Decoration.CATALYST;
            }
            if (roll < 0.0055) {
                return Decoration.SHRIEKER;
            }
            if (roll < 0.021) {
                return Decoration.SENSOR;
            }
            return roll < 0.115 && touchesBlobEdge(x, z) ? Decoration.VEIN : Decoration.NONE;
        }

        private boolean insideEnvelope(int x, int z) {
            double dx = x + 0.5 - centerX;
            double dz = z + 0.5 - centerZ;
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            return localX * localX / (majorRadius * majorRadius) + localZ * localZ / (minorRadius * minorRadius) <= 1.0;
        }
    }

    public record Blob(double centerX, double centerZ, double radiusX, double radiusZ, double cos, double sin, long seed) {
        boolean contains(int x, int z) {
            double dx = x + 0.5 - centerX;
            double dz = z + 0.5 - centerZ;
            double localX = dx * cos + dz * sin;
            double localZ = -dx * sin + dz * cos;
            double ellipse = localX * localX / (radiusX * radiusX) + localZ * localZ / (radiusZ * radiusZ);
            double edgeNoise = (unit(mix(seed ^ positionSalt(x, z))) - 0.5) * 0.22;
            return ellipse <= 1.0 + edgeNoise;
        }
    }

    static long positionSalt(int x, int z) {
        return (long) x * 7146057691288625177L ^ (long) z * -7046029288634856825L;
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        return value ^ value >>> 31;
    }

    static double unit(long value) {
        return (double) (value >>> 11) * (double) 1.110223E-16F;
    }
}
