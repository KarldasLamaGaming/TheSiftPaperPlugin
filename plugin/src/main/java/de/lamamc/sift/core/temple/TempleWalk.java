package de.lamamc.sift.core.temple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walkability check of a temple (alpha.42e), without a world: a body of 3 x 3 blocks and 3 (or 4) high walks from the
 * gate. Steps up or down by one block (stairs), never drops further. It must reach the court, every brood spawner (zone 3
 * with a 4 high body for the Charoite Wisp), the arena at the altar, the chests and the enchanter. Reported as well: stairs
 * that end at a wall or in the air, and cells a player can stand in that no 3 x 3 x 3 body reaches (corridors under 3 wide
 * or high). The same check runs on a built temple through {@link Grid} (/sift temple validate).
 */
public final class TempleWalk {

    /** What a block is for walking. */
    public enum Kind {
        /** Free (air, plants). */
        AIR,
        /** Solid, one can stand on it (also stairs and slabs, a spawner, a chest). */
        SOLID,
        /** A stair; {@link Grid#facing} says which way it rises. */
        STAIR,
        /** Must not be entered (fire, lava). */
        HAZARD
    }

    /** The blocks around a temple, coordinates relative to its centre (court floor level y = 0 stands on y = -1). */
    public interface Grid {
        Kind kind(int x, int y, int z);

        /** Direction a stair rises to as (dx, dz), or null. */
        int[] facing(int x, int y, int z);
    }

    /** A place the walk must reach: its block and how close a body has to come (blocks, horizontally). */
    public record Target(String name, TempleLayout.Pos pos, int reach, int height) {
    }

    /** Result: the targets not reached, stairs that end nowhere, cells only a narrow body reaches. */
    public record Report(int bodyPositions, List<String> missing, List<TempleLayout.Pos> deadEndStairs, List<TempleLayout.Pos> narrow) {

        public boolean ok() {
            return missing.isEmpty() && deadEndStairs.isEmpty() && narrow.isEmpty();
        }

        public List<String> lines() {
            List<String> out = new ArrayList<>();
            out.add(bodyPositions + " Standplätze für einen 3x3x3-Körper erreichbar");
            out.add(missing.isEmpty() ? "alle Ziele erreichbar" : "nicht erreichbar: " + String.join(", ", missing));
            out.add(deadEndStairs.isEmpty() ? "keine Treppe endet an einer Wand oder im Leeren"
                    : deadEndStairs.size() + " Treppenstufen ohne Anschluss, z. B. " + sample(deadEndStairs));
            out.add(narrow.isEmpty() ? "keine Engstelle unter 3 x 3 x 3"
                    : narrow.size() + " Standplätze nur für Spieler (unter 3 breit oder hoch), z. B. " + sample(narrow));
            return out;
        }

        private static String sample(List<TempleLayout.Pos> list) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < Math.min(4, list.size()); i++) {
                TempleLayout.Pos p = list.get(i);
                s.append(i == 0 ? "" : "; ").append(p.x()).append(' ').append(p.y()).append(' ').append(p.z());
            }
            return s.toString();
        }
    }

    private TempleWalk() {
    }

    /** The grid of a planned temple: unset blocks are air above the court floor and rock below it. */
    public static Grid grid(TempleLayout.Layout layout) {
        Map<TempleLayout.Pos, String> blocks = layout.blocks();
        int extent = layout.params().extent();
        return new Grid() {
            @Override
            public Kind kind(int x, int y, int z) {
                String v = blocks.get(new TempleLayout.Pos(x, y, z));
                if (v == null) {
                    boolean inside = Math.max(Math.abs(x), Math.abs(z)) <= extent;
                    return y >= 0 || !inside && y >= -1 ? Kind.AIR : Kind.SOLID;
                }
                return kindOf(v);
            }

            @Override
            public int[] facing(int x, int y, int z) {
                return facingOf(blocks.get(new TempleLayout.Pos(x, y, z)));
            }
        };
    }

    static Kind kindOf(String v) {
        if (TempleLayout.AIR.equals(v)) {
            return Kind.AIR;
        }
        if (v.contains("soul_fire") || v.contains("lava")) {
            return Kind.HAZARD;
        }
        return v.contains("_stairs") ? Kind.STAIR : Kind.SOLID;
    }

    static int[] facingOf(String v) {
        if (v == null || !v.contains("_stairs")) {
            return null;
        }
        if (v.contains("facing=north")) {
            return new int[] {0, -1};
        }
        if (v.contains("facing=south")) {
            return new int[] {0, 1};
        }
        if (v.contains("facing=east")) {
            return new int[] {1, 0};
        }
        return new int[] {-1, 0};
    }

    /** The targets of a planned temple: court, spawners (zone 3 four high), altar, chests, enchanter. */
    public static List<Target> targets(TempleLayout.Layout layout) {
        return targets(layout, layout.spawners());
    }

    /** The same with the spawners of an older layout version. */
    public static List<Target> targets(TempleLayout.Layout layout, List<TempleLayout.Spawner> spawners) {
        List<Target> out = new ArrayList<>();
        int r = layout.params().radius();
        out.add(new Target("Hof", new TempleLayout.Pos(0, 0, r - 10), 2, 3));
        for (int i = 0; i < spawners.size(); i++) {
            TempleLayout.Spawner s = spawners.get(i);
            out.add(new Target("Spawner " + (i + 1) + " (Zone " + s.zone() + ")", s.pos(), 2, s.zone() == 3 ? 4 : 3));
        }
        TempleLayout.Pos altar = layout.altar();
        out.add(new Target("Altar (Arena)", new TempleLayout.Pos(altar.x(), altar.y() - 2, altar.z()), 3, 4));
        for (int i = 0; i < layout.chests().size(); i++) {
            out.add(new Target("Truhe " + (i + 1), layout.chests().get(i), 2, 3));
        }
        out.add(new Target("Enchanter", layout.enchanter(), 2, 3));
        return out;
    }

    /**
     * Walks the grid from {@code start} (a standing position: feet block, in the gate) within {@code extent} blocks around the centre
     * and between {@code minY} and {@code maxY}. Narrow places count only where a player walks in {@code planned} (the
     * unweathered plan) too, so breaches and rubble of the ruin, and rooms only a breach leads into, are not reported.
     */
    public static Report check(Grid grid, Grid planned, TempleLayout.Pos start, List<Target> targets, int extent, int minY, int maxY) {
        // in a built temple the gate floor may sit a block off: the first standing place over or under the start
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            if (body(grid, start.x(), start.y() + dy, start.z(), 3)) {
                start = new TempleLayout.Pos(start.x(), start.y() + dy, start.z());
                break;
            }
        }
        Set<TempleLayout.Pos> body3 = walk(grid, List.of(start), 3, extent, minY, maxY);
        // a 4 high body (the flying Charoite Wisp) lives in the zones: it starts wherever the 3 high way has room for it
        Set<TempleLayout.Pos> body4 = body3;
        if (targets.stream().anyMatch(t -> t.height() > 3)) {
            List<TempleLayout.Pos> seeds = body3.stream().filter(b -> body(grid, b.x(), b.y(), b.z(), 4)).toList();
            body4 = walk(grid, seeds, 4, extent, minY, maxY);
        }
        List<String> missing = new ArrayList<>();
        for (Target t : targets) {
            Set<TempleLayout.Pos> bodies = t.height() > 3 ? body4 : body3;
            boolean reached = false;
            for (TempleLayout.Pos b : bodies) {
                if (Math.abs(b.x() - t.pos().x()) <= t.reach() && Math.abs(b.z() - t.pos().z()) <= t.reach()
                        && Math.abs(b.y() - t.pos().y()) <= 1) {
                    reached = true;
                    break;
                }
            }
            if (!reached) {
                missing.add(t.name());
            }
        }
        return new Report(body3.size(), missing, deadEndStairs(grid, extent, minY, maxY),
                narrow(grid, planned, body3, start, extent, minY, maxY));
    }

    private static boolean free(Grid g, int x, int y, int z) {
        return g.kind(x, y, z) == Kind.AIR;
    }

    private static boolean ground(Grid g, int x, int y, int z) {
        Kind k = g.kind(x, y, z);
        return k == Kind.SOLID || k == Kind.STAIR;
    }

    /**
     * A body of 3 x 3 x h with its feet at y around (x, z): all free, resting on at least one block under its footprint
     * (like a mob's box on stairs: it sits on the highest step under it), none of them a hazard.
     */
    static boolean body(Grid g, int x, int y, int z, int h) {
        boolean rests = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Kind k = g.kind(x + dx, y - 1, z + dz);
                if (k == Kind.HAZARD) {
                    return false;
                }
                rests |= k == Kind.SOLID || k == Kind.STAIR;
            }
        }
        if (!rests) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy < h; dy++) {
                    if (!free(g, x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean headroom(Grid g, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!free(g, x + dx, y, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** A body also moves diagonally (diagonal corridors, corners of rooms); the 3 x 3 boxes at both ends must be free. */
    private static final int[][] BODY_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    static Set<TempleLayout.Pos> walk(Grid g, List<TempleLayout.Pos> starts, int h, int extent, int minY, int maxY) {
        Set<TempleLayout.Pos> seen = new HashSet<>();
        ArrayDeque<TempleLayout.Pos> queue = new ArrayDeque<>();
        for (TempleLayout.Pos start : starts) {
            if (body(g, start.x(), start.y(), start.z(), h) && seen.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            TempleLayout.Pos p = queue.poll();
            for (int[] s : BODY_STEPS) {
                int x = p.x() + s[0];
                int z = p.z() + s[1];
                if (Math.max(Math.abs(x), Math.abs(z)) > extent) {
                    continue;
                }
                for (int dy = 1; dy >= -1; dy--) {
                    int y = p.y() + dy;
                    if (y < minY || y > maxY) {
                        continue;
                    }
                    TempleLayout.Pos n = new TempleLayout.Pos(x, y, z);
                    // stepping up needs room for the head above the old position too
                    if (!seen.contains(n) && body(g, x, y, z, h) && (dy <= 0 || headroom(g, p.x(), p.y() + h, p.z()))) {
                        seen.add(n);
                        queue.add(n);
                        break;
                    }
                }
            }
        }
        return seen;
    }

    /** Stairs whose top does not lead onto a step or floor one higher, or whose foot not onto floor one lower. */
    static List<TempleLayout.Pos> deadEndStairs(Grid g, int extent, int minY, int maxY) {
        List<TempleLayout.Pos> out = new ArrayList<>();
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (g.kind(x, y, z) != Kind.STAIR) {
                        continue;
                    }
                    int[] f = g.facing(x, y, z);
                    int fx = x + f[0];
                    int fz = z + f[1];
                    // up: a step one higher, or a floor at the stair's height + 1 (the standing level), with room above
                    boolean up = g.kind(fx, y + 1, fz) == Kind.STAIR && free(g, fx, y + 2, fz)
                            || ground(g, fx, y, fz) && free(g, fx, y + 1, fz) && free(g, fx, y + 2, fz)
                            || ground(g, fx, y + 1, fz) && free(g, fx, y + 2, fz) && free(g, fx, y + 3, fz);
                    int bx = x - f[0];
                    int bz = z - f[1];
                    boolean down = g.kind(bx, y - 1, bz) == Kind.STAIR
                            || ground(g, bx, y - 1, bz) && free(g, bx, y, bz) && free(g, bx, y + 1, bz);
                    if (!up || !down) {
                        out.add(new TempleLayout.Pos(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Cells a player can walk to (feet free, head free, ground below) that are more than one block away from everything a
     * reachable 3 x 3 x 3 body covers: ways and rooms under 3 wide or high. A single row beside a pillar, behind a chest
     * or in a crenel gap is not reported.
     */
    static List<TempleLayout.Pos> narrow(Grid g, Grid planned, Set<TempleLayout.Pos> bodies, TempleLayout.Pos start, int extent, int minY,
            int maxY) {
        Set<TempleLayout.Pos> covered = new HashSet<>();
        for (TempleLayout.Pos b : bodies) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    covered.add(new TempleLayout.Pos(b.x() + dx, b.y(), b.z() + dz));
                }
            }
        }
        Set<TempleLayout.Pos> plannedWays = planned == g ? null : walkPlayer(planned, start, extent, minY, maxY);
        List<TempleLayout.Pos> out = new ArrayList<>();
        for (TempleLayout.Pos p : walkPlayer(g, start, extent, minY, maxY)) {
            if (!covered.contains(p) && !besideCovered(covered, p) && (plannedWays == null || plannedWays.contains(p))) {
                out.add(p);
            }
        }
        out.sort(java.util.Comparator.comparingInt(TempleLayout.Pos::y).thenComparingInt(TempleLayout.Pos::x)
                .thenComparingInt(TempleLayout.Pos::z));
        return out;
    }

    private static boolean besideCovered(Set<TempleLayout.Pos> covered, TempleLayout.Pos p) {
        for (int[] s : STEPS) {
            for (int dy = -1; dy <= 1; dy++) {
                if (covered.contains(new TempleLayout.Pos(p.x() + s[0], p.y() + dy, p.z() + s[1]))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Where a player (1 x 1 x 2) walks from the start, inside the temple's square only. */
    static Set<TempleLayout.Pos> walkPlayer(Grid g, TempleLayout.Pos start, int extent, int minY, int maxY) {
        Set<TempleLayout.Pos> seen = new HashSet<>();
        ArrayDeque<TempleLayout.Pos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        Set<TempleLayout.Pos> inside = new HashSet<>();
        while (!queue.isEmpty()) {
            TempleLayout.Pos p = queue.poll();
            if (Math.max(Math.abs(p.x()), Math.abs(p.z())) < extent - 4) {
                inside.add(p);
            }
            for (int[] s : STEPS) {
                int x = p.x() + s[0];
                int z = p.z() + s[1];
                if (Math.max(Math.abs(x), Math.abs(z)) > extent) {
                    continue;
                }
                for (int dy = 1; dy >= -1; dy--) {
                    int y = p.y() + dy;
                    TempleLayout.Pos n = new TempleLayout.Pos(x, y, z);
                    if (y >= minY && y <= maxY && !seen.contains(n) && ground(g, x, y - 1, z) && free(g, x, y, z)
                            && free(g, x, y + 1, z) && (dy <= 0 || free(g, p.x(), p.y() + 2, p.z()))) {
                        seen.add(n);
                        queue.add(n);
                        break;
                    }
                }
            }
        }
        return inside;
    }

    /** Counts of a report for logs and tests. */
    public static Map<String, Integer> summary(Report r) {
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("bodies", r.bodyPositions());
        out.put("missing", r.missing().size());
        out.put("deadEndStairs", r.deadEndStairs().size());
        out.put("narrow", r.narrow().size());
        return out;
    }
}
