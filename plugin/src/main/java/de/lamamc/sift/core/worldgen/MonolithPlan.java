package de.lamamc.sift.core.worldgen;

import java.util.Random;
import java.util.Set;

/**
 * Lush and bare monoliths (alpha.27), a port of SiftMonolithFeature of the original 1.1.1: a leaning,
 * tapering siftslate pillar with terraces, an irregular edge, a root flare into the ground and a cap
 * (siftslate growth with plants on lush ones), plus smaller satellite pillars around it.
 */
public final class MonolithPlan {

    /** SiftMonolithFeature#isSiftTerrain. */
    public static final Set<String> TERRAIN = Set.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk",
            "dry_healthy_sculk_growth");
    /** Small plants a monolith or arch may overwrite (SiftMonolithFeature#canReplace). */
    public static final Set<String> PLANTS = Set.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds", "siftslate_stalks",
            "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts");
    public static final String SIFTSLATE = "siftslate";
    public static final String GROWTH = "siftslate_growth";

    /**
     * What was built. {@code reason} is null when a monolith stands; x/y/z is its foot (first free y of
     * the ground), top* the centre of its cap.
     */
    public record Result(String reason, int x, int y, int z, int topX, int topY, int topZ, int radius, int height, int satellites,
                         int blocks) {

        public boolean built() {
            return reason == null;
        }

        static Result rejected(String reason, int x, int y, int z) {
            return new Result(reason, x, y, z, x, y, z, 0, 0, 0, 0);
        }
    }

    private record Pillar(int placed, int topX, int topY, int topZ) {
    }

    private MonolithPlan() {
    }

    /** Tries a monolith in this chunk. {@code portalClearance} 22 and {@code reserveRadius} 22 in the original. */
    public static Result place(LandformWorld w, Random random, int chunkX, int chunkZ, boolean lush, int portalClearance,
                               int reserveRadius) {
        int centerX = (chunkX << 4) + 8 + random.nextInt(5) - 2;
        int centerZ = (chunkZ << 4) + 8 + random.nextInt(5) - 2;
        int centerY = surface(w, centerX, centerZ);
        if (centerY < 0) {
            return Result.rejected("kein Sift-Boden", centerX, 0, centerZ);
        }
        if (!drySupported(w, centerX, centerY, centerZ, 6)) {
            return Result.rejected("Boden nicht trocken oder zu dünn", centerX, centerY, centerZ);
        }
        int radius = lush ? 5 + random.nextInt(3) : 4 + random.nextInt(3);
        if (w.nearPortal(centerX, centerZ, portalClearance)) {
            return Result.rejected("zu nah am Haupt-Portal", centerX, centerY, centerZ);
        }
        if (!stableFootprint(w, centerX, centerY, centerZ, radius, lush ? 14 : 12)) {
            return Result.rejected("Gelände uneben", centerX, centerY, centerZ);
        }
        int requestedHeight = lush ? 31 + random.nextInt(25) : 24 + random.nextInt(21);
        int height = Math.min(requestedHeight, w.maxY() - 1 - centerY - 3);
        if (height < 18) {
            return Result.rejected("zu nah an der Bauhöhe", centerX, centerY, centerZ);
        }
        if (!w.reserve(centerX - reserveRadius, centerZ - reserveRadius, centerX + reserveRadius, centerZ + reserveRadius)) {
            return Result.rejected("Platz reserviert", centerX, centerY, centerZ);
        }
        Pillar main = pillar(w, random, centerX, centerY, centerZ, radius, height, lush, true);
        int placed = main.placed();
        int satellites = lush ? 1 + random.nextInt(3) : random.nextInt(2);
        int built = 0;
        for (int i = 0; i < satellites; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = radius + 4 + random.nextInt(4);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int z = centerZ + (int) Math.round(Math.sin(angle) * distance);
            int y = surface(w, x, z);
            if (y < 0) {
                continue;
            }
            int satelliteRadius = 2 + random.nextInt(2);
            if (drySupported(w, x, y, z, 6) && stableFootprint(w, x, y, z, satelliteRadius, 10)) {
                int satelliteHeight = Math.min(13 + random.nextInt(Math.max(7, height / 2)), w.maxY() - 1 - y - 3);
                if (satelliteHeight >= 10) {
                    placed += pillar(w, random, x, y, z, satelliteRadius, satelliteHeight, lush && random.nextFloat() < 0.72f, false).placed();
                    built++;
                }
            }
        }
        if (placed == 0) {
            return Result.rejected("nichts ersetzbar", centerX, centerY, centerZ);
        }
        return new Result(null, centerX, centerY, centerZ, main.topX(), main.topY(), main.topZ(), radius, height, built, placed);
    }

    private static Pillar pillar(LandformWorld w, Random random, int ox, int oy, int oz, int baseRadius, int height,
                                 boolean withGrowth, boolean mainPillar) {
        int placed = 0;
        int leanX = random.nextInt(5) - 2;
        int leanZ = random.nextInt(5) - 2;
        int baseY = oy - 2;
        long salt = random.nextLong();
        placed += rootFlare(w, ox, oy, oz, baseRadius, salt);
        for (int dy = 0; dy <= height; dy++) {
            double progress = (double) dy / height;
            int centerX = ox + (int) Math.round(leanX * progress * progress);
            int centerZ = oz + (int) Math.round(leanZ * progress * progress);
            double radius = baseRadius - (baseRadius - 2.25) * progress + terraceBoost(progress, mainPillar);
            int limit = (int) Math.ceil(radius + 1.0);
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double edgeRadius = radius + (hash01(centerX + dx, baseY + dy, centerZ + dz, salt) - 0.5) * 0.82;
                    if (dx * dx + dz * dz <= edgeRadius * edgeRadius) {
                        int x = centerX + dx;
                        int y = baseY + dy;
                        int z = centerZ + dz;
                        if (canReplace(w.block(x, y, z)) && w.writable(x, y, z)) {
                            w.set(x, y, z, SIFTSLATE);
                            placed++;
                        }
                    }
                }
            }
        }
        int topX = ox + leanX;
        int topZ = oz + leanZ;
        int topY = baseY + height;
        int topRadius = Math.max(2, baseRadius - 2) + (mainPillar ? 1 : 0);
        String cap = withGrowth ? GROWTH : SIFTSLATE;
        for (int dx = -topRadius; dx <= topRadius; dx++) {
            for (int dz = -topRadius; dz <= topRadius; dz++) {
                double edgeRadius = topRadius + (hash01(topX + dx, topY, topZ + dz, salt ^ 1779033703L) - 0.5) * 0.7;
                if (dx * dx + dz * dz <= edgeRadius * edgeRadius) {
                    int x = topX + dx;
                    int z = topZ + dz;
                    if (canReplace(w.block(x, topY, z)) && w.writable(x, topY, z)) {
                        w.set(x, topY, z, cap);
                        placed++;
                    }
                    if (withGrowth) {
                        w.decorate(x, topY, z, 1.08f);
                    }
                }
            }
        }
        return new Pillar(placed, topX, topY, topZ);
    }

    private static int rootFlare(LandformWorld w, int ox, int oy, int oz, int baseRadius, long salt) {
        int placed = 0;
        int rootRadius = baseRadius + 2;
        for (int dx = -rootRadius; dx <= rootRadius; dx++) {
            for (int dz = -rootRadius; dz <= rootRadius; dz++) {
                double edgeRadius = rootRadius + (hash01(ox + dx, oy, oz + dz, salt ^ -4126379630918251389L) - 0.5) * 0.9;
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > edgeRadius * edgeRadius) {
                    continue;
                }
                int x = ox + dx;
                int z = oz + dz;
                int localSurface = surface(w, x, z);
                if (localSurface < 0 || Math.abs(localSurface - oy) > 8) {
                    continue;
                }
                double centerStrength = 1.0 - Math.min(1.0, Math.sqrt(distanceSquared) / rootRadius);
                int targetTop = oy + (int) Math.round(centerStrength * 2.0);
                if (localSurface <= targetTop + 1) {
                    for (int y = localSurface - 2; y <= targetTop; y++) {
                        if (w.writable(x, y, z) && canReplace(w.block(x, y, z))) {
                            w.set(x, y, z, SIFTSLATE);
                            placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** At least 84 % of the columns around dry Sift ground, with at most {@code maximumRelief} between them. */
    static boolean stableFootprint(LandformWorld w, int ox, int oy, int oz, int radius, int maximumRelief) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int samples = 0;
        int valid = 0;
        int sampleRadius = radius + 2;
        for (int dx = -sampleRadius; dx <= sampleRadius; dx++) {
            for (int dz = -sampleRadius; dz <= sampleRadius; dz++) {
                if (dx * dx + dz * dz <= sampleRadius * sampleRadius) {
                    samples++;
                    int y = surface(w, ox + dx, oz + dz);
                    if (y >= 0 && drySupported(w, ox + dx, y, oz + dz, 6)) {
                        valid++;
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
        }
        return valid >= Math.ceil(samples * 0.84) && maxY - minY <= maximumRelief;
    }

    static double terraceBoost(double progress, boolean mainPillar) {
        double boost = 0.0;
        if (Math.abs(progress - 0.28) < 0.035) {
            boost += mainPillar ? 1.35 : 0.75;
        }
        if (Math.abs(progress - 0.58) < 0.03) {
            boost += mainPillar ? 1.05 : 0.55;
        }
        if (Math.abs(progress - 0.81) < 0.025) {
            boost += mainPillar ? 0.75 : 0.35;
        }
        return boost;
    }

    public static boolean terrain(String block) {
        return TERRAIN.contains(block);
    }

    /** No fluid at the surface and {@code depth} blocks of Sift ground below it. */
    static boolean drySupported(LandformWorld w, int x, int surfaceY, int z, int depth) {
        if (w.fluid(x, surfaceY, z)) {
            return false;
        }
        for (int offset = 1; offset <= depth; offset++) {
            if (!terrain(w.block(x, surfaceY - offset, z))) {
                return false;
            }
        }
        return true;
    }

    /** Air, Sift ground and the small plants. */
    public static boolean canReplace(String block) {
        return LandformWorld.AIR.equals(block) || TERRAIN.contains(block) || PLANTS.contains(block);
    }

    /**
     * SiftMonolithFeature#findTerrainSurface: the first free y above Sift ground within 8 blocks below the
     * WG surface, or -1 (as in the original, also for ground below y 0).
     */
    public static int surface(LandformWorld w, int x, int z) {
        int y = w.height(x, z);
        if (y == Integer.MIN_VALUE) {
            return -1;
        }
        for (int step = 0; step < 8 && y > w.minY(); y--) {
            if (terrain(w.block(x, y - 1, z))) {
                return y;
            }
            step++;
        }
        return -1;
    }

    static double hash01(int x, int y, int z, long salt) {
        long value = salt ^ x * -7046029254386353131L;
        value ^= y * -4417276706812531889L;
        value ^= z * 1609587929392839161L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (value >>> 11) * (double) 1.110223E-16F;
    }
}
