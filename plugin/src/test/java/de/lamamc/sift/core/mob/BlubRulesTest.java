package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class BlubRulesTest {

    private final BlubRules rules = BlubRules.defaults();

    @Test
    void hoppingAnimationSpeedIsClamped() {
        assertEquals(0.72, BlubRules.hoppingAnimationSpeed(0), 1e-9);
        assertEquals(0.72 + 0.7, BlubRules.hoppingAnimationSpeed(0.1), 1e-9);
        assertEquals(2.35, BlubRules.hoppingAnimationSpeed(1), 1e-9);
    }

    @Test
    void movingWhileAirborneOrSliding() {
        assertTrue(BlubRules.movingForAnimation(false, 0));
        assertTrue(BlubRules.movingForAnimation(true, 0.002));
        assertFalse(BlubRules.movingForAnimation(true, 0.0005));
    }

    @Test
    void jumpsLikeTheOriginal() {
        assertEquals(0.3, rules.jumpPower(1.0, false));
        assertEquals(0.36, rules.jumpPower(1.2, false));
        assertEquals(0.5, rules.jumpPower(1.0, true));
        assertEquals(8, rules.landingDelay(1.0));
        assertEquals(6, rules.landingDelay(1.1));
        assertEquals(4, rules.landingDelay(1.2));
        assertEquals(0.2 * 1.2, rules.hopSpeed(0.3, 1.2), 1e-9);
    }

    @Test
    void tamingIsOneInThree() {
        Random random = new Random(5);
        int tamed = 0;
        for (int i = 0; i < 30000; i++) {
            if (rules.rollTame(random)) {
                tamed++;
            }
        }
        assertEquals(1 / 3.0, tamed / 30000.0, 0.015);
    }

    @Test
    void timersStayInTheirRanges() {
        Random random = new Random(9);
        for (int i = 0; i < 1000; i++) {
            int flap = rules.nextFlap(random);
            int idle = rules.nextIdleSound(random);
            assertTrue(flap >= 140 && flap <= 540);
            assertTrue(idle >= 180 && idle <= 540);
        }
    }
}
