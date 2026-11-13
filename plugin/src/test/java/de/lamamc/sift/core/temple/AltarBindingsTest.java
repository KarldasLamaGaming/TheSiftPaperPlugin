package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.temple.AltarBindings.Verdict;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AltarBindingsTest {

    private static final UUID KARL = new UUID(0, 1);
    private static final UUID MATE = new UUID(0, 2);

    @Test
    void theFirstWakeBindsAndTheSameTempleMayBeWokenAgainAfterACallOff() {
        AltarBindings b = new AltarBindings();
        assertEquals(Verdict.ALLOW, b.check(KARL, "temple-3", true, false));
        assertTrue(b.woke(KARL, "temple-3"));
        // died or called off: nothing changes, the same temple again
        assertEquals(Verdict.ALLOW, b.check(KARL, "temple-3", true, false));
        assertFalse(b.woke(KARL, "temple-3"), "no new binding");
        assertEquals(new AltarBindings.Binding("temple-3", false), b.get(KARL));
    }

    @Test
    void anotherTempleIsClosedAndAfterTheVictoryEveryAltarIs() {
        AltarBindings b = new AltarBindings();
        b.woke(KARL, "temple-3");
        assertEquals(Verdict.OTHER_TEMPLE, b.check(KARL, "temple-7", true, false));
        assertEquals(1, b.defeated("temple-3"));
        assertEquals(Verdict.DEFEATED, b.check(KARL, "temple-7", true, false));
        assertEquals(Verdict.DEFEATED, b.check(KARL, "temple-3", true, false));
        assertEquals(0, b.defeated("temple-3"), "only once");
    }

    @Test
    void fellowFightersStayFree() {
        AltarBindings b = new AltarBindings();
        b.woke(KARL, "temple-3");
        b.defeated("temple-3"); // MATE fought along, never woke the altar
        assertNull(b.get(MATE));
        assertEquals(Verdict.ALLOW, b.check(MATE, "temple-7", true, false));
    }

    @Test
    void bypassAndTheSwitchBindNobody() {
        AltarBindings b = new AltarBindings();
        b.woke(KARL, "temple-3");
        b.defeated("temple-3");
        assertEquals(Verdict.FREE, b.check(KARL, "temple-7", true, true), "bypass permission");
        assertEquals(Verdict.FREE, b.check(KARL, "temple-7", false, false), "rule switched off");
        assertEquals(Verdict.FREE, b.check(MATE, "temple-7", false, false));
    }

    @Test
    void adminResetAndPlayerResetLiftTheBinding() {
        AltarBindings b = new AltarBindings();
        b.woke(KARL, "temple-3");
        b.woke(MATE, "temple-5");
        b.defeated("temple-3");
        assertTrue(b.reset(KARL));
        assertFalse(b.reset(KARL));
        assertEquals(Verdict.ALLOW, b.check(KARL, "temple-7", true, false));
        assertEquals(1, b.clear());
        assertEquals(0, b.size());
    }

    @Test
    void survivesARestart() {
        AltarBindings b = new AltarBindings();
        b.woke(KARL, "temple-3");
        b.woke(MATE, "temple-5");
        b.defeated("temple-5");
        AltarBindings read = AltarBindings.parse(b.serialize() + "broken line\nnot-a-uuid temple-1 bound\n");
        assertEquals(new AltarBindings.Binding("temple-3", false), read.get(KARL));
        assertEquals(new AltarBindings.Binding("temple-5", true), read.get(MATE));
        assertEquals(2, read.size());
        assertEquals(0, AltarBindings.parse(null).size());
    }
}
