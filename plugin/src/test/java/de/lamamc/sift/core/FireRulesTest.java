package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class FireRulesTest {

    private static double burnRate(int burnOdds, int chance) {
        Random random = new Random(1);
        int burned = 0;
        int ticks = 200_000;
        for (int i = 0; i < ticks; i++) {
            if (FireRules.burnOut(random, chance, burnOdds, 5, false) != FireRules.BurnOut.NOTHING) {
                burned++;
            }
        }
        return (double) burned / ticks;
    }

    @Test
    void burnChanceIsOddsOverChance() {
        assertEquals(5.0 / 300, burnRate(5, 300), 0.002);   // willow log beside the fire
        assertEquals(20.0 / 300, burnRate(20, 300), 0.003); // willow planks
        assertEquals(60.0 / 250, burnRate(60, 250), 0.005); // willow foliage above the fire
        assertEquals(0.0, burnRate(0, 300));
    }

    @Test
    void youngFireReplacesBurntBlocksWithFireMoreOften() {
        Random random = new Random(2);
        int fire = 0;
        int air = 0;
        for (int i = 0; i < 100_000; i++) {
            switch (FireRules.burnOut(random, 1, 100, 0, false)) {
                case FIRE -> fire++;
                case AIR -> air++;
                default -> {
                }
            }
        }
        assertEquals(0.5, (double) fire / (fire + air), 0.01); // age 0: nextInt(10) < 5
        assertEquals(FireRules.BurnOut.AIR, FireRules.burnOut(new Random(3), 1, 100, 0, true)); // rain: no new fire
    }

    @Test
    void spreadNeedsIgniteOddsAndIsRarerHigherUp() {
        assertFalse(FireRules.spreads(new Random(4), 0, 0, 0, 2, false));
        Random random = new Random(5);
        int near = 0;
        int high = 0;
        for (int i = 0; i < 100_000; i++) {
            near += FireRules.spreads(random, 30, 0, 0, 2, false) ? 1 : 0;
            high += FireRules.spreads(random, 30, 0, 4, 2, false) ? 1 : 0;
        }
        // foliage (30): adjusted (30 + 40 + 14) / 30 = 2 → 3 of 100 next to the fire, 3 of 400 three blocks up
        assertEquals(0.03, near / 100_000.0, 0.003);
        assertEquals(0.0075, high / 100_000.0, 0.0015);
        assertTrue(FireRules.tickDelay(new Random(6)) >= 30);
    }
}
