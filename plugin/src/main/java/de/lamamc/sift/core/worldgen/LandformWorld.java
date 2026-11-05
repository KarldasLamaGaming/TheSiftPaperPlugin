package de.lamamc.sift.core.worldgen;

/**
 * The blocks a monolith or arch (alpha.27) reads and writes, without Bukkit: Sift ids, "air" for any
 * air, anything else as "other". The plans read back what they wrote, like the original features.
 */
public interface LandformWorld {

    String AIR = "air";
    String OTHER = "other";

    /** First free y above the WORLD_SURFACE_WG heightmap (vanilla getHeight), or Integer.MIN_VALUE outside the region. */
    int height(int x, int z);

    /** Sift id, {@link #AIR} or {@link #OTHER}. */
    String block(int x, int y, int z);

    /** A fluid in this block (ichor, waterlogged). */
    boolean fluid(int x, int y, int z);

    /** The chunk and its neighbours minus one block (SiftWorldgenBounds#aroundWithNeighbourMargin). */
    boolean contains(int x, int z);

    /** contains and inside the build height (ensureCanWrite). */
    boolean writable(int x, int y, int z);

    /** Sets a Sift id or {@link #AIR}. */
    void set(int x, int y, int z, String block);

    /** SiftSurfaceDecorator#decorate on this floor block, with the shared random. */
    void decorate(int x, int floorY, int z, float density);

    /** SiftFeaturePlacementGuard#intersectsPortal(center, radius). */
    boolean nearPortal(int x, int z, int radius);

    /** SiftFeaturePlacementGuard#tryReserveSolid. */
    boolean reserve(int minX, int minZ, int maxX, int maxZ);

    int minY();

    /** Exclusive: vanilla getMaxY() is maxY() - 1. */
    int maxY();
}
