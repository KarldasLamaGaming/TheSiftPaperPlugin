package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TempleWalkTest {

    static TempleWalk.Report report(long seed, TempleLayout.Params p) {
        TempleLayout.Layout layout = TempleLayout.build(seed, p);
        return TempleWalk.check(TempleWalk.grid(layout), TempleWalk.grid(TempleLayout.unweathered(seed, p)),
                new TempleLayout.Pos(0, 0, p.radius() - 2), TempleWalk.targets(layout), p.extent(), p.bottom() - 1, p.top());
    }

    @Test
    void theTempleCanBeWalkedByA3x3x3BodyFromTheGate() {
        for (long seed = 1; seed <= 3; seed++) {
            TempleWalk.Report r = report(seed, TempleLayout.Params.DEFAULT);
            assertTrue(r.ok(), "seed " + seed + ": " + r.lines());
            assertEquals(List.of(), r.missing());
        }
    }

    @Test
    void everyAllowedSizeIsWalkableToo() {
        TempleLayout.Params[] sizes = {new TempleLayout.Params(48, 18, 30, 20), new TempleLayout.Params(64, 24, 48, 24),
            new TempleLayout.Params(40, 10, 16, 24), new TempleLayout.Params(40, 24, 30, 16), new TempleLayout.Params(52, 12, 26, 18)};
        for (TempleLayout.Params p : sizes) {
            TempleWalk.Report r = report(7L, p);
            assertTrue(r.ok(), p + ": " + r.lines());
        }
        // the wall walk stairs need wall height + 2 between the towers: capped at radius - 19
        assertEquals(21, new TempleLayout.Params(40, 24, 30, 16).wallHeight());
    }

    @Test
    void theTargetsAreTheCourtSpawnersArenaChestsAndEnchanter() {
        TempleLayout.Layout layout = TempleLayout.build(1L);
        List<TempleWalk.Target> targets = TempleWalk.targets(layout);
        assertEquals(1 + 12 + 1 + 2 + 1, targets.size());
        assertEquals(4, targets.stream().filter(t -> t.height() == 4).count() - 1, "zone 3 four high (and the arena)");
    }

    // ------------------------------------------------------------------- the walker on small hand-made grids

    /** A floor at y -1 over x, z in [-20, 20]; cells set to AIR or a stair are carved into rock above it. */
    private static final class Rock implements TempleWalk.Grid {
        final Map<TempleLayout.Pos, TempleWalk.Kind> cells = new HashMap<>();
        final Map<TempleLayout.Pos, int[]> facing = new HashMap<>();

        void air(int x1, int y1, int z1, int x2, int y2, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        cells.put(new TempleLayout.Pos(x, y, z), TempleWalk.Kind.AIR);
                    }
                }
            }
        }

        @Override
        public TempleWalk.Kind kind(int x, int y, int z) {
            return cells.getOrDefault(new TempleLayout.Pos(x, y, z), TempleWalk.Kind.SOLID);
        }

        @Override
        public int[] facing(int x, int y, int z) {
            return facing.get(new TempleLayout.Pos(x, y, z));
        }
    }

    private static TempleWalk.Report walk(Rock g, TempleLayout.Pos target) {
        return TempleWalk.check(g, g, new TempleLayout.Pos(0, 0, 0), List.of(new TempleWalk.Target("Ziel", target, 1, 3)), 20, -5, 10);
    }

    @Test
    void aCorridorThreeWideAndHighIsFineOneWideIsNarrow() {
        Rock wide = new Rock();
        wide.air(-1, 0, -1, 1, 2, 15);
        assertTrue(walk(wide, new TempleLayout.Pos(0, 0, 15)).ok());

        Rock narrow = new Rock();
        narrow.air(-1, 0, -1, 1, 2, 1); // room at the start
        narrow.air(0, 0, 2, 0, 1, 12); // 1 wide, 2 high
        narrow.air(-1, 0, 13, 1, 2, 15); // room at the end
        TempleWalk.Report r = walk(narrow, new TempleLayout.Pos(0, 0, 15));
        assertEquals(List.of("Ziel"), r.missing());
        assertFalse(r.narrow().isEmpty(), "the 1 wide corridor is reported");
    }

    @Test
    void stairsNeedALandingAndTwoMoreThanTheBodyAboveThem() {
        // stairs down along +z: step i at y -1 - i rising back north, 3 wide
        Rock g = new Rock();
        g.air(-1, 0, -3, 1, 4, 0);
        for (int i = 0; i < 4; i++) {
            int z = 1 + i;
            for (int x = -1; x <= 1; x++) {
                TempleLayout.Pos step = new TempleLayout.Pos(x, -1 - i, z);
                g.cells.put(step, TempleWalk.Kind.STAIR);
                g.facing.put(step, new int[] {0, -1});
            }
            g.air(-1, -i, z, 1, -i + 4, z);
        }
        g.air(-1, -4, 5, 1, 0, 9); // landing at the foot
        assertTrue(walk(g, new TempleLayout.Pos(0, -4, 8)).ok(), walk(g, new TempleLayout.Pos(0, -4, 8)).lines().toString());

        // the same stairs ending at a wall: the last step leads onto nothing
        Rock dead = new Rock();
        dead.cells.putAll(g.cells);
        dead.facing.putAll(g.facing);
        for (int x = -1; x <= 1; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = 5; z <= 9; z++) {
                    dead.cells.remove(new TempleLayout.Pos(x, y, z));
                }
            }
        }
        assertFalse(TempleWalk.deadEndStairs(dead, 20, -5, 10).isEmpty());
    }
}
