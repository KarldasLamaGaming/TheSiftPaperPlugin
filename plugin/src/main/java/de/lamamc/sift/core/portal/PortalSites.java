package de.lamamc.sift.core.portal;

import de.lamamc.sift.core.worldgen.LandmarkPlacement;

/**
 * Where the portal shrines of a dimension stand: one per cell of {@code cellChunks × cellChunks} chunks,
 * in the middle third of the cell (like the abandoned portals), not closer than {@code minDistance} blocks
 * to 0/0 (the End keeps the main island free). Every site is built, so the nearest site can be computed
 * without loading anything (compass, /sift portal locate).
 */
public record PortalSites(int cellChunks, int minDistance, long salt) {

    /** Salts per dimension, so the three grids do not line up. */
    public static final long OVERWORLD_SALT = 0x51F7_0C0A_57A1_0001L;
    public static final long NETHER_SALT = 0x51F7_0C0A_57A1_0002L;
    public static final long END_SALT = 0x51F7_0C0A_57A1_0003L;

    public PortalSites {
        if (cellChunks < 3) {
            throw new IllegalArgumentException("cell-chunks must be at least 3, got " + cellChunks);
        }
    }

    /** A site: its chunk and the block at the chunk centre. */
    public record Site(int chunkX, int chunkZ) {
        public int blockX() {
            return (chunkX << 4) + 8;
        }

        public int blockZ() {
            return (chunkZ << 4) + 8;
        }

        double distanceTo(double x, double z) {
            return Math.hypot(blockX() + 0.5 - x, blockZ() + 0.5 - z);
        }
    }

    /** The site of a cell, or null when the cell's candidate lies inside the kept-free zone. */
    public Site site(long seed, int cellX, int cellZ) {
        LandmarkPlacement.ChunkPos chunk = LandmarkPlacement.candidateChunk(seed, cellX, cellZ, cellChunks, salt, 0, true);
        Site site = new Site(chunk.x(), chunk.z());
        return site.distanceTo(0, 0) < minDistance ? null : site;
    }

    /** Whether a shrine belongs into this chunk. */
    public boolean isSite(long seed, int chunkX, int chunkZ) {
        Site site = site(seed, Math.floorDiv(chunkX, cellChunks), Math.floorDiv(chunkZ, cellChunks));
        return site != null && site.chunkX() == chunkX && site.chunkZ() == chunkZ;
    }

    /** The nearest site to a block position, searching outwards cell ring by cell ring (null if none within maxRings). */
    public Site nearest(long seed, double x, double z, int maxRings) {
        int cellBlocks = cellChunks * 16;
        int cx = Math.floorDiv((int) Math.floor(x), cellBlocks);
        int cz = Math.floorDiv((int) Math.floor(z), cellBlocks);
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxRings; ring++) {
            // every cell of this ring and beyond is at least (ring - 1) cells away
            if (best != null && (ring - 1) * (double) cellBlocks > bestDistance) {
                break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    Site site = site(seed, cx + dx, cz + dz);
                    if (site != null && site.distanceTo(x, z) < bestDistance) {
                        best = site;
                        bestDistance = site.distanceTo(x, z);
                    }
                }
            }
        }
        return best;
    }
}
