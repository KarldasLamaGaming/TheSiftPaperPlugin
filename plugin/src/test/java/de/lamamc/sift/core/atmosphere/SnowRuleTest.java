package de.lamamc.sift.core.atmosphere;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnowRuleTest {

    @Test
    void snowOnlyInTheSnowyPeaksInTheDarkAndAtMostTwoLayers() {
        assertTrue(SnowRule.snow("ichor_snowy_peaks", 0, 1, 2));
        assertTrue(SnowRule.snow("ichor_snowy_peaks", 9, 2, 2));
        assertFalse(SnowRule.snow("ichor_snowy_peaks", 10, 1, 2), "block light 10 or more");
        assertFalse(SnowRule.snow("ichor_snowy_peaks", 0, 3, 2), "a third layer");
        assertFalse(SnowRule.snow("siftslate_peaks", 0, 1, 2), "other biomes, even when high and cold");
    }

    @Test
    void ichorDoesNotFreeze() {
        assertFalse(SnowRule.freeze(false));
        assertTrue(SnowRule.freeze(true));
    }
}
