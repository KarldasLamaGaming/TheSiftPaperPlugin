package de.lamamc.sift.core.worldgen;

/**
 * One landmark per cell of {@code cellChunks × cellChunks} chunks, at a chunk derived from the world seed
 * (SiftLandmarkPlacement of the original). Deterministic, so every chunk can decide on its own.
 */
public final class LandmarkPlacement {

    /** Abandoned portals: one per 32×32 chunks. */
    public static final long ABANDONED_PORTAL_SALT = 4702392765337521733L;

    private LandmarkPlacement() {
    }

    public record ChunkPos(int x, int z) {
    }

    public static boolean isSelectedChunk(long worldSeed, int chunkX, int chunkZ, int cellChunks, long salt) {
        ChunkPos selected = candidateChunk(worldSeed, Math.floorDiv(chunkX, cellChunks), Math.floorDiv(chunkZ, cellChunks),
                cellChunks, salt, 0);
        return selected.x() == chunkX && selected.z() == chunkZ;
    }

    public static ChunkPos candidateChunk(long worldSeed, int cellX, int cellZ, int cellChunks, long salt, int candidateIndex) {
        long indexedSalt = salt ^ (long) candidateIndex * -2960836687051489901L;
        long first = mix(worldSeed, cellX, cellZ, indexedSalt);
        long second = mix(worldSeed, cellZ, cellX, indexedSalt ^ -7046029254386353131L);
        int offsetX = (int) Long.remainderUnsigned(first, cellChunks);
        int offsetZ = (int) Long.remainderUnsigned(second, cellChunks);
        return new ChunkPos(cellX * cellChunks + offsetX, cellZ * cellChunks + offsetZ);
    }

    private static long mix(long seed, int cellX, int cellZ, long salt) {
        long value = seed ^ salt;
        value ^= (long) cellX * -7046029254386353131L;
        value ^= (long) cellZ * -4417276706812531889L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        return value ^ value >>> 31;
    }
}
