package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TempleLayoutTest {

    private static final TempleLayout.Layout LAYOUT = TempleLayout.build(42L);
    private static final TempleLayout.Params P = LAYOUT.params();

    @Test
    void markersAreWhereTheyBelong() {
        assertEquals(new TempleLayout.Pos(0, -P.depth() + 2, 0), LAYOUT.altar());
        assertEquals("soul_block", LAYOUT.blocks().get(LAYOUT.altar()));
        assertEquals("sculk_enchanter", LAYOUT.blocks().get(LAYOUT.enchanter()));
        assertEquals(1, LAYOUT.blocks().values().stream().filter("sculk_enchanter"::equals).count(), "one enchanter");
        assertEquals(2, LAYOUT.chests().size());
        LAYOUT.chests().forEach(c -> assertTrue(LAYOUT.blocks().get(c).startsWith("minecraft:chest")));
        assertTrue(LAYOUT.enchanter().z() > 0 && LAYOUT.enchanter().y() < 0, "treasure hall in the south, below");
    }

    @Test
    void bossArenaIsOpenAroundTheAltarWithTheSoulRingInItsFloor() {
        int floor = -P.depth();
        assertTrue(TempleLayout.soulRing().size() >= 16);
        for (int[] xz : TempleLayout.soulRing()) {
            assertEquals("soul_block", LAYOUT.blocks().get(new TempleLayout.Pos(xz[0], floor - 1, xz[1])), "ring soul in the floor");
            assertEquals("air", LAYOUT.blocks().get(new TempleLayout.Pos(xz[0], floor, xz[1])), "room above it");
        }
        // the golem (2.9 x 4.2) lands 6 north of the altar and falls from the keep top: the column is open
        for (int y = floor; y <= TempleLayout.KEEP_TOP + 3; y++) {
            String b = LAYOUT.blocks().get(new TempleLayout.Pos(0, y, -6));
            assertTrue(b == null || b.equals("air"), "shaft open at y " + y + ": " + b);
        }
        assertEquals(11, TempleLayout.ARENA_RADIUS);
    }

    @Test
    void broodSpawnersStandInTheirZones() {
        assertEquals(12, LAYOUT.spawners().size());
        assertEquals(java.util.Map.of(1, 3L, 2, 5L, 3, 4L), LAYOUT.spawners().stream()
                .collect(java.util.stream.Collectors.groupingBy(TempleLayout.Spawner::zone, java.util.stream.Collectors.counting())));
        for (var sp : LAYOUT.spawners()) {
            assertEquals(TempleLayout.SPAWNER, LAYOUT.blocks().get(sp.pos()), sp.toString());
            assertEquals(sp.zone() == 1 ? 0 : -P.depth(), sp.pos().y(), "on the floor of its level: " + sp);
            var above = LAYOUT.blocks().get(new TempleLayout.Pos(sp.pos().x(), sp.pos().y() + 1, sp.pos().z()));
            assertTrue(above == null || "air".equals(above), "room above " + sp);
        }
        var inner = LAYOUT.spawners().stream().filter(sp -> sp.zone() == 3).toList();
        inner.forEach(sp -> assertTrue(Math.hypot(sp.pos().x(), sp.pos().z()) < 18, "zone 3 next to the arena: " + sp));
    }

    @Test
    void theTrialRoomsAreEmptyAndSealed() {
        assertEquals(Set.of("wither", "warden"), LAYOUT.chambers().keySet());
        assertTrue(LAYOUT.chambers().get("wither").min().x() > 0 && LAYOUT.chambers().get("warden").max().x() < 0);
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
                            assertTrue("reinforced_siftslate".equals(block) || "chiseled_siftslate".equals(block)
                                    || "charoite_block".equals(block), "shell " + x + " " + y + " " + z + ": " + block);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everythingStaysInsideTheFootprint() {
        int ext = P.extent();
        for (var p : LAYOUT.blocks().keySet()) {
            assertTrue(Math.abs(p.x()) <= ext && Math.abs(p.z()) <= ext, p.toString());
            assertTrue(p.y() >= P.bottom() && p.y() <= P.top(), p.toString());
        }
        assertTrue(LAYOUT.blocks().keySet().stream().anyMatch(p -> p.y() >= P.towerHeight()), "towers reach their height");
        // the arena shaft is open from the trial level to the sky
        for (int y = -P.depth(); y <= P.top(); y++) {
            String v = LAYOUT.blocks().get(new TempleLayout.Pos(5, y, 0));
            assertTrue(v == null || "air".equals(v), "shaft at y " + y + ": " + v);
        }
    }

    @Test
    void decayDependsOnTheSeedButNotTheStructure() {
        TempleLayout.Layout other = TempleLayout.build(43L);
        assertEquals(LAYOUT, TempleLayout.build(42L), "deterministic");
        assertNotEquals(LAYOUT.blocks(), other.blocks());
        assertEquals(LAYOUT.altar(), other.altar());
        assertEquals(LAYOUT.enchanter(), other.enchanter());
        assertEquals(LAYOUT.chambers(), other.chambers());
        assertTrue(TempleLayout.PALETTE.containsAll(LAYOUT.blocks().values().stream()
                .filter(v -> !v.equals("air") && !v.startsWith("minecraft:")).distinct().toList()));
        long missing = LAYOUT.blocks().entrySet().stream().filter(e -> e.getKey().y() > 4 && "air".equals(e.getValue())).count();
        assertTrue(missing > 100, "weathered at the top");
    }

    @Test
    void sizeComesFromTheParameters() {
        TempleLayout.Params big = new TempleLayout.Params(52, 18, 32, 20);
        TempleLayout.Layout layout = TempleLayout.build(42L, big);
        assertEquals(new TempleLayout.Pos(0, -18, 0), layout.altar());
        assertTrue(layout.blocks().keySet().stream().allMatch(p -> Math.abs(p.x()) <= 57 && p.y() >= -21 && p.y() <= 38));
        assertTrue(layout.blocks().keySet().stream().anyMatch(p -> Math.abs(p.x()) == 52 && p.y() == 17), "walls 18 high");
        // clamped to sane values
        assertEquals(new TempleLayout.Params(40, 10, 16, 16), new TempleLayout.Params(10, 2, 3, 4));
        walkable(layout);
    }

    @Test
    void altarEnchanterAndChestsCanBeReachedOnFootFromTheGate() {
        walkable(LAYOUT);
    }

    /** Breadth-first walk from in front of the gate: feet cell needs two free blocks and a solid one below. */
    private static void walkable(TempleLayout.Layout layout) {
        TempleLayout.Params p = layout.params();
        int ext = p.extent() + 3;
        Predicate<TempleLayout.Pos> solid = pos -> {
            String v = layout.blocks().get(pos);
            if (v == null) {
                return pos.y() < 0; // terrain below the court, cleared above it
            }
            return !v.equals("air") && !v.contains("chain") && !v.contains("lantern") && !v.contains("soul_fire");
        };
        Predicate<TempleLayout.Pos> standable = pos -> !solid.test(pos)
                && !solid.test(new TempleLayout.Pos(pos.x(), pos.y() + 1, pos.z()))
                && solid.test(new TempleLayout.Pos(pos.x(), pos.y() - 1, pos.z()));
        TempleLayout.Pos start = new TempleLayout.Pos(0, 0, p.radius() + 3);
        assertTrue(standable.test(start));
        Set<TempleLayout.Pos> seen = new HashSet<>();
        ArrayDeque<TempleLayout.Pos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            TempleLayout.Pos at = queue.poll();
            for (int[] d : dirs) {
                for (int dy = 1; dy >= -3; dy--) {
                    TempleLayout.Pos next = new TempleLayout.Pos(at.x() + d[0], at.y() + dy, at.z() + d[1]);
                    if (Math.abs(next.x()) > ext || Math.abs(next.z()) > ext || next.y() < p.bottom() || next.y() > p.top() + 2) {
                        continue;
                    }
                    if (dy == 1 && solid.test(new TempleLayout.Pos(at.x(), at.y() + 2, at.z()))) {
                        continue; // no room to jump
                    }
                    if (standable.test(next)) {
                        if (seen.add(next)) {
                            queue.add(next);
                        }
                        break;
                    }
                }
            }
        }
        assertTrue(seen.stream().anyMatch(s -> s.y() == -p.depth() && Math.abs(s.x()) <= 3 && Math.abs(s.z()) <= 3),
                "arena floor next to the altar reachable");
        for (TempleLayout.Pos target : new TempleLayout.Pos[] {layout.enchanter(), layout.chests().get(0), layout.chests().get(1)}) {
            assertTrue(seen.stream().anyMatch(s -> Math.abs(s.x() - target.x()) + Math.abs(s.z() - target.z()) <= 2
                    && Math.abs(s.y() - target.y()) <= 1), "reachable: " + target);
        }
        assertTrue(seen.stream().anyMatch(s -> s.y() == p.wallHeight()), "wall walk reachable");
        assertTrue(seen.stream().anyMatch(s -> s.y() == 6 && Math.abs(s.x()) <= 16 && Math.abs(s.z()) <= 16), "keep reachable");
    }
}
