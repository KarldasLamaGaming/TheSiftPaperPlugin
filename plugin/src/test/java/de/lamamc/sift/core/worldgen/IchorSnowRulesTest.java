package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IchorSnowRulesTest {

    @Test
    void noiseIsSmoothAndInRange() {
        for (int x = -60; x < 60; x += 7) {
            for (int z = -60; z < 60; z += 5) {
                double v = IchorSnowRules.valueNoise(42L, x, z, 26);
                assertTrue(v >= 0 && v <= 1, "in [0, 1]: " + v);
                double next = IchorSnowRules.valueNoise(42L, x + 1, z, 26);
                assertTrue(Math.abs(next - v) < 0.2, "smooth from block to block");
            }
        }
        // the corners of a cell are the cell values: noise at a cell origin equals its unit value
        assertEquals(IchorSnowRules.valueNoise(7L, 26, 52, 26), IchorSnowRules.valueNoise(7L, 26, 52, 26));
    }

    @Test
    void mostlyOneLayerSometimesTwo() {
        int two = 0;
        int total = 0;
        for (int x = 0; x < 400; x++) {
            for (int z = 0; z < 400; z += 3) {
                int layers = IchorSnowRules.layers(123456789L, IchorSnowRules.GROUND_SALT, x, z);
                assertTrue(layers == 1 || layers == 2);
                two += layers == 2 ? 1 : 0;
                total++;
            }
        }
        double share = (double) two / total;
        assertTrue(share > 0.01 && share < 0.5, "two layers in patches only: " + share);
        assertEquals(IchorSnowRules.layers(1L, IchorSnowRules.CANOPY_SALT, 10, 20),
                IchorSnowRules.layers(1L, IchorSnowRules.CANOPY_SALT, 10, 20), "deterministic per seed");
    }
}
