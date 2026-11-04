package de.lamamc.sift.core.worldgen;

/**
 * Ichor snow on the ichor snowy peaks (alpha.26), from SiftIchorSnowFeature of the original 1.1.1: one or two
 * layers per column, decided by smooth value noise over the world seed (scale 26, detail scale 9).
 */
public final class IchorSnowRules {

    /** Salts of the original: ground snow and snow on willow crowns. */
    public static final long GROUND_SALT = 5279142693729160783L;
    public static final long CANOPY_SALT = 2164546982323162371L;
    private static final long DETAIL_SALT = 6002822589557593153L;

    private IchorSnowRules() {
    }

    /** Two layers where noise(26) + 0.35 × noise(9) is above 0.88, else one. */
    public static int layers(long seed, long salt, int x, int z) {
        double field = valueNoise(seed ^ salt, x, z, 26);
        double detail = valueNoise(seed ^ DETAIL_SALT ^ salt, x, z, 9);
        return field + detail * 0.35 > 0.88 ? 2 : 1;
    }

    public static double valueNoise(long seed, int x, int z, int scale) {
        int cellX = Math.floorDiv(x, scale);
        int cellZ = Math.floorDiv(z, scale);
        double localX = (double) Math.floorMod(x, scale) / scale;
        double localZ = (double) Math.floorMod(z, scale) / scale;
        double smoothX = localX * localX * (3.0 - 2.0 * localX);
        double smoothZ = localZ * localZ * (3.0 - 2.0 * localZ);
        double a = unit(seed, cellX, cellZ);
        double b = unit(seed, cellX + 1, cellZ);
        double c = unit(seed, cellX, cellZ + 1);
        double d = unit(seed, cellX + 1, cellZ + 1);
        return lerp(smoothZ, lerp(smoothX, a, b), lerp(smoothX, c, d));
    }

    private static double unit(long seed, int x, int z) {
        long value = seed ^ (long) x * -7046029254386353131L ^ (long) z * -4417276706812531889L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (value >>> 11) * (double) 1.110223E-16F;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
