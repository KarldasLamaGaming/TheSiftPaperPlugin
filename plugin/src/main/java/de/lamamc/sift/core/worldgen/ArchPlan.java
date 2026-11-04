package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Cliff arches (alpha.27), a port of SiftArchFeature of the original 1.1.1: a natural bridge of healthy
 * sculk or siftslate over a dip, 26–30 blocks between its feet. The best of four axes through the chunk
 * centre is chosen by valley depth and drop; the arch is a parabola of rough spheres with waves,
 * blended into the ground at both ends, with a few gouges, growth on its crown and plants on top.
 */
public final class ArchPlan {

    private static final int[][] AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    public static final String HEALTHY = "healthy_sculk";
    public static final String DRY_HEALTHY = "dry_healthy_sculk";

    /** What was built; {@code reason} is null for an arch, crown* is the top of its middle. */
    public record Result(String reason, int x, int y, int z, int crownX, int crownY, int crownZ, boolean healthy, int halfSpan,
                         int rise, int blocks) {

        public boolean built() {
            return reason == null;
        }

        static Result rejected(String reason, int x, int y, int z) {
            return new Result(reason, x, y, z, x, y, z, false, 0, 0, 0);
        }
    }

    record Candidate(double axisX, double axisZ, int halfSpan, int leftY, int rightY, int valleyDepth) {
    }

    record CurvePoint(double x, double y, double z) {
    }

    private ArchPlan() {
    }

    /** Tries an arch in this chunk; {@code portalClearance} 23 in the original. */
    public static Result place(LandformWorld w, Random random, int chunkX, int chunkZ, int portalClearance) {
        int centerX = (chunkX << 4) + 8 + random.nextInt(3) - 1;
        int centerZ = (chunkZ << 4) + 8 + random.nextInt(3) - 1;
        if (w.nearPortal(centerX, centerZ, portalClearance)) {
            return Result.rejected("zu nah am Haupt-Portal", centerX, 0, centerZ);
        }
        int centerY = MonolithPlan.surface(w, centerX, centerZ);
        if (centerY < 0) {
            return Result.rejected("kein Sift-Boden", centerX, 0, centerZ);
        }
        int halfSpan = 13 + random.nextInt(3);
        Candidate best = bestSpan(w, centerX, centerZ, centerY, halfSpan, random);
        if (best == null) {
            return Result.rejected("keine Senke", centerX, centerY, centerZ);
        }
        int maxY = w.maxY() - 1;
        int baselineMiddle = (best.leftY() + best.rightY()) / 2;
        int archRise = 7 + random.nextInt(6) + Math.min(5, best.valleyDepth() / 5);
        int topAtMiddle = baselineMiddle + archRise + 5;
        if (topAtMiddle >= maxY) {
            archRise -= topAtMiddle - maxY + 1;
        }
        if (archRise < 6) {
            return Result.rejected("zu nah an der Bauhöhe", centerX, centerY, centerZ);
        }
        String leftFloor = w.block(centerX - (int) Math.round(best.axisX() * best.halfSpan()), best.leftY() - 1,
                centerZ - (int) Math.round(best.axisZ() * best.halfSpan()));
        String rightFloor = w.block(centerX + (int) Math.round(best.axisX() * best.halfSpan()), best.rightY() - 1,
                centerZ + (int) Math.round(best.axisZ() * best.halfSpan()));
        boolean healthyGround = healthy(leftFloor) || healthy(rightFloor);
        boolean growthGround = MonolithPlan.GROWTH.equals(leftFloor) || MonolithPlan.GROWTH.equals(rightFloor);
        float healthyChance = healthyGround ? 0.78f : growthGround ? 0.22f : 0.43f;
        boolean healthy = random.nextFloat() < healthyChance;
        String body = healthy ? HEALTHY : MonolithPlan.SIFTSLATE;
        boolean growthVariant = !healthy && growthGround;
        double baseThickness = 2.45 + random.nextDouble() * 0.9;
        int steps = best.halfSpan() * 4;
        long shapeSalt = random.nextLong();
        int placed = 0;
        Set<Long> archBlocks = new LinkedHashSet<>();
        List<int[]> crownPath = new ArrayList<>();
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            CurvePoint point = curvePoint(best, centerX, centerZ, t, archRise, shapeSalt);
            double endStrength = Math.pow(Math.abs(t * 2.0 - 1.0), 4.0);
            double thicknessWave = 0.93 + 0.09 * Math.sin(t * Math.PI * 5.0 + phase(shapeSalt, 11))
                    + 0.045 * Math.sin(t * Math.PI * 9.0 + phase(shapeSalt, 29));
            double localThickness = baseThickness * thicknessWave + endStrength * 1.15;
            placed += sphere(w, point, localThickness, body, healthy, step, shapeSalt, archBlocks);
            if (step % 5 == 0) {
                crownPath.add(new int[] {(int) Math.round(point.x()), (int) Math.round(point.y() + localThickness),
                        (int) Math.round(point.z())});
            }
        }
        placed += blendAnchor(w, best, centerX, centerZ, true, baseThickness, body, healthy, shapeSalt ^ 1279608404L, archBlocks);
        placed += blendAnchor(w, best, centerX, centerZ, false, baseThickness, body, healthy, shapeSalt ^ 353416726612L, archBlocks);
        carveGouges(w, random, best, centerX, centerZ, archRise, baseThickness, shapeSalt, archBlocks);
        if (growthVariant) {
            coatCrown(w, archBlocks);
        }
        for (int[] crown : crownPath) {
            w.decorate(crown[0], crown[1], crown[2], healthy ? 0.82f : 0.72f);
        }
        if (placed == 0) {
            return Result.rejected("nichts ersetzbar", centerX, centerY, centerZ);
        }
        int[] middle = crownPath.get(crownPath.size() / 2);
        return new Result(null, centerX, centerY, centerZ, middle[0], middle[1], middle[2], healthy, best.halfSpan(), archRise, placed);
    }

    private static int blendAnchor(LandformWorld w, Candidate c, int centerX, int centerZ, boolean left, double baseThickness,
                                   String body, boolean healthy, long salt, Set<Long> archBlocks) {
        CurvePoint endpoint = curvePoint(c, centerX, centerZ, left ? 0.0 : 1.0, 0, salt);
        double outward = left ? -1.0 : 1.0;
        double perpendicularX = -c.axisZ();
        double perpendicularZ = c.axisX();
        int previousSurface = left ? c.leftY() : c.rightY();
        int placed = 0;
        int samples = 5;
        for (int sample = 0; sample < samples; sample++) {
            double progress = (sample + 1.0) / samples;
            double distance = 0.85 + sample * 0.78;
            double sideWave = Math.sin(progress * Math.PI * 2.4 + phase(salt, 13)) * 0.42 * (1.0 - progress);
            double x = endpoint.x() + c.axisX() * outward * distance + perpendicularX * sideWave;
            double z = endpoint.z() + c.axisZ() * outward * distance + perpendicularZ * sideWave;
            int sampleX = (int) Math.round(x);
            int sampleZ = (int) Math.round(z);
            if (!w.contains(sampleX, sampleZ)) {
                break;
            }
            int surface = MonolithPlan.surface(w, sampleX, sampleZ);
            if (surface < 0 || Math.abs(surface - previousSurface) > 9) {
                break;
            }
            previousSurface = surface;
            double eased = smootherstep(progress);
            double centerY = endpoint.y() + (surface - 0.35 - endpoint.y()) * eased;
            double radius = baseThickness + 0.9 - progress * (baseThickness - 0.55) + Math.sin(progress * Math.PI * 3.0 + phase(salt, 31)) * 0.16;
            placed += sphere(w, new CurvePoint(x, centerY, z), Math.max(1.25, radius), body, healthy, left ? -sample : sample, salt,
                    archBlocks);
        }
        return placed;
    }

    /** Exposed top blocks of a siftslate arch on growth ground become siftslate growth. */
    private static void coatCrown(LandformWorld w, Set<Long> archBlocks) {
        for (long packed : archBlocks) {
            int x = (int) (packed >> 38);
            int y = (int) (packed << 52 >> 52);
            int z = (int) (packed << 26 >> 38);
            if (w.contains(x, z) && w.writable(x, y, z) && !archBlocks.contains(pack(x, y + 1, z))
                    && openCrownSpace(w.block(x, y + 1, z)) && MonolithPlan.SIFTSLATE.equals(w.block(x, y, z))) {
                w.set(x, y, z, MonolithPlan.GROWTH);
            }
        }
    }

    private static boolean openCrownSpace(String block) {
        return LandformWorld.AIR.equals(block) || MonolithPlan.PLANTS.contains(block);
    }

    static Candidate bestSpan(LandformWorld w, int centerX, int centerZ, int centerY, int halfSpan, Random random) {
        Candidate best = null;
        double bestScore = -Double.MAX_VALUE;
        for (int[] direction : AXES) {
            double normalization = direction[0] != 0 && direction[1] != 0 ? 0.70710678118 : 1.0;
            double axisX = direction[0] * normalization;
            double axisZ = direction[1] * normalization;
            int leftX = centerX - (int) Math.round(axisX * halfSpan);
            int leftZ = centerZ - (int) Math.round(axisZ * halfSpan);
            int rightX = centerX + (int) Math.round(axisX * halfSpan);
            int rightZ = centerZ + (int) Math.round(axisZ * halfSpan);
            if (!w.contains(leftX, leftZ) || !w.contains(rightX, rightZ)) {
                continue;
            }
            int leftY = MonolithPlan.surface(w, leftX, leftZ);
            int rightY = MonolithPlan.surface(w, rightX, rightZ);
            if (leftY < 0 || rightY < 0) {
                continue;
            }
            int innerDistance = Math.max(7, halfSpan / 2);
            int leftInnerX = centerX - (int) Math.round(axisX * (halfSpan - innerDistance));
            int leftInnerZ = centerZ - (int) Math.round(axisZ * (halfSpan - innerDistance));
            int rightInnerX = centerX + (int) Math.round(axisX * (halfSpan - innerDistance));
            int rightInnerZ = centerZ + (int) Math.round(axisZ * (halfSpan - innerDistance));
            if (!w.contains(leftInnerX, leftInnerZ) || !w.contains(rightInnerX, rightInnerZ)) {
                continue;
            }
            int leftInnerY = MonolithPlan.surface(w, leftInnerX, leftInnerZ);
            int rightInnerY = MonolithPlan.surface(w, rightInnerX, rightInnerZ);
            if (leftInnerY < 0 || rightInnerY < 0) {
                continue;
            }
            int heightDifference = Math.abs(leftY - rightY);
            int valleyDepth = Math.min(leftY, rightY) - centerY;
            int leftDrop = leftY - leftInnerY;
            int rightDrop = rightY - rightInnerY;
            if (heightDifference <= 20 && valleyDepth >= 6 && leftDrop >= 3 && rightDrop >= 3 && leftDrop + rightDrop >= 8) {
                double score = valleyDepth * 5.5 + (leftDrop + rightDrop) * 2.2 - heightDifference * 0.75 + random.nextDouble();
                if (score > bestScore) {
                    bestScore = score;
                    best = new Candidate(axisX, axisZ, halfSpan, leftY, rightY, valleyDepth);
                }
            }
        }
        return best;
    }

    static CurvePoint curvePoint(Candidate c, int centerX, int centerZ, double t, int archRise, long salt) {
        double signedDistance = (t * 2.0 - 1.0) * c.halfSpan();
        double archMask = Math.sin(Math.PI * t);
        double lateralWave = (Math.sin(t * Math.PI * 2.0 + phase(salt, 7)) * 0.68 + Math.sin(t * Math.PI * 5.0 + phase(salt, 23)) * 0.32)
                * 1.45 * archMask;
        double verticalWave = (Math.sin(t * Math.PI * 3.0 + phase(salt, 17)) * 0.72 + Math.sin(t * Math.PI * 7.0 + phase(salt, 37)) * 0.28)
                * 1.05 * archMask;
        double perpendicularX = -c.axisZ();
        double perpendicularZ = c.axisX();
        double baseY = c.leftY() + (c.rightY() - c.leftY()) * t;
        return new CurvePoint(centerX + c.axisX() * signedDistance + perpendicularX * lateralWave,
                baseY + archRise * 4.0 * t * (1.0 - t) + verticalWave,
                centerZ + c.axisZ() * signedDistance + perpendicularZ * lateralWave);
    }

    private static int sphere(LandformWorld w, CurvePoint center, double radius, String body, boolean healthy, int stripeSeed,
                              long salt, Set<Long> placedBlocks) {
        int placed = 0;
        int limit = (int) Math.ceil(radius + 0.5);
        int roundedX = (int) Math.round(center.x());
        int roundedY = (int) Math.round(center.y());
        int roundedZ = (int) Math.round(center.z());
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dy = -limit; dy <= limit; dy++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double edgeRadius = radius + (MonolithPlan.hash01(roundedX + dx, roundedY + dy, roundedZ + dz, salt) - 0.5) * 0.62;
                    if (dx * dx + dy * dy + dz * dz > edgeRadius * edgeRadius) {
                        continue;
                    }
                    int x = roundedX + dx;
                    int y = roundedY + dy;
                    int z = roundedZ + dz;
                    if (w.contains(x, z) && w.writable(x, y, z) && MonolithPlan.canReplace(w.block(x, y, z))) {
                        boolean dryVein = healthy && dy < 0 && stripeSeed / 8 % 7 == 3
                                && MonolithPlan.hash01(x, y, z, salt ^ -7046029254386353131L) < 0.58;
                        w.set(x, y, z, dryVein ? DRY_HEALTHY : body);
                        placedBlocks.add(pack(x, y, z));
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private static void carveGouges(LandformWorld w, Random random, Candidate c, int centerX, int centerZ, int archRise,
                                    double thickness, long salt, Set<Long> placedBlocks) {
        int gouges = 3 + random.nextInt(5);
        double perpendicularX = -c.axisZ();
        double perpendicularZ = c.axisX();
        for (int i = 0; i < gouges; i++) {
            double t = 0.16 + random.nextDouble() * 0.68;
            CurvePoint curve = curvePoint(c, centerX, centerZ, t, archRise, salt);
            double angle = -1.0995574287564276 + random.nextDouble() * Math.PI * 1.7;
            double surfaceOffset = thickness * (0.68 + random.nextDouble() * 0.22);
            double gougeX = curve.x() + perpendicularX * Math.cos(angle) * surfaceOffset;
            double gougeY = curve.y() + Math.sin(angle) * surfaceOffset;
            double gougeZ = curve.z() + perpendicularZ * Math.cos(angle) * surfaceOffset;
            double radius = 0.82 + random.nextDouble() * 0.78;
            int limit = (int) Math.ceil(radius);
            int roundedX = (int) Math.round(gougeX);
            int roundedY = (int) Math.round(gougeY);
            int roundedZ = (int) Math.round(gougeZ);
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dy = -limit; dy <= limit; dy++) {
                    for (int dz = -limit; dz <= limit; dz++) {
                        if (dx * dx + dy * dy + dz * dz > radius * radius) {
                            continue;
                        }
                        int x = roundedX + dx;
                        int y = roundedY + dy;
                        int z = roundedZ + dz;
                        if (placedBlocks.remove(pack(x, y, z)) && w.writable(x, y, z)) {
                            w.set(x, y, z, LandformWorld.AIR);
                        }
                    }
                }
            }
        }
    }

    private static boolean healthy(String block) {
        return HEALTHY.equals(block) || DRY_HEALTHY.equals(block);
    }

    static double phase(long salt, int shift) {
        return (salt >>> shift & 65535L) / 65535.0 * Math.PI * 2.0;
    }

    static double smootherstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }
}
