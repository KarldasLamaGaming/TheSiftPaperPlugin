package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class AllayRulesTest {

    @Test
    void groupsStayWithinProfileAndCap() {
        AllayRules.Profile forest = AllayRules.DEFAULTS.get("overgrown_forest");
        Random random = new Random(3);
        int spawnedChecks = 0;
        for (int i = 0; i < 1000; i++) {
            int size = AllayRules.groupSize(forest, 0, random);
            if (size > 0) {
                spawnedChecks++;
                assertTrue(size >= 2 && size <= 4, "2–4 in the forest: " + size);
            }
        }
        assertTrue(spawnedChecks > 650 && spawnedChecks < 790, "72 % of the checks: " + spawnedChecks);
    }

    @Test
    void localCapLimitsTheGroup() {
        AllayRules.Profile clearing = AllayRules.DEFAULTS.get("overgrown_clearing");
        AllayRules.Profile always = new AllayRules.Profile(1.0, clearing.minGroup(), clearing.maxGroup(), clearing.localCap());
        for (int seed = 0; seed < 50; seed++) {
            int size = AllayRules.groupSize(always, 7, new Random(seed));
            assertEquals(1, size, "only one place left of 8");
            assertEquals(0, AllayRules.groupSize(always, 8, new Random(seed)), "cap reached");
        }
        AllayRules.Profile wastes = new AllayRules.Profile(1.0, 2, 3, 12);
        assertEquals(0, AllayRules.groupSize(wastes, 11, new Random(1)), "one place left but groups start at 2");
    }

    @Test
    void spiralStaysInTheRing() {
        Random random = new Random(9);
        for (int attempt = 0; attempt < 18; attempt++) {
            int[] o = AllayRules.offset(0.7, attempt, 34, 80, random);
            double d = Math.sqrt(o[0] * o[0] + o[1] * o[1]);
            assertTrue(d >= 33 && d <= 81, "34–80 blocks: " + d);
        }
    }
}
