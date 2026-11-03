package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SifterRulesTest {

    private final SifterRules rules = SifterRules.defaults();

    @Test
    void biteTimelineLikeTheOriginal() {
        int sounds = 0;
        int damage = 0;
        int age = 0;
        SifterRules.BiteStep step;
        do {
            step = rules.step(++age);
            if (step == SifterRules.BiteStep.SOUND) {
                sounds++;
                assertEquals(8, age);
            }
            if (step == SifterRules.BiteStep.DAMAGE) {
                damage++;
                assertEquals(13, age);
            }
        } while (step != SifterRules.BiteStep.END);
        assertEquals(14, age);
        assertEquals(1, sounds);
        assertEquals(1, damage);
        assertThrows(IllegalArgumentException.class, () -> new SifterRules(10, 8, 12, 2.5, 20, 0.83, new int[] {1, 2}, 1));
    }

    @Test
    void reachIsTwoAndAHalfBlocks() {
        assertTrue(rules.inReach(6.25));
        assertFalse(rules.inReach(6.3));
    }

    @Test
    void meleeRangeLikeVanilla() {
        // Sifter 0.9 wide at x 0, player 0.6 wide: boxes touch with the 0.83 widening up to ~1.58 apart
        double[] sifter = {-0.45, 64, -0.45, 0.45, 64.9, 0.45};
        assertTrue(rules.inMeleeRange(sifter, box(1.5, 64)));
        assertFalse(rules.inMeleeRange(sifter, box(1.7, 64)));
        assertFalse(rules.inMeleeRange(sifter, box(1.0, 67)), "not above its head");
    }

    private static double[] box(double x, double y) {
        return new double[] {x - 0.3, y, -0.3, x + 0.3, y + 1.8, 0.3};
    }

    @Test
    void meatLikeTheLootTable() {
        Random random = new Random(3);
        int min = Integer.MAX_VALUE;
        int max = 0;
        int lootingMax = 0;
        for (int i = 0; i < 2000; i++) {
            int plain = rules.meatCount(0, random);
            min = Math.min(min, plain);
            max = Math.max(max, plain);
            lootingMax = Math.max(lootingMax, rules.meatCount(3, random));
        }
        assertEquals(1, min);
        assertEquals(2, max);
        assertEquals(5, lootingMax);
    }

    @Test
    void huntsOnlyPlayersButStrikesBackAtAnyAttacker() {
        // like the original: NearestAttackableTargetGoal(Player) + HurtByTargetGoal
        org.junit.jupiter.api.Assertions.assertTrue(SifterRules.hunts(true, false));
        org.junit.jupiter.api.Assertions.assertFalse(SifterRules.hunts(true, true), "creative/spectator");
        org.junit.jupiter.api.Assertions.assertFalse(SifterRules.hunts(false, false), "a Blub (or any mob) is never hunted");
        org.junit.jupiter.api.Assertions.assertTrue(SifterRules.retaliates(false, false, false), "a Blub that bit it");
        org.junit.jupiter.api.Assertions.assertTrue(SifterRules.retaliates(false, true, false), "a survival player");
        org.junit.jupiter.api.Assertions.assertFalse(SifterRules.retaliates(false, true, true), "a creative player");
        org.junit.jupiter.api.Assertions.assertFalse(SifterRules.retaliates(true, false, false), "itself");
    }
}
