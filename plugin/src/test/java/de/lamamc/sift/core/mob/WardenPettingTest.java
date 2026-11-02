package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class WardenPettingTest {

    private static WardenPetting.Action idleUntilSearch(WardenPetting petting, Random random) {
        for (int i = 0; i < 5000; i++) {
            WardenPetting.Action action = petting.idle(true, random);
            if (action != WardenPetting.Action.NONE) {
                return action;
            }
        }
        return WardenPetting.Action.NONE;
    }

    @Test
    void searchesOnlyWhenCalmAfterTheDelay() {
        Random random = new Random(1);
        WardenPetting petting = new WardenPetting(WardenPetting.Rules.defaults());
        for (int i = 0; i < 799; i++) {
            assertEquals(WardenPetting.Action.NONE, petting.idle(true, random), "tick " + i);
        }
        WardenPetting angry = new WardenPetting(WardenPetting.Rules.defaults());
        for (int i = 0; i < 5000; i++) {
            assertEquals(WardenPetting.Action.NONE, angry.idle(false, random));
        }
        assertEquals(WardenPetting.Action.SEARCH, idleUntilSearch(petting, random));
    }

    @Test
    void approachesThenPetsWithHeartsAndRests() {
        Random random = new Random(2);
        WardenPetting petting = new WardenPetting(WardenPetting.Rules.defaults());
        idleUntilSearch(petting, random);
        petting.searched(true, random);
        assertTrue(petting.active());
        assertEquals(WardenPetting.Action.APPROACH, petting.tick(true, 100, random));
        int hearts = 0;
        WardenPetting.Action action;
        int ticks = 0;
        do {
            action = petting.tick(true, 4, random);
            if (action == WardenPetting.Action.HEART) {
                hearts++;
            }
            ticks++;
        } while (action == WardenPetting.Action.PET || action == WardenPetting.Action.HEART);
        assertEquals(WardenPetting.Action.DONE, action);
        assertEquals(100, ticks);
        assertEquals(5, hearts);
        assertFalse(petting.active());
        assertTrue(petting.searchCooldown() >= 2400);
    }

    @Test
    void configuredTimingsApply() {
        Random random = new Random(4);
        WardenPetting petting = new WardenPetting(new WardenPetting.Rules(10, 2, 20, 5, new int[] {3, 3}, new int[] {50, 50},
                new int[] {7, 7}, new int[] {9, 9}));
        for (int i = 0; i < 3; i++) {
            assertEquals(WardenPetting.Action.NONE, petting.idle(true, random));
        }
        assertEquals(WardenPetting.Action.SEARCH, petting.idle(true, random));
        petting.searched(false, random);
        assertEquals(9, petting.searchCooldown());
        petting.searched(true, random);
        assertEquals(WardenPetting.Action.APPROACH, petting.tick(true, 9, random), "3 blocks is outside reach 2");
        assertEquals(WardenPetting.Action.FAILED, petting.tick(true, 121, random), "11 blocks is outside the search range");
        assertEquals(7, petting.searchCooldown());
    }

    @Test
    void givesUpWhenTooSlowOrDisturbed() {
        Random random = new Random(3);
        WardenPetting petting = new WardenPetting(WardenPetting.Rules.defaults());
        petting.searched(true, random);
        WardenPetting.Action action = WardenPetting.Action.NONE;
        for (int i = 0; i < 241; i++) {
            action = petting.tick(true, 100, random);
        }
        assertEquals(WardenPetting.Action.FAILED, action);
        petting.searched(true, random);
        assertEquals(WardenPetting.Action.FAILED, petting.tick(false, 4, random), "warden no longer calm");
        assertTrue(petting.searchCooldown() >= 600 && petting.searchCooldown() <= 1200);
        petting.searched(false, random);
        assertTrue(petting.searchCooldown() >= 300 && petting.searchCooldown() <= 600);
    }
}
