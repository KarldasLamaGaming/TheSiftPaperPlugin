package de.lamamc.sift.core.atmosphere;

/**
 * Which snow and ice the weather may form in the Sift (alpha.32), after ServerLevelWeatherMixin of the
 * original 1.1.1: ichor snow only in {@code ichor_snowy_peaks}, only where the block light is below 10, at
 * most 2 layers. Ichor is its own fluid in the original and never freezes; here it is water, so the ice
 * vanilla would form on it is refused.
 */
public final class SnowRule {

    public static final String BIOME = "ichor_snowy_peaks";
    public static final int MAX_BLOCK_LIGHT = 9;

    private SnowRule() {
    }

    /** May a snow layer form (or grow to {@code layers}) here? */
    public static boolean snow(String biome, int blockLight, int layers, int maxLayers) {
        return BIOME.equals(biome) && blockLight <= MAX_BLOCK_LIGHT && layers <= maxLayers;
    }

    /** May water (ichor) freeze to ice in the Sift? */
    public static boolean freeze(boolean ichorFreezes) {
        return ichorFreezes;
    }
}
