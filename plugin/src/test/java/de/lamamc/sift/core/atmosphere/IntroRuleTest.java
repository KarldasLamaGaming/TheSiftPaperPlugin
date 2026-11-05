package de.lamamc.sift.core.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntroRuleTest {

    @Test
    void playsOnceAfterTheDelayOnGround() {
        int counted = 0;
        for (int i = 0; i < 3; i++) {
            counted = IntroRule.count(counted, true, true, 5);
        }
        assertEquals(15, counted);
        assertFalse(IntroRule.play(false, counted, 20), "not yet");
        counted = IntroRule.count(counted, true, true, 5);
        assertTrue(IntroRule.play(false, counted, 20));
        assertFalse(IntroRule.play(true, counted, 20), "only once per player");
    }

    @Test
    void fallingOrLeavingStartsOver() {
        assertEquals(0, IntroRule.count(15, true, false, 5), "no ground below: still loading or falling");
        assertEquals(0, IntroRule.count(15, false, true, 5), "left the Sift");
        assertTrue(IntroRule.play(false, 0, 0), "delay 0 plays at once");
    }
}
