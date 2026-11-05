package de.lamamc.sift.core.worldgen;

import java.util.Random;

/**
 * Dry sculk spikes (alpha.30), a port of SiftDrySpikeFeature of the original 1.1.1: a leaning, sharply
 * tapering spike of dry healthy sculk (radius 3–4, 19–31 high) with a root flare, and 2–4 smaller spikes
 * around it (radius 2–3, 10–22 high).
 */
public final class DrySpikePlan {

    public static final String DRY = "dry_healthy_sculk";

    /** {@code reason} is null when built; x/y/z is the foot of the main spike, topY its tip. */
    public record Result(String reason, int x, int y, int z, int topY, int spikes, int blocks) {

        public boolean built() {
            return reason == null;
        }
    }

    private DrySpikePlan() {
    }

    /** Portal clearance and reservation ±18 in the original. */
    public static Result place(LandformWorld w, Random random, int chunkX, int chunkZ, int portalClearance, int reserveRadius) {
        int centerX = (chunkX << 4) + 8 + random.nextInt(5) - 2;
        int centerZ = (chunkZ << 4) + 8 + random.nextInt(5) - 2;
        int centerY = MonolithPlan.surface(w, centerX, centerZ);
        if (centerY < 0) {
            return new Result("kein Sift-Boden", centerX, 0, centerZ, 0, 0, 0);
        }
        if (w.nearPortal(centerX, centerZ, portalClearance)) {
            return new Result("zu nah am Haupt-Portal", centerX, centerY, centerZ, 0, 0, 0);
        }
        if (!MonolithPlan.drySupported(w, centerX, centerY, centerZ, 6)) {
            return new Result("Boden nicht trocken oder zu dünn", centerX, centerY, centerZ, 0, 0, 0);
        }
        int mainRadius = 3 + random.nextInt(2);
        if (!stableFootprint(w, centerX, centerZ, mainRadius + 2)) {
            return new Result("Gelände uneben", centerX, centerY, centerZ, 0, 0, 0);
        }
        if (!w.reserve(centerX - reserveRadius, centerZ - reserveRadius, centerX + reserveRadius, centerZ + reserveRadius)) {
            return new Result("Platz reserviert", centerX, centerY, centerZ, 0, 0, 0);
        }
        int[] main = spike(w, random, centerX, centerY, centerZ, mainRadius, 19 + random.nextInt(13));
        int placed = main[0];
        int spikes = main[0] > 0 ? 1 : 0;
        int satellites = 2 + random.nextInt(3);
        for (int i = 0; i < satellites; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = 4 + random.nextInt(6);
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int z = centerZ + (int) Math.round(Math.sin(angle) * distance);
            int y = MonolithPlan.surface(w, x, z);
            if (y >= 0) {
                int[] s = spike(w, random, x, y, z, 2 + random.nextInt(2), 10 + random.nextInt(13));
                placed += s[0];
                spikes += s[0] > 0 ? 1 : 0;
            }
        }
        if (placed == 0) {
            return new Result("nichts ersetzbar", centerX, centerY, centerZ, 0, 0, 0);
        }
        return new Result(null, centerX, centerY, centerZ, main[1], spikes, placed);
    }

    /** One spike; returns {placed blocks, y of the tip}. Nothing when its footprint is unstable or it would be lower than 8. */
    private static int[] spike(LandformWorld w, Random random, int ox, int oy, int oz, int baseRadius, int requestedHeight) {
        if (!stableFootprint(w, ox, oz, baseRadius + 2)) {
            return new int[] {0, oy};
        }
        int height = Math.min(requestedHeight, w.maxY() - 1 - oy - 2);
        if (height < 8) {
            return new int[] {0, oy};
        }
        int leanX = random.nextInt(7) - 3;
        int leanZ = random.nextInt(7) - 3;
        long salt = random.nextLong();
        int placed = rootFlare(w, ox, oy, oz, baseRadius, salt);
        for (int dy = 0; dy <= height; dy++) {
            double progress = Math.max(0.0, (double) dy / height);
            int centerX = ox + (int) Math.round(leanX * progress * progress);
            int centerZ = oz + (int) Math.round(leanZ * progress * progress);
            double radius = Math.max(0.32, baseRadius * Math.pow(1.0 - progress, 0.72));
            int limit = Math.max(1, (int) Math.ceil(radius));
            for (int dx = -limit; dx <= limit; dx++) {
                for (int dz = -limit; dz <= limit; dz++) {
                    double edgeRadius = radius + (MonolithPlan.hash01(centerX + dx, oy + dy, centerZ + dz, salt) - 0.5) * 0.45;
                    if (dx * dx + dz * dz > edgeRadius * edgeRadius) {
                        continue;
                    }
                    int x = centerX + dx;
                    int y = oy + dy;
                    int z = centerZ + dz;
                    if (w.writable(x, y, z) && MonolithPlan.canReplace(w.block(x, y, z))) {
                        w.set(x, y, z, DRY);
                        placed++;
                    }
                }
            }
        }
        return new int[] {placed, oy + height};
    }

    private static int rootFlare(LandformWorld w, int ox, int oy, int oz, int baseRadius, long salt) {
        int rootRadius = baseRadius + 2;
        int placed = 0;
        for (int dx = -rootRadius; dx <= rootRadius; dx++) {
            for (int dz = -rootRadius; dz <= rootRadius; dz++) {
                double edgeRadius = rootRadius + (MonolithPlan.hash01(ox + dx, oy, oz + dz, salt ^ -3372029247567499371L) - 0.5) * 0.8;
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > edgeRadius * edgeRadius) {
                    continue;
                }
                int x = ox + dx;
                int z = oz + dz;
                int localSurface = MonolithPlan.surface(w, x, z);
                if (localSurface < 0 || Math.abs(localSurface - oy) > 6) {
                    continue;
                }
                double centerStrength = 1.0 - Math.min(1.0, Math.sqrt(distanceSquared) / rootRadius);
                int targetTop = oy + (int) Math.round(centerStrength * 1.5);
                if (localSurface <= targetTop + 1) {
                    for (int y = localSurface - 2; y <= targetTop; y++) {
                        if (w.writable(x, y, z) && MonolithPlan.canReplace(w.block(x, y, z))) {
                            w.set(x, y, z, DRY);
                            placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** At least 82 % dry Sift ground in the circle, relief at most 12. */
    static boolean stableFootprint(LandformWorld w, int ox, int oz, int radius) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int samples = 0;
        int valid = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    samples++;
                    int y = MonolithPlan.surface(w, ox + dx, oz + dz);
                    if (y >= 0 && MonolithPlan.drySupported(w, ox + dx, y, oz + dz, 6)) {
                        valid++;
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
        }
        return valid >= Math.ceil(samples * 0.82) && maxY - minY <= 12;
    }
}
