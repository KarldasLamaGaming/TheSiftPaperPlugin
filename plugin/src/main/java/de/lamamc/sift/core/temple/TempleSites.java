package de.lamamc.sift.core.temple;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the temples stand (phase 4, alpha.41, phase4.temples): exactly {@code count} sites on a ring around the
 * Sift's main portal, at equal angles plus a seeded offset and jitter, at a seeded radius between the ring's minimum
 * and maximum. Computed from the seed alone, so they are known before any chunk is generated: other landmarks avoid
 * them, and the compass and /sift temple list can show them. A world border smaller than the ring pulls the ring in.
 */
public final class TempleSites {

    public static final long SALT = 7391248675931447331L;

    /** Distance kept from the world border. */
    public static final int BORDER_MARGIN = 200;

    public record Site(int index, int x, int z) {

        public String id() {
            return "temple-" + (index + 1);
        }

        public double distanceSquared(double px, double pz) {
            return (x - px) * (x - px) + (z - pz) * (z - pz);
        }
    }

    public record Ring(int count, int minRadius, int maxRadius, double angleJitterDegrees) {
    }

    private TempleSites() {
    }

    /**
     * @param borderHalfSize half the side of the world border (blocks from its centre to its edge)
     */
    public static List<Site> sites(long seed, Ring ring, int centerX, int centerZ, double borderHalfSize) {
        List<Site> sites = new ArrayList<>();
        if (ring.count() <= 0) {
            return sites;
        }
        int[] radii = effectiveRadii(ring, borderHalfSize);
        SplittableRandom random = new SplittableRandom(seed ^ SALT);
        double step = 360.0 / ring.count();
        double offset = random.nextDouble() * 360.0;
        double jitter = Math.min(Math.max(0, ring.angleJitterDegrees()), step / 2 - 1);
        for (int i = 0; i < ring.count(); i++) {
            double angle = Math.toRadians(offset + i * step + (random.nextDouble() * 2 - 1) * jitter);
            double radius = radii[0] + random.nextDouble() * (radii[1] - radii[0]);
            int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            int z = centerZ + (int) Math.round(Math.sin(angle) * radius);
            // centre of its chunk: the whole temple (radius 20) is then built by that chunk's populator
            sites.add(new Site(i, Math.floorDiv(x, 16) * 16 + 8, Math.floorDiv(z, 16) * 16 + 8));
        }
        return sites;
    }

    /** {min, max} radius after pulling the ring inside the world border. */
    public static int[] effectiveRadii(Ring ring, double borderHalfSize) {
        int limit = (int) Math.max(0, Math.min(Integer.MAX_VALUE, borderHalfSize - BORDER_MARGIN));
        int max = Math.min(ring.maxRadius(), limit);
        int min = Math.min(Math.min(ring.minRadius(), ring.maxRadius()), max);
        return new int[] {min, Math.max(min, max)};
    }

    /** Whether a rectangle touches the area of a temple (its footprint radius around the site). */
    public static boolean intersects(List<Site> sites, int footprintRadius, int minX, int minZ, int maxX, int maxZ) {
        for (Site site : sites) {
            if (maxX >= site.x() - footprintRadius && minX <= site.x() + footprintRadius
                    && maxZ >= site.z() - footprintRadius && minZ <= site.z() + footprintRadius) {
                return true;
            }
        }
        return false;
    }

    /** The nearest site not yet defeated, or null. */
    public static Site nearest(List<Site> sites, double x, double z, java.util.function.Predicate<Site> open) {
        Site best = null;
        for (Site site : sites) {
            if (open.test(site) && (best == null || site.distanceSquared(x, z) < best.distanceSquared(x, z))) {
                best = site;
            }
        }
        return best;
    }
}
