package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TempleLayoutTest {

    private static final TempleLayout.Layout LAYOUT = TempleLayout.build(42L);

    @Test
    void altarEnchanterAndChestsAreWhereTheyBelong() {
        assertEquals(new TempleLayout.Pos(0, 2, 0), LAYOUT.altar());
        assertEquals("soul_block", LAYOUT.blocks().get(LAYOUT.altar()));
        assertEquals("sculk_enchanter", LAYOUT.blocks().get(LAYOUT.enchanter()));
        assertEquals(1, LAYOUT.blocks().values().stream().filter("sculk_enchanter"::equals).count(), "one enchanter");
        assertEquals(2, LAYOUT.chests().size());
        LAYOUT.chests().forEach(c -> assertTrue(LAYOUT.blocks().get(c).startsWith("minecraft:chest")));
    }

    @Test
    void theChambersAreEmptyAndSealed() {
        assertEquals(2, LAYOUT.chambers().size());
        for (var chamber : LAYOUT.chambers().values()) {
            var min = chamber.min();
            var max = chamber.max();
            for (int x = min.x() - 1; x <= max.x() + 1; x++) {
                for (int y = min.y() - 1; y <= max.y() + 1; y++) {
                    for (int z = min.z() - 1; z <= max.z() + 1; z++) {
                        String block = LAYOUT.blocks().get(new TempleLayout.Pos(x, y, z));
                        if (chamber.contains(x, y, z)) {
                            assertEquals("air", block, "inside " + x + " " + y + " " + z);
                        } else {
                            assertTrue("reinforced_siftslate".equals(block) || "chiseled_siftslate".equals(block),
                                    "shell " + x + " " + y + " " + z + ": " + block);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everythingStaysInsideTheFootprintAndTheArenaIsFree() {
        for (var e : LAYOUT.blocks().entrySet()) {
            var p = e.getKey();
            assertTrue(Math.abs(p.x()) <= TempleLayout.RADIUS && Math.abs(p.z()) <= TempleLayout.RADIUS, p.toString());
            assertTrue(p.y() >= -1 && p.y() < TempleLayout.CLEAR_HEIGHT, p.toString());
            if (p.y() >= 0 && Math.sqrt(p.x() * p.x() + p.z() * p.z()) < 11 && Math.max(Math.abs(p.x()), Math.abs(p.z())) > 1) {
                throw new AssertionError("block in the arena at " + p + ": " + e.getValue());
            }
        }
        for (int x = -3; x <= 3; x++) {
            assertEquals(null, LAYOUT.blocks().get(new TempleLayout.Pos(x, 0, TempleLayout.RADIUS)), "entrance open");
        }
    }

    @Test
    void decayDependsOnTheSeedButNotTheStructure() {
        TempleLayout.Layout other = TempleLayout.build(43L);
        assertEquals(LAYOUT, TempleLayout.build(42L), "deterministic");
        assertNotEquals(LAYOUT.blocks(), other.blocks());
        assertEquals(LAYOUT.altar(), other.altar());
        assertEquals(LAYOUT.chambers(), other.chambers());
        assertTrue(TempleLayout.PALETTE.containsAll(LAYOUT.blocks().values().stream()
                .filter(v -> !v.equals("air") && !v.startsWith("minecraft:")).distinct().toList()));
    }
}
