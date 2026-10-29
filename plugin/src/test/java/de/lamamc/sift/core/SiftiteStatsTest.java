package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SiftiteStatsTest {

    private final SiftiteStats stats = SiftiteStats.defaults();

    @Test
    void armorDurabilityFollowsTheMultiplier() {
        assertEquals(429, stats.armorDurability("siftite_helmet"));
        assertEquals(624, stats.armorDurability("siftite_chestplate"));
        assertEquals(585, stats.armorDurability("siftite_leggings"));
        assertEquals(507, stats.armorDurability("siftite_boots"));
        assertThrows(IllegalArgumentException.class, () -> stats.armorDurability("siftite_sword"));
    }

    @Test
    void swordDealsHalfAPointMoreThanNetherite() {
        // netherite sword: modifier 7 (+1 player base = 8 damage); Siftite: 8.5
        assertEquals(7.5, stats.attackDamageModifier(7.0), 1e-9);
    }

    @Test
    void onlyTheNetheriteSpeedIsRaised() {
        assertEquals(9.5f, stats.miningSpeedFor(9.0f), 1e-6);
        assertEquals(1.5f, stats.miningSpeedFor(1.5f), 1e-6); // e.g. sword on cobwebs-like rules
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SiftiteStats(0, 9.5f, 4.5, 39, 3, 0.12));
    }
}
