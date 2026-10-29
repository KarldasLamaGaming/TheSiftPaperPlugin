package de.lamamc.sift.core.worldgen;

/**
 * One landmark per cell of {@code cellChunks × cellChunks} chunks, at a chunk derived from the world seed
 * (SiftLandmarkPlacement of the original, version 1.1.1). Deterministic, so every chunk can decide on its
 * own. Abandoned portals and soul canyons keep a third of the cell free on every side, so neighbouring
 * landmarks are at least two thirds of a cell apart; soul canyon cells are shifted by half a cell.
 */
public final class LandmarkPlacement {

    /** Abandoned portals: one per 20×20 chunks in the original. */
    public static final long ABANDONED_PORTAL_SALT = 4702392765337521733L;
    public static final long SOUL_CANYON_SALT = 6003110614342389337L;

    private LandmarkPlacement() {
    }

    public record ChunkPos(int x, int z) {
    }

    public static boolean isSelectedChunk(long worldSeed, int chunkX, int chunkZ, int cellChunks, long salt) {
        int gridOffset = gridOffset(cellChunks, salt);
        ChunkPos selected = candidateChunk(worldSeed, Math.floorDiv(chunkX - gridOffset, cellChunks),
                Math.floorDiv(chunkZ - gridOffset, cellChunks), cellChunks, salt, 0);
        return selected.x() == chunkX && selected.z() == chunkZ;
    }

    public static ChunkPos candidateChunk(long worldSeed, int cellX, int cellZ, int cellChunks, long salt, int candidateIndex) {
        long indexedSalt = salt ^ (long) candidateIndex * -2960836687051489901L;
        long first = mix(worldSeed, cellX, cellZ, indexedSalt);
        long second = mix(worldSeed, cellZ, cellX, indexedSalt ^ -7046029254386353131L);
        int inset = salt == ABANDONED_PORTAL_SALT || salt == SOUL_CANYON_SALT ? cellChunks / 3 : 0;
        int spread = cellChunks - inset * 2;
        int offsetX = inset + (int) Long.remainderUnsigned(first, spread);
        int offsetZ = inset + (int) Long.remainderUnsigned(second, spread);
        int gridOffset = gridOffset(cellChunks, salt);
        return new ChunkPos(cellX * cellChunks + gridOffset + offsetX, cellZ * cellChunks + gridOffset + offsetZ);
    }

    /** Cell of a chunk (for listing candidates around a position). */
    public static int cellOf(int chunk, int cellChunks, long salt) {
        return Math.floorDiv(chunk - gridOffset(cellChunks, salt), cellChunks);
    }

    private static int gridOffset(int cellChunks, long salt) {
        return salt == SOUL_CANYON_SALT ? cellChunks / 2 : 0;
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
