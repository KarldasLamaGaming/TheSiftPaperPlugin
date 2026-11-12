package de.lamamc.sift.core.boss;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class GearTableTest {

    private static final GearTable TABLE = new GearTable(List.of(
            new GearTable.Entry("minecraft:netherite_helmet", 3, Map.of("minecraft:protection", new int[] {3, 4},
                    "minecraft:mending", new int[] {0, 1})),
            new GearTable.Entry("minecraft:netherite_sword", 1, Map.of("minecraft:sharpness", new int[] {5, 5}))));

    @Test
    void weightsAndLevelsStayInTheirRanges() {
        SplittableRandom random = new SplittableRandom(7);
        Map<String, Integer> count = new HashMap<>();
        int mending = 0;
        for (int i = 0; i < 4000; i++) {
            GearTable.Roll r = TABLE.roll(random);
            count.merge(r.item(), 1, Integer::sum);
            if (r.item().endsWith("helmet")) {
                int p = r.enchantments().get("minecraft:protection");
                assertTrue(p >= 3 && p <= 4);
                if (r.enchantments().containsKey("minecraft:mending")) {
                    mending++;
                    assertEquals(1, r.enchantments().get("minecraft:mending"));
                }
            } else {
                assertEquals(Map.of("minecraft:sharpness", 5), r.enchantments());
            }
        }
        double helmets = count.get("minecraft:netherite_helmet") / 4000.0;
        assertTrue(helmets > 0.70 && helmets < 0.80, "weight 3 of 4: " + helmets);
        assertTrue(mending > 1200 && mending < 1800, "min 0 means about half: " + mending);
    }

    @Test
    void sameSeedSameRollAndEmptyTables() {
        assertEquals(TABLE.roll(new SplittableRandom(3)), TABLE.roll(new SplittableRandom(3)));
        assertNull(new GearTable(List.of()).roll(new SplittableRandom(1)));
        assertNull(new GearTable(List.of(new GearTable.Entry("x", 0, Map.of()))).roll(new SplittableRandom(1)));
    }

    @Test
    void levelRangesFromConfigValues() {
        assertArrayEquals(new int[] {2, 2}, GearTable.range(2));
        assertArrayEquals(new int[] {1, 3}, GearTable.range(List.of(1, 3)));
        assertNull(GearTable.range("drei"));
        assertNull(GearTable.range(List.of(1)));
    }
}
