package de.lamamc.sift.core.siftite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SiftiteRecoveryTest {

    @Test
    void tagIds() {
        assertEquals(java.util.Set.of("siftite_ingot", "siftite_sword"),
                SiftiteRecovery.idsFromTag(List.of("the_sift:siftite_sword", "the_sift:siftite_ingot")));
    }

    @Test
    void keepInventoryOfTheDeathDecides() {
        assertTrue(SiftiteRecovery.secure(false, 2), "keep_inventory false in the death world: saved");
        assertFalse(SiftiteRecovery.secure(true, 2), "true: vanilla keeps the inventory, nothing to save");
        assertFalse(SiftiteRecovery.secure(false, 0), "no Siftite");
        assertTrue(SiftiteRecovery.revert(true, false), "another plugin switched keepInventory on: undo, no duplicate");
        assertTrue(SiftiteRecovery.revert(false, true), "death cancelled: undo");
        assertFalse(SiftiteRecovery.revert(false, false));
    }

    @Test
    void pendingListNeverLosesOrDoubles() {
        List<String> pending = SiftiteRecovery.append(List.of("sword"), List.of("helmet", "ingot x5"));
        assertEquals(List.of("sword", "helmet", "ingot x5"), pending, "older items first, then this death's");
        pending = SiftiteRecovery.afterDelivery(pending, null);
        assertEquals(List.of("helmet", "ingot x5"), pending, "delivered: gone");
        pending = SiftiteRecovery.afterDelivery(SiftiteRecovery.afterDelivery(pending, null), "ingot x2");
        assertEquals(List.of("ingot x2"), pending, "partly delivered: the rest stays first");
        assertEquals(List.of(), SiftiteRecovery.afterDelivery(List.<String>of(), null));
    }

    @Test
    void flightApproachesAndIsCapped() {
        double[] pos = {0, 0, 0};
        double[] vel = {0, 0, 0};
        double[] target = {100, 0, 0};
        double[] s = SiftiteRecovery.step(pos, vel, target, SiftiteRecovery.KEEP, SiftiteRecovery.PULL, SiftiteRecovery.MAX_SPEED);
        assertEquals(SiftiteRecovery.MAX_SPEED, s[3], 1e-9, "capped");
        double[] p = {0, 0, 0};
        double[] v = {0, 0, 0};
        double[] near = {1, 0.5, 0};
        for (int i = 0; i < 40 && !SiftiteRecovery.reached(p, near); i++) {
            double[] next = SiftiteRecovery.step(p, v, near, SiftiteRecovery.KEEP, SiftiteRecovery.PULL, SiftiteRecovery.MAX_SPEED);
            p = new double[] {next[0], next[1], next[2]};
            v = new double[] {next[3], next[4], next[5]};
        }
        assertTrue(SiftiteRecovery.reached(p, near), "arrives within 40 ticks");
        assertFalse(SiftiteRecovery.reached(new double[] {0, 0, 0}, near));
    }

    @Test
    void orbitStaysAroundThePlayer() {
        for (int t = 0; t < 50; t++) {
            double[] o = SiftiteRecovery.orbit(10, 64, 10, t, 1, 3);
            assertEquals(1.65, Math.hypot(o[0] - 10, o[2] - 10), 1e-9);
            assertTrue(o[1] >= 64 && o[1] <= 64.7 + 1e-9);
        }
    }

    /** alpha.34 hotfix: vanilla items and empty slots have no Sift id (null); that crashed every death with them. */
    @Test
    void vanillaItemsAndEmptySlotsAreNoSiftite() {
        var ids = SiftiteRecovery.idsFromTag(List.of("the_sift:siftite_ingot", "the_sift:siftite_sword"));
        assertFalse(SiftiteRecovery.isSiftite(ids, null), "vanilla item or empty slot");
        assertFalse(ids.contains(null), "the set itself takes null too (a TreeSet threw here)");
        assertFalse(SiftiteRecovery.isSiftite(ids, "charoite"), "other Sift item");
        assertTrue(SiftiteRecovery.isSiftite(ids, "siftite_sword"));
        assertFalse(SiftiteRecovery.isSiftite(java.util.Set.of(), null), "empty tag, no Siftite at all");
    }

    /** alpha.34 hotfix: a second death event before respawning (death screen open) must not save twice. */
    @Test
    void onlyTheFirstDeathEventSinceRespawnCounts() {
        assertTrue(SiftiteRecovery.firstDeath(false));
        assertFalse(SiftiteRecovery.firstDeath(true));
        // the pending list after one death is the same whether a second event comes or not
        List<String> afterFirst = SiftiteRecovery.append(List.of(), List.of("sword"));
        List<String> afterSecond = SiftiteRecovery.firstDeath(true) ? SiftiteRecovery.append(afterFirst, List.of("sword")) : afterFirst;
        assertEquals(List.of("sword"), afterSecond);
    }
}
