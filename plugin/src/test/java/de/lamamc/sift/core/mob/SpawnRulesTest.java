package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SpawnRulesTest {

    private static SpawnRules rules() {
        return new SpawnRules(80, 32, 76, 0.8, 64, 1, 3, List.of(
                new SpawnRules.Profile(List.of("overgrown_forest"), 0.36, 8),
                new SpawnRules.Profile(List.of("overgrown_forest", "overgrown"), 0.28, 6),
                new SpawnRules.Profile(List.of(), 0.18, 5)));
    }

    @Test
    void firstMatchingProfileWins() {
        assertEquals(8, rules().profile("overgrown_forest").cap());
        assertEquals(6, rules().profile("overgrown").cap());
        assertEquals(5, rules().profile("wastes").cap());
        SpawnRules onlyForest = new SpawnRules(80, 32, 76, 0.8, 64, 1, 3,
                List.of(new SpawnRules.Profile(List.of("overgrown_forest"), 1, 4)));
        assertNull(onlyForest.profile("wastes"));
        assertFalse(onlyForest.rollAttempt("wastes", 0, new Random(1)));
    }

    @Test
    void capStopsAttemptsAndLimitsGroups() {
        SpawnRules always = new SpawnRules(80, 32, 76, 0.8, 64, 3, 3, List.of(new SpawnRules.Profile(List.of(), 1.0, 4)));
        Random random = new Random(7);
        // like the original: without room for the smallest group (3) nothing spawns
        assertFalse(always.rollAttempt("x", 2, random));
        assertTrue(always.rollAttempt("x", 1, random));
        assertEquals(0, always.groupSize("x", 2, random));
        assertEquals(3, always.groupSize("x", 0, random));
        SpawnRules sifter = new SpawnRules(80, 32, 76, 0.8, 96, 1, 5, List.of(new SpawnRules.Profile(List.of(), 1.0, 8)), 18, 4);
        for (int i = 0; i < 200; i++) {
            int size = sifter.groupSize("x", 6, random);
            assertTrue(size >= 1 && size <= 2, "room 2: " + size);
        }
    }

    @Test
    void chanceIsRespected() {
        Random random = new Random(42);
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            if (rules().rollAttempt("overgrown_forest", 0, random)) {
                hits++;
            }
        }
        assertEquals(0.36, hits / 20000.0, 0.02);
    }

    @Test
    void offsetsStayInTheRing() {
        Random random = new Random(3);
        int surface = 0;
        for (int i = 0; i < 5000; i++) {
            SpawnRules.Offset offset = rules().offset(random);
            double distance = Math.hypot(offset.dx(), offset.dz());
            assertTrue(distance >= 31 && distance <= 77, "distance " + distance);
            if (offset.surface()) {
                surface++;
            }
        }
        assertEquals(0.8, surface / 5000.0, 0.03);
    }

    @Test
    void spiralFollowsTheGoldenAngle() {
        SpawnRules sifter = new SpawnRules(80, 32, 76, 0.8, 96, 1, 5, List.of(), 18, 4);
        Random random = new Random(11);
        for (int attempt = 0; attempt < 18; attempt++) {
            SpawnRules.Offset offset = sifter.offset(0.5, attempt, random);
            double angle = Math.atan2(offset.dz(), offset.dx());
            double expected = Math.atan2(Math.sin(0.5 + attempt * SpawnRules.GOLDEN_ANGLE), Math.cos(0.5 + attempt * SpawnRules.GOLDEN_ANGLE));
            double diff = Math.abs(Math.atan2(Math.sin(angle - expected), Math.cos(angle - expected)));
            assertTrue(diff < 0.04, "attempt " + attempt + " angle off by " + diff);
            double distance = Math.hypot(offset.dx(), offset.dz());
            assertTrue(distance >= 31 && distance <= 77, "distance " + distance);
        }
    }

    @Test
    void dueEveryIntervalPerPlayer() {
        int due = 0;
        for (long tick = 0; tick < 800; tick++) {
            if (rules().due(tick, 17)) {
                due++;
            }
        }
        assertEquals(10, due);
        assertThrows(IllegalArgumentException.class, () -> new SpawnRules(80, 10, 5, 0.5, 64, 1, 1, List.of()));
    }
}
