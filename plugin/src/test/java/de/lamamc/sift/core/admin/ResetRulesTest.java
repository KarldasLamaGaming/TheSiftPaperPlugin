package de.lamamc.sift.core.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResetRulesTest {

    @Test
    void eachPlayerOncePerServerReset() {
        assertFalse(ResetRules.due(0, 0), "no reset ordered yet");
        assertTrue(ResetRules.due(0, 1), "never reset");
        assertFalse(ResetRules.due(1, 1), "already done");
        assertTrue(ResetRules.due(1, 2), "a later reset");
    }

    @Test
    void confirmationOnlyWithinTheWindow() {
        assertTrue(ResetRules.confirmable(1000, 1000 + 29_000, 30_000));
        assertFalse(ResetRules.confirmable(1000, 1000 + 31_000, 30_000), "too late");
        assertFalse(ResetRules.confirmable(0, 5000, 30_000), "nothing requested");
        assertFalse(ResetRules.confirmable(5000, 4000, 30_000), "clock went back");
    }
}
