package de.lamamc.sift.world.gen;

/**
 * The area of the main portal that other features keep out of (SiftFeaturePlacementGuard): before the
 * portal is built the whole search area around 0/0, afterwards its footprint plus 15 blocks.
 */
public final class PortalReservation {

    private volatile int[] rectangle; // minX, minZ, maxX, maxZ or null
    private volatile boolean built;

    public void reserveSearchArea(int radius) {
        rectangle = new int[] {-radius, -radius, radius, radius};
        built = false;
    }

    public void reserveFootprint(int originX, int originZ, int sizeX, int sizeZ, int margin) {
        rectangle = new int[] {originX - margin, originZ - margin, originX + sizeX - 1 + margin, originZ + sizeZ - 1 + margin};
        built = true;
    }

    public void clear() {
        rectangle = null;
    }

    /** Whether a square of {@code radius} around x/z touches the reserved area. */
    public boolean intersects(int x, int z, int radius) {
        int[] r = rectangle;
        return r != null && x + radius >= r[0] && x - radius <= r[2] && z + radius >= r[1] && z - radius <= r[3];
    }

    /** Trees only keep out of the finished portal; before that the site clearing removes them. */
    public boolean intersectsBuilt(int x, int z, int radius) {
        return built && intersects(x, z, radius);
    }
}
