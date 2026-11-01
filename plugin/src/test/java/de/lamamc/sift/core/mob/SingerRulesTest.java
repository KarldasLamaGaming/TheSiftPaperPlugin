package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SingerRulesTest {

    private final SingerRules rules = new SingerRules(SingerRules.ticks(7.375), SingerRules.ticks(8.0417), SingerRules.ticks(5),
            SingerRules.defaultWaveTicks(), 0.6, 3.0);

    @Test
    void phasesFollowTheAnimationLengths() {
        assertEquals(148, rules.appearTicks());
        assertEquals(161, rules.singTicks());
        assertEquals(100, rules.disappearTicks());
        assertEquals(SingerRules.Phase.APPEAR, rules.phase(0));
        assertEquals(SingerRules.Phase.SING, rules.phase(148));
        assertEquals(SingerRules.Phase.DISAPPEAR, rules.phase(309));
        assertEquals(SingerRules.Phase.GONE, rules.phase(408));
    }

    @Test
    void eightWavesDuringTheSong() {
        assertArrayEquals(new int[] {3, 25, 42, 57, 75, 92, 98, 117}, SingerRules.defaultWaveTicks());
        int waves = 0;
        for (int tick = 0; tick < 420; tick++) {
            int wave = rules.waveAt(tick);
            if (wave >= 0) {
                assertEquals(SingerRules.Phase.SING, rules.phase(tick));
                waves++;
            }
        }
        assertEquals(8, waves);
        assertEquals(10, rules.travelTicks(6.0));
        assertEquals(1, rules.travelTicks(0.1));
    }

    @Test
    void consolesRightFarToNearThenLeftNearToFar() {
        // Singer at 0/0 looking south (+z): its right is -x
        List<double[]> consoles = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            consoles.add(new double[] {-i * 2, 0, 3});
            consoles.add(new double[] {i * 2, 0, 3});
        }
        List<double[]> order = SingerRules.order(consoles, new double[] {0, 0, 0}, 0);
        double[] xs = order.stream().mapToDouble(c -> c[0]).toArray();
        assertArrayEquals(new double[] {-8, -6, -4, -2, 2, 4, 6, 8}, xs);
    }

    @Test
    void atMostEightConsoles() {
        List<double[]> consoles = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            consoles.add(new double[] {i, 0, 2});
        }
        assertEquals(8, SingerRules.order(consoles, new double[] {0, 0, 0}, 0).size());
    }

    @Test
    void facesTheNearestConsole() {
        double[] at = {0, 0, 0};
        assertEquals(270f, SingerRules.cardinalYaw(at, new double[] {5, 0, 1}));
        assertEquals(90f, SingerRules.cardinalYaw(at, new double[] {-5, 0, 1}));
        assertEquals(0f, SingerRules.cardinalYaw(at, new double[] {1, 0, 5}));
        assertEquals(180f, SingerRules.cardinalYaw(at, new double[] {1, 0, -5}));
        assertEquals(0f, SingerRules.cardinalYaw(at, null));
    }

    @Test
    void headroom() {
        assertTrue(SingerRules.headroomOk(new int[] {4, 5, 4}, 4));
        assertFalse(SingerRules.headroomOk(new int[] {4, 3, 6}, 4));
    }

    @Test
    void barterTableLikeTheOriginal() {
        BarterTable table = BarterTable.original();
        assertEquals(200, table.totalWeight());
        assertEquals("minecraft:iron_block", table.entryFor(0).item());
        assertEquals("minecraft:iron_block", table.entryFor(29).item());
        assertEquals("minecraft:netherite_upgrade_smithing_template", table.entryFor(30).item());
        assertEquals("the_sift:music_disc_wellspring", table.entryFor(69).item());
        assertEquals("#wool", table.entryFor(199).item());
        Random random = new Random(1);
        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < 40000; i++) {
            BarterTable.Reward reward = table.roll(random);
            hits.merge(reward.item(), 1, Integer::sum);
            if (reward.item().equals("minecraft:carrot")) {
                assertTrue(reward.count() >= 8 && reward.count() <= 20);
            }
        }
        assertEquals(30 / 200.0, hits.get("minecraft:iron_block") / 40000.0, 0.01);
        assertEquals(3 / 200.0, hits.get("the_sift:music_disc_wellspring") / 40000.0, 0.004);
    }
}
