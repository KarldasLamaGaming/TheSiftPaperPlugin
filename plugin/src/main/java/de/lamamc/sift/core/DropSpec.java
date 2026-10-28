package de.lamamc.sift.core;

import java.util.List;

/**
 * Drop rule of a block, modeled on the loot tables of the original mod.
 *
 * <ul>
 *   <li>{@code SELF}: the block itself, or {@code item} if set (a crop drops its seeds)</li>
 *   <li>{@code SILK_OR}: the block with Silk Touch, otherwise {@code min..max} of {@code item}
 *       (siftslate -> cobbled siftslate, ichor snow block -> 4 ichor snowballs)</li>
 *   <li>{@code ORE}: the block with Silk Touch, otherwise {@code min..max} of {@code item}, Fortune as
 *       vanilla "ore_drops"</li>
 *   <li>{@code CHANCE}: the block with Silk Touch, otherwise {@code item} with the chance for the Fortune
 *       level ({@code chances}, last entry for higher levels), else the block itself (sift rubble)</li>
 *   <li>{@code SHEARS_OR_SILK}: the block only with shears or Silk Touch</li>
 *   <li>{@code SILK_ONLY}: the block only with Silk Touch (glass)</li>
 *   <li>{@code NONE}: nothing</li>
 * </ul>
 */
public record DropSpec(Kind kind, String item, int min, int max, List<Double> chances) {

    public enum Kind { SELF, SILK_OR, ORE, CHANCE, SHEARS_OR_SILK, SILK_ONLY, NONE }

    public DropSpec {
        chances = List.copyOf(chances);
    }

    public static DropSpec of(Kind kind) {
        return new DropSpec(kind, null, 1, 1, List.of());
    }
}
