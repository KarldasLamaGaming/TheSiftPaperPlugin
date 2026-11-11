package de.lamamc.sift.core.ichor;

/**
 * Which water is ichor (alpha.38, "Ichor B, small variant"): by default all water in the Sift is ichor and all water
 * elsewhere is plain water. Only the exceptions are marked (per block position in the chunk's data): plain water
 * poured into the Sift and ichor poured outside it. A mark flips the default; flowing water inherits the mark of the
 * block it flows from.
 */
public final class FluidIdentity {

    private FluidIdentity() {
    }

    /** Whether water at a position is ichor. */
    public static boolean isIchor(boolean inSift, boolean marked) {
        return inSift != marked;
    }

    /** The mark a position needs to hold ichor (or plain water) in this world. */
    public static boolean markFor(boolean inSift, boolean ichor) {
        return inSift != ichor;
    }

    /**
     * Emptying a bucket (player or dispenser): the new mark of the target, or null to leave it as it is (the target
     * already holds a water source, e.g. a bucket emptied into a lake does not change the lake).
     */
    public static Boolean onEmpty(boolean inSift, boolean ichorBucket, boolean targetHoldsWater) {
        return targetHoldsWater ? null : markFor(inSift, ichorBucket);
    }

    /** Ichor and water do not mix in a cauldron: adding one to a non-empty cauldron of the other is refused. */
    public static boolean mixes(int level, boolean cauldronIchor, boolean addIchor) {
        return level > 0 && cauldronIchor != addIchor;
    }

    /** A block position inside its chunk as one int (y from -2048 to 2047). */
    public static int pack(int x, int y, int z) {
        return (y + 2048) << 8 | (z & 15) << 4 | x & 15;
    }

    public static int localX(int packed) {
        return packed & 15;
    }

    public static int localZ(int packed) {
        return packed >> 4 & 15;
    }

    public static int y(int packed) {
        return (packed >>> 8) - 2048;
    }
}
