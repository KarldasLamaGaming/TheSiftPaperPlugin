package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyActivationTest {

    @Test
    void onlyTheMatchingKeyOpensASleepingOrWokenPortal() {
        assertEquals(KeyActivation.Result.OPEN, KeyActivation.decide(true, true, PortalState.LOCKED, "echo_resonator", "echo_resonator"));
        // woken before alpha.37 (the tune used to open them) or ignition cut short
        assertEquals(KeyActivation.Result.OPEN, KeyActivation.decide(true, true, PortalState.UNLOCKED, "echo_resonator", "echo_resonator"));
        assertEquals(KeyActivation.Result.WRONG_KEY, KeyActivation.decide(true, true, PortalState.LOCKED, "void_resonator", "echo_resonator"));
        // the Ancient City takes its own key, not the overworld one
        assertEquals(KeyActivation.Result.WRONG_KEY, KeyActivation.decide(true, true, PortalState.LOCKED, "echo_resonator", "ancient_resonator"));
        assertEquals(KeyActivation.Result.WRONG_KEY, KeyActivation.decide(true, true, PortalState.LOCKED, "echo_resonator", null));
    }

    @Test
    void openPortalsUnknownConsolesAndSwitchedOffKeys() {
        assertEquals(KeyActivation.Result.ALREADY_OPEN, KeyActivation.decide(true, true, PortalState.OPEN, "echo_resonator", "echo_resonator"));
        assertEquals(KeyActivation.Result.NO_PORTAL, KeyActivation.decide(true, false, null, "echo_resonator", null));
        assertEquals(KeyActivation.Result.DISABLED, KeyActivation.decide(false, true, PortalState.LOCKED, "echo_resonator", "echo_resonator"));
    }

    @Test
    void theKeyIsUsedUpOnlyWhenItWakesASleepingPortal() {
        assertTrue(KeyActivation.consumes(true, false, PortalState.LOCKED));
        assertFalse(KeyActivation.consumes(true, true, PortalState.LOCKED), "creative");
        assertFalse(KeyActivation.consumes(false, false, PortalState.LOCKED), "portals.keys.consume: false");
        assertFalse(KeyActivation.consumes(true, false, PortalState.UNLOCKED), "already awake");
    }
}
