package de.lamamc.sift.core;

import java.util.Locale;
import java.util.Map;

/**
 * Siftite gear values (config.yml {@code items.siftite}) and how they turn the netherite base item into
 * Siftite. Armor points and the attack speed stay those of netherite; only the configured values change.
 */
public record SiftiteStats(int toolDurability, float miningSpeed, double attackDamageBonus,
                           int armorDurabilityMultiplier, double armorToughness, double knockbackResistance) {

    /** Vanilla netherite: tool material speed and attack damage bonus. */
    public static final float NETHERITE_MINING_SPEED = 9.0f;
    public static final double NETHERITE_ATTACK_DAMAGE_BONUS = 4.0;

    /** Vanilla durability per unit of the armor material's multiplier (ArmorType). */
    private static final Map<String, Integer> ARMOR_DURABILITY_FACTOR = Map.of(
            "helmet", 11, "chestplate", 16, "leggings", 15, "boots", 13);

    public SiftiteStats {
        if (toolDurability <= 0 || miningSpeed <= 0 || armorDurabilityMultiplier <= 0) {
            throw new IllegalArgumentException("Siftite durability and mining speed have to be positive");
        }
    }

    /** The mining speed for a tool rule of the netherite base: netherite's speed becomes Siftite's. */
    public float miningSpeedFor(float baseSpeed) {
        return Math.abs(baseSpeed - NETHERITE_MINING_SPEED) < 1e-4 ? miningSpeed : baseSpeed;
    }

    /** The attack damage modifier of the netherite base item, raised by the larger damage bonus. */
    public double attackDamageModifier(double netheriteModifier) {
        return netheriteModifier + attackDamageBonus - NETHERITE_ATTACK_DAMAGE_BONUS;
    }

    /** Durability of an armor piece, by the end of its id (helmet, chestplate, leggings, boots). */
    public int armorDurability(String itemId) {
        String piece = itemId.substring(itemId.lastIndexOf('_') + 1).toLowerCase(Locale.ROOT);
        Integer factor = ARMOR_DURABILITY_FACTOR.get(piece);
        if (factor == null) {
            throw new IllegalArgumentException("Not an armor piece: " + itemId);
        }
        return factor * armorDurabilityMultiplier;
    }

    /** Approved defaults (2200, 9.5, +4.5, 39, 3.0, 0.12). */
    public static SiftiteStats defaults() {
        return new SiftiteStats(2200, 9.5f, 4.5, 39, 3.0, 0.12);
    }
}
