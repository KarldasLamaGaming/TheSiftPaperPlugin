package de.lamamc.sift.core.temple;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The block plan of a temple (phase 4, alpha.41.1, Karl's choice "M"), relative to its centre: y 0 = the level players
 * stand on in the court (the court floor is y -1). A ruined bastion over a trial level:
 * <ul>
 * <li>above: square curtain wall with wall walk and crenels, buttresses, four round corner towers and three wall
 * towers (north, east, west) with charoite pennants and banners, a heavy gatehouse in the south, ruined barracks in
 * the court, a square chasm with soul fire around the raised keep, crossed by four bridges that climb to the keep,
 * obelisks with charoite tips on the keep;</li>
 * <li>in the middle an open shaft from the keep down to the arena with the Seelen-Altar;</li>
 * <li>below (depth blocks down): round ring corridor with eight spokes, pillar chambers on the diagonals, the sealed
 * trial rooms of the wither (east) and the warden (west, both empty until their trials), the treasure hall in the south
 * with the Sculk Enchanter and two chests, a north chamber; four stairways lead down from the court into the pillar
 * chambers.</li>
 * </ul>
 * Since alpha.42e (layout version 4) every way, room and spawner zone is at least 3 wide and 3 high (zone 3 and the arena
 * gates 4 high) and every stair ends on a landing; {@link TempleWalk} checks that.
 * Blocks: Sift ids, "air" or vanilla states "minecraft:...". Positions not in the plan keep the terrain (below the
 * court) or are cleared by the builder (above it). Weathering (missing blocks, breaches, cobbled and overgrown bricks,
 * sculk) comes from the seed; walk surfaces, stairs and the markers are never weathered.
 */
public final class TempleLayout {

    public record Pos(int x, int y, int z) {
    }

    /** Inclusive box. */
    public record Box(Pos min, Pos max) {

        public boolean contains(int x, int y, int z) {
            return x >= min.x && x <= max.x && y >= min.y && y <= max.y && z >= min.z && z <= max.z;
        }
    }

    /**
     * Size of a temple (config phase4.temples.size): radius = half side of the court (min 40), wall and corner tower
     * height above the court, depth of the trial level below it (16 to 24).
     */
    public record Params(int radius, int wallHeight, int towerHeight, int depth) {

        public static final Params DEFAULT = new Params(40, 14, 26, 16);

        public Params {
            radius = Math.max(40, Math.min(64, radius));
            // the stairs up to the wall walk need wall height + 2 blocks between the wall tower and the corner tower
            wallHeight = Math.max(10, Math.min(Math.min(24, radius - 19), wallHeight));
            towerHeight = Math.max(wallHeight + 6, Math.min(48, towerHeight));
            depth = Math.max(16, Math.min(24, depth));
        }

        /** Half side of the whole footprint (towers, buttresses and banners stand out of the court). */
        public int extent() {
            return radius + 5;
        }

        /** Highest y of the plan (pennants). */
        public int top() {
            return towerHeight + 6;
        }

        /** Lowest y of the plan (floor of the trial level). */
        public int bottom() {
            return -depth - 1;
        }
    }

    /**
     * @param blocks   position -> block (later entries of the plan already replaced earlier ones)
     * @param altar    the block players right-click to wake the boss (alpha.42)
     * @param chambers "wither" and "warden" -> their inner, empty space
     */
    public record Layout(Map<Pos, String> blocks, Pos altar, Pos enchanter, List<Pos> chests, Map<String, Box> chambers,
                         Params params, List<Spawner> spawners) {
    }

    /**
     * A brood spawner (alpha.42a): zone 1 = court (barracks, light), 2 = chambers of the trial level, 3 = inner ring next to
     * the arena (strongest). The block is a vanilla trial spawner without spawn data (vanilla leaves it alone); the plugin
     * runs it.
     */
    public record Spawner(Pos pos, int zone) {
    }

    // materials
    static final String BRICKS = "siftslate_bricks";
    static final String CHISELED = "chiseled_siftslate";
    static final String REINFORCED = "reinforced_siftslate";
    static final String COBBLED = "cobbled_siftslate";
    static final String GROWTH = "siftslate_growth";
    static final String SOUL = "soul_block";
    static final String SIFTITE = "siftite_block";
    static final String CHAROITE = "charoite_block";
    static final String SCULK = "healthy_sculk";
    static final String ICHOR_GLASS = "ichor_glass";
    static final String ENCHANTER = "sculk_enchanter";
    static final String AIR = "air";
    static final String FLOOR = "minecraft:polished_deepslate";
    static final String GRID = "minecraft:deepslate_tiles";
    static final String CRACKED = "minecraft:cracked_deepslate_tiles";
    static final String SLAB = "minecraft:deepslate_brick_slab[type=bottom]";
    static final String WALL = "minecraft:deepslate_brick_wall";
    static final String BARS = "minecraft:iron_bars";
    static final String CHAIN = "minecraft:iron_chain[axis=y]";
    static final String LANTERN = "minecraft:soul_lantern[hanging=true]";
    static final String CHEST = "minecraft:chest[facing=north]";
    static final String SOUL_SOIL = "minecraft:soul_soil";
    static final String SOUL_FIRE = "minecraft:soul_fire";
    /** Radius of the arena shaft around the altar (blocks). */
    public static final int ARENA_RADIUS = Plan.SHAFT;
    /** Height of the keep's floor above the court; the arena shaft opens at its top. */
    public static final int KEEP_TOP = Plan.KEEP;

    /** Whether the arena floor at (x, z) from the altar is one of the soul blocks of its ring. */
    static boolean soulRingCell(int x, int z) {
        double d = Math.hypot(x, z);
        return d >= 7.5 && d < 8.4 && Math.floorMod(x + z, 2) == 0;
    }

    /** Offsets (x, z) from the altar of the soul blocks in the arena floor. */
    public static List<int[]> soulRing() {
        List<int[]> out = new java.util.ArrayList<>();
        for (int x = -Plan.SHAFT; x <= Plan.SHAFT; x++) {
            for (int z = -Plan.SHAFT; z <= Plan.SHAFT; z++) {
                if (soulRingCell(x, z)) {
                    out.add(new int[] {x, z});
                }
            }
        }
        return out;
    }

    public static final String SPAWNER = "minecraft:trial_spawner[trial_spawner_state=inactive,ominous=true]";

    private static String stairs(String facing) {
        return "minecraft:deepslate_brick_stairs[facing=" + facing + "]";
    }

    /** Sift ids the temple is built from (protected until the boss is defeated). */
    public static final List<String> PALETTE = List.of(BRICKS, CHISELED, REINFORCED, COBBLED, GROWTH, SOUL, ENCHANTER, SIFTITE,
            CHAROITE, SCULK, ICHOR_GLASS);
    /** Vanilla materials of the temple (protected like the palette inside the temple). */
    public static final List<String> VANILLA = List.of("polished_deepslate", "deepslate_tiles", "cracked_deepslate_tiles", "deepslate_brick_slab",
            "deepslate_brick_wall", "deepslate_brick_stairs", "iron_bars", "iron_chain", "soul_lantern", "chest", "trial_spawner");

    private static final Set<String> NOT_WEATHERED = Set.of(ENCHANTER, CHEST, SOUL, CHAROITE, SIFTITE, REINFORCED, CHAIN, LANTERN,
            BARS, SOUL_SOIL, SOUL_FIRE, ICHOR_GLASS, SPAWNER);

    private TempleLayout() {
    }

    public static Layout build(long seed) {
        return build(seed, Params.DEFAULT);
    }

    public static Layout build(long seed, Params params) {
        return new Plan(seed, params).build(true);
    }

    /**
     * The plan before it is weathered (alpha.42e): the ways as designed. The walk check measures widths on it, so a breach
     * in a ruined wall is not reported as a narrow way.
     */
    public static Layout unweathered(long seed, Params params) {
        return new Plan(seed, params).build(false);
    }

    /** The plan being drawn; later writes replace earlier ones. */
    private static final class Plan {

        final Map<Pos, String> b = new LinkedHashMap<>();
        final Set<Pos> keep = new HashSet<>();
        final SplittableRandom rnd;
        final Params p;
        final int r;
        final int ug;
        static final int KEEP = 5;
        static final int SHAFT = 11;

        Plan(long seed, Params params) {
            this.rnd = new SplittableRandom(seed);
            this.p = params;
            this.r = params.radius();
            this.ug = -params.depth();
        }

        void set(int x, int y, int z, String v, boolean keepIt) {
            Pos pos = new Pos(x, y, z);
            b.put(pos, v);
            if (keepIt) {
                keep.add(pos);
            } else {
                keep.remove(pos);
            }
        }

        void set(int x, int y, int z, String v) {
            set(x, y, z, v, false);
        }

        String get(int x, int y, int z) {
            return b.get(new Pos(x, y, z));
        }

        void box(int x1, int y1, int z1, int x2, int y2, int z2, String v, boolean keepIt) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                        set(x, y, z, v, keepIt);
                    }
                }
            }
        }

        void carve(int x1, int y1, int z1, int x2, int y2, int z2) {
            box(x1, y1, z1, x2, y2, z2, AIR, false);
        }

        /** A room: shell of wall, air inside, floor at y1; openings already carved into the shell stay open. */
        void hollow(int x1, int y1, int z1, int x2, int y2, int z2, String wall, String floor) {
            for (int x = x1; x <= x2; x++) {
                for (int y = y1; y <= y2; y++) {
                    for (int z = z1; z <= z2; z++) {
                        boolean side = x == x1 || x == x2 || z == z1 || z == z2;
                        if (side || y == y1 || y == y2) {
                            if (AIR.equals(get(x, y, z))) {
                                continue;
                            }
                            set(x, y, z, y == y1 && !side ? floor : wall, y == y1);
                        } else {
                            set(x, y, z, AIR);
                        }
                    }
                }
            }
        }

        void crenel(int x, int y, int z, int i) {
            if (Math.floorMod(i, 2) == 0) {
                set(x, y, z, BRICKS);
            }
        }

        /**
         * Vanilla deepslate on purpose: the Sift's plant features grow on every mushroom-stem state (all Sift masonry), and
         * neighbour chunks decorated after a temple part would sow them over the court.
         */
        String courtFloor() {
            double v = rnd.nextDouble();
            return v < 0.12 ? CRACKED : v < 0.45 ? GRID : FLOOR;
        }

        // ------------------------------------------------------------------------------------------- above

        Layout build(boolean weathered) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    set(x, -1, z, courtFloor(), true);
                }
            }
            curtainWall();
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    roundTower(sx * (r - 4), sz * (r - 4), 6, p.towerHeight());
                }
            }
            roundTower(0, -(r - 2), 5, p.towerHeight() - 6);
            roundTower(r - 2, 0, 5, p.towerHeight() - 6);
            roundTower(-(r - 2), 0, 5, p.towerHeight() - 6);
            for (int[] banner : new int[][] {{0, -r - 4}, {r + 4, 0}, {-r - 4, 0}}) {
                for (int y = 8; y <= Math.min(16, p.towerHeight() - 10); y++) {
                    set(banner[0], y, banner[1], y % 2 == 1 ? CHAROITE : SIFTITE);
                }
            }
            wallStairs();
            gatehouse();
            chasmAndBridges();
            raisedKeep();
            for (int[] h : new int[][] {{-33, -33, -25, -26}, {25, -33, 33, -26}, {-12, -33, 12, -26}}) {
                hollow(h[0], -1, h[1], h[2], 6, h[3], BRICKS, FLOOR);
                int mid = (h[0] + h[2]) / 2;
                carve(mid - 1, 0, h[3], mid + 1, 2, h[3]);
                carve(mid - 1, 0, h[1], mid + 1, 2, h[1]);
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                box(sx * 4, 0, -34, sx * 4, 2, -34, BRICKS, false); // closes the pocket between the north tower and the barracks
            }
            for (int i = 0; i < 160; i++) {
                int x = rnd.nextInt(-r, r + 1);
                int z = rnd.nextInt(-r, r + 1);
                for (int y = 0; y < 4; y++) {
                    String v = get(x, y, z);
                    if (BRICKS.equals(v)) {
                        set(x, y, z, SCULK, keep.contains(new Pos(x, y, z)));
                    }
                }
            }
            // ---- below (alpha.42e, layout version 4: every way 3 wide and at least 3 high, see TempleWalk)
            Pos altar = shaft();
            ringAndSpokes();
            arenaGates();
            openCorridor(0, -(r - 6), 0, -16);
            openCorridor(0, 16, 0, 20);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    // diagonal spokes from the ring into the pillar chambers, 5 x 5 so they are 3 wide across
                    for (int i = 13; i < 22; i++) {
                        int x0 = sx * i;
                        int z0 = sz * i;
                        carve(x0 - 2, ug, z0 - 2, x0 + 2, ug + 4, z0 + 2);
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 2; dz++) {
                                set(x0 + dx, ug - 1, z0 + dz, FLOOR, true);
                            }
                        }
                    }
                    chamber(sx * 25 - 4, sz * 25 - 4, sx * 25 + 4, sz * 25 + 4);
                }
            }
            hollow(-8, ug - 1, -r, 8, ug + 8, -r + 7, BRICKS, FLOOR);
            Map<String, Box> chambers = new LinkedHashMap<>();
            chambers.put("wither", sealedRoom(19, -8, r - 3, 8, true));
            chambers.put("warden", sealedRoom(-(r - 3), -8, -19, 8, false));
            openCorridor(16, 0, 18, 0);
            openCorridor(-18, 0, -16, 0);
            Pos[] hall = treasureHall(-9, 19, 9, r - 3);
            List<Spawner> spawners = spawners();
            // stairways last: from the court down to a corridor that leads on into each pillar chamber
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    descent(sx, sz);
                }
            }
            if (weathered) {
                weather();
            }
            for (Pos marker : List.of(altar, hall[0], hall[1], hall[2])) {
                if (b.get(marker) == null) {
                    throw new IllegalStateException("marker lost: " + marker);
                }
            }
            return new Layout(b, altar, hall[0], List.of(hall[1], hall[2]), chambers, p, spawners);
        }

        void curtainWall() {
            int wall = p.wallHeight();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    int m = Math.max(Math.abs(x), Math.abs(z));
                    if (m < r - 3 || (z >= r - 3 && Math.abs(x) <= 7)) {
                        continue;
                    }
                    for (int y = 0; y < wall; y++) {
                        set(x, y, z, y % 5 == 0 ? CHISELED : BRICKS);
                    }
                    if (m == r) {
                        crenel(x, wall, z, x + z);
                    }
                    if (m >= r - 3 && m <= r - 1) {
                        set(x, wall - 1, z, FLOOR, true); // wall walk, 3 wide (alpha.42e)
                    }
                }
            }
            for (int k = -r + 8; k < r - 7; k += 10) {
                int h = wall - 3 - Math.floorMod(k, 3);
                for (int[] c : new int[][] {{k, -r - 1}, {k, r + 1}, {-r - 1, k}, {r + 1, k}}) {
                    if (Math.abs(c[0]) <= 8 && c[1] > 0) {
                        continue;
                    }
                    box(c[0], 0, c[1], c[0], h - 1, c[1], BRICKS, false);
                    set(c[0], h, c[1], SLAB);
                }
            }
        }

        /** Hollow round tower, floors every 7 (one level with the wall walk), slits, crenels, pennant, doors. */
        void roundTower(int cx, int cz, int radius, int h) {
            int walk = p.wallHeight() - 1;
            for (int x = cx - radius - 1; x <= cx + radius + 1; x++) {
                for (int z = cz - radius - 1; z <= cz + radius + 1; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > radius + 0.5) {
                        continue;
                    }
                    boolean shell = d > radius - 0.7;
                    for (int y = 0; y < h; y++) {
                        // a floor every 7 blocks, one level with the wall walk; none so low the room below is under 3 high
                        boolean level = Math.floorMod(y - walk, 7) == 0 && y >= 3;
                        if (shell) {
                            boolean slit = Math.floorMod(y - walk, 7) == 4 && (x == cx || z == cz);
                            set(x, y, z, slit ? AIR : level ? CHISELED : BRICKS);
                        } else {
                            set(x, y, z, level ? FLOOR : AIR, level);
                        }
                    }
                    if (shell) {
                        crenel(x, h, z, x + z);
                        int m = Math.max(Math.abs(x), Math.abs(z));
                        // door from the wall walk, 3 wide and 3 high
                        if (m >= r - 3 && m <= r - 1 && h > walk + 4) {
                            carve(x, walk + 1, z, x, walk + 3, z);
                        }
                        // door from the court (on the side facing the centre), 3 wide, moved one block towards the
                        // centre so the curtain wall does not stand in front of it (alpha.42e)
                        int dcx = cx - Integer.signum(cx);
                        int dcz = cz - Integer.signum(cz);
                        if (Math.abs(x) + Math.abs(z) < Math.abs(cx) + Math.abs(cz) - radius + 1.5
                                && (Math.abs(x - dcx) <= 1 || Math.abs(z - dcz) <= 1)) {
                            carve(x, 0, z, x, 2, z);
                        }
                    }
                }
            }
            box(cx, h, cz, cx, h + 4, cz, CHAIN, false);
            set(cx, h + 5, cz, CHAROITE);
        }

        /**
         * Stairs from the court up to the wall walk along the inner face of the east and west walls (alpha.42e): 3 wide,
         * 5 free above every step, a 3 x 3 landing at the top level with the walk beside it.
         */
        void wallStairs() {
            int walk = p.wallHeight() - 1;
            for (int sx = -1; sx <= 1; sx += 2) {
                int xc = sx * (r - 5);
                int z0 = 7; // clear of the wall tower in the middle of the wall
                for (int i = 0; i < walk; i++) {
                    int z = z0 + i;
                    for (int x = xc - 1; x <= xc + 1; x++) {
                        box(x, 0, z, x, i - 1, z, BRICKS, false);
                        set(x, i, z, stairs("south"), true);
                        carve(x, i + 1, z, x, i + 5, z);
                    }
                }
                for (int z = z0 + walk; z < z0 + walk + 3; z++) {
                    for (int x = xc - 1; x <= xc + 1; x++) {
                        box(x, 0, z, x, walk - 1, z, BRICKS, false);
                        set(x, walk, z, FLOOR, true);
                        carve(x, walk + 1, z, x, walk + 3, z);
                    }
                }
            }
        }

        void gatehouse() {
            int wall = p.wallHeight();
            for (int sx = -1; sx <= 1; sx += 2) {
                int x1 = Math.min(sx * 8, sx * 14);
                int x2 = Math.max(sx * 8, sx * 14);
                box(x1, 0, r - 6, x2, wall + 6, r, BRICKS, false);
                carve(x1 + 1, 0, r - 5, x2 - 1, wall + 5, r - 1);
                for (int y = 5; y < wall + 6; y += 6) {
                    box(x1 + 1, y, r - 5, x2 - 1, y, r - 1, FLOOR, true);
                }
                for (int x = x1; x <= x2; x++) {
                    crenel(x, wall + 7, r - 6, x);
                    crenel(x, wall + 7, r, x);
                }
                carve((x1 + x2) / 2 - 1, 0, r - 6, (x1 + x2) / 2 + 1, 2, r - 6); // door from the court, 3 wide
            }
            box(-7, 0, r - 3, 7, wall + 2, r, BRICKS, false);
            carve(-3, 0, r - 3, 3, 7, r);
            for (int x = -4; x <= 4; x++) {
                set(x, 8, r, CHISELED);
            }
            for (int x = -3; x <= 3; x++) {
                for (int y = 5; y < 8; y++) {
                    set(x, y, r - 1, BARS); // half-raised portcullis
                }
            }
            for (int x = -7; x <= 7; x++) {
                crenel(x, wall + 3, r, x);
                if (Math.abs(x) <= 2) {
                    set(x, wall, r + 1, x == 0 ? CHAROITE : SIFTITE);
                }
            }
            for (int x = -3; x <= 3; x++) {
                for (int z = r - 3; z <= r; z++) {
                    set(x, -1, z, FLOOR, true);
                }
            }
        }

        /** Square chasm 17..22 with soul fire, four bridges climbing to the raised keep. */
        void chasmAndBridges() {
            for (int x = -23; x <= 23; x++) {
                for (int z = -23; z <= 23; z++) {
                    int m = Math.max(Math.abs(x), Math.abs(z));
                    int across = Math.min(Math.abs(x), Math.abs(z));
                    if (m >= 17 && m <= 22) {
                        box(x, -9, z, x, -1, z, AIR, false);
                        set(x, -10, z, SOUL_SOIL);
                        if (Math.floorMod(x * 7 + z * 3, 5) == 0) {
                            set(x, -9, z, SOUL_FIRE);
                        }
                        if (across <= 2) {
                            int y = 21 - m; // -1 at the outer edge ... 4 at the keep
                            String facing = Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
                            set(x, y, z, across == 2 ? BRICKS : y > -1 ? stairs(facing) : FLOOR, true);
                            box(x, y + 1, z, x, y + 4, z, AIR, false);
                            if (across == 2) {
                                set(x, y + 1, z, WALL);
                            }
                            if (m == 17 || m == 22) {
                                box(x, -9, z, x, y - 1, z, BRICKS, false);
                            }
                        }
                    }
                    if (m == 16 || m == 23) {
                        box(x, -10, z, x, -2, z, BRICKS, false);
                    }
                }
            }
        }

        void raisedKeep() {
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    int m = Math.max(Math.abs(x), Math.abs(z));
                    if (Math.hypot(x, z) <= SHAFT + 1.6) {
                        continue;
                    }
                    box(x, 0, z, x, KEEP - 1, z, m < 16 ? BRICKS : CHISELED, false);
                    set(x, KEEP, z, FLOOR, true);
                    if (m == 16 && Math.min(Math.abs(x), Math.abs(z)) > 2) {
                        set(x, KEEP + 1, z, WALL);
                    }
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int ox = sx * 14;
                    int oz = sz * 14;
                    box(ox, KEEP + 1, oz, ox, KEEP + 8, oz, CHISELED, false);
                    set(ox, KEEP + 9, oz, CHAROITE);
                    set(ox, KEEP + 10, oz, "minecraft:soul_lantern[hanging=false]");
                }
            }
        }

        /**
         * Stairs from the court (y -1) down to the trial level (alpha.42e): along z at x = +-31, 3 wide, 5 free above
         * every step (a 3 x 3 body on a stair rests on the step behind it, so it needs 2 more than its height). At the foot the way goes straight on into a corridor and through a door into the pillar chamber of
         * the quadrant, so no stair ends at a wall.
         */
        void descent(int sx, int sz) {
            int x0 = sx * 31; // beside the pillar chambers (21..29), whatever the size
            int top = sz * 12;
            int length = p.depth();
            String facing = sz > 0 ? "north" : "south"; // they rise back towards the court
            for (int i = 0; i <= length; i++) {
                int z = top + sz * i;
                int y = -1 - i;
                for (int x = x0 - 2; x <= x0 + 2; x++) {
                    boolean edge = Math.abs(x - x0) == 2;
                    for (int yy = y; yy <= y + 6; yy++) {
                        if (yy >= 0) {
                            // open to the court at the top: a railing instead of side walls, no roof
                            if (edge && yy == 0) {
                                set(x, yy, z, WALL, true);
                            } else if (!edge) {
                                set(x, yy, z, AIR);
                            }
                        } else if (edge || yy == y + 6) {
                            if (!AIR.equals(get(x, yy, z))) {
                                set(x, yy, z, BRICKS);
                            }
                        } else {
                            set(x, yy, z, AIR);
                        }
                    }
                    if (!edge) {
                        set(x, y, z, i < length ? stairs(facing) : FLOOR, true);
                    }
                }
            }
            int foot = top + sz * length;
            // deeper levels: the stairs are longer, the turn comes after the last step (its corridor is 3 wide around outer)
            int outer = sz * Math.max(31, Math.abs(foot) + 1);
            openCorridor(x0, foot, x0, outer);
            openCorridor(x0 + sx, outer, sx * 24, outer); // past the stair corridor so the corner is 3 x 3
            openCorridor(sx * 25, outer, sx * 25, sz * 30);
            int wall = sz * 29; // outer wall of the pillar chamber
            for (int x = Math.min(sx * 24, sx * 26); x <= Math.max(sx * 24, sx * 26); x++) {
                carve(x, ug, wall, x, ug + 3, wall);
                set(x, ug - 1, wall, FLOOR, true);
            }
        }

        // ------------------------------------------------------------------------------------------- below

        /** Open arena shaft from the trial level up to the keep top; the altar in the middle. */
        Pos shaft() {
            for (int x = -SHAFT - 2; x <= SHAFT + 2; x++) {
                for (int z = -SHAFT - 2; z <= SHAFT + 2; z++) {
                    double d = Math.hypot(x, z);
                    if (d <= SHAFT + 0.5) {
                        box(x, ug, z, x, KEEP, z, AIR, false);
                        String floor = soulRingCell(x, z) ? SOUL
                                : d > SHAFT - 0.7 ? CHISELED : FLOOR;
                        set(x, ug - 1, z, floor, true);
                    } else if (d <= SHAFT + 1.6) {
                        for (int y = ug - 1; y <= KEEP; y++) {
                            String v = y < ug + 4 ? REINFORCED : BRICKS;
                            if (Math.floorMod(y - ug, 6) == 5) {
                                v = CHISELED;
                            }
                            set(x, y, z, v);
                        }
                    }
                }
            }
            box(-1, ug, -1, 1, ug, 1, REINFORCED, true);
            set(0, ug + 1, 0, CHISELED, true);
            Pos altar = new Pos(0, ug + 2, 0);
            set(altar.x(), altar.y(), altar.z(), SOUL, true);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = (int) (sx * SHAFT * 0.62);
                    int z = (int) (sz * SHAFT * 0.62);
                    box(x, ug + 6, z, x, KEEP, z, CHAIN, false);
                    set(x, ug + 5, z, LANTERN);
                }
            }
            return altar;
        }

        /** Four gates from the arena through the shaft wall and the ring's inner wall into the ring corridor. */
        void arenaGates() {
            int[][] axes = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] a : axes) {
                for (int k = SHAFT - 1; k <= 15; k++) {
                    for (int w = -1; w <= 1; w++) {
                        int x = a[0] * k + (a[1] != 0 ? w : 0);
                        int z = a[1] * k + (a[0] != 0 ? w : 0);
                        carve(x, ug, z, x, ug + 3, z);
                        if (k > SHAFT + 1) {
                            set(x, ug - 1, z, FLOOR, true);
                        }
                    }
                }
            }
        }

        void ringAndSpokes() {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    double d = Math.hypot(x, z);
                    // about 4.6 wide: a 3 x 3 body also fits where the ring runs diagonally (alpha.42e)
                    if (d >= 14.0 && d <= 18.6) {
                        carve(x, ug, z, x, ug + 4, z);
                        set(x, ug - 1, z, Math.floorMod(x + z, 4) == 0 ? GRID : FLOOR, true);
                        set(x, ug + 5, z, BRICKS);
                    } else if ((d >= 13.0 && d < 14.0) || (d > 18.6 && d <= 19.6)) {
                        for (int y = ug - 1; y <= ug + 5; y++) {
                            if (!AIR.equals(get(x, y, z))) {
                                set(x, y, z, BRICKS);
                            }
                        }
                    }
                }
            }
        }

        /** Axis-aligned corridor without own walls (the rock is the wall), 3 wide, 5 high, tiled floor. */
        void openCorridor(int x1, int z1, int x2, int z2) {
            if (z1 == z2) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    for (int z = z1 - 1; z <= z1 + 1; z++) {
                        carve(x, ug, z, x, ug + 4, z);
                        set(x, ug - 1, z, Math.floorMod(x + z, 4) == 0 ? GRID : FLOOR, true);
                    }
                }
            } else {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    for (int x = x1 - 1; x <= x1 + 1; x++) {
                        carve(x, ug, z, x, ug + 4, z);
                        set(x, ug - 1, z, Math.floorMod(x + z, 4) == 0 ? GRID : FLOOR, true);
                    }
                }
            }
        }

        /** Pillar chamber with a grid floor and ichor glass lights in the ceiling. */
        void chamber(int x1, int z1, int x2, int z2) {
            int h = 7;
            hollow(x1, ug - 1, z1, x2, ug + h, z2, BRICKS, FLOOR);
            for (int x = x1 + 1; x < x2; x++) {
                for (int z = z1 + 1; z < z2; z++) {
                    if ((x - x1) % 4 == 0 && (z - z1) % 4 == 0) {
                        box(x, ug, z, x, ug + h - 1, z, CHISELED, false);
                    } else if ((x - x1) % 4 == 0 || (z - z1) % 4 == 0) {
                        set(x, ug - 1, z, GRID, true);
                    }
                }
            }
            for (int x = x1 + 2; x < x2 - 1; x += 4) {
                for (int z = z1 + 2; z < z2 - 1; z += 4) {
                    set(x, ug + h, z, ICHOR_GLASS);
                }
            }
        }

        /** Empty trial room: reinforced shell, charoite seal on the side facing the centre (alpha.42 / option B). */
        Box sealedRoom(int x1, int z1, int x2, int z2, boolean sealWest) {
            int h = 9;
            box(x1, ug - 1, z1, x2, ug + h, z2, REINFORCED, true);
            carve(x1 + 1, ug, z1 + 1, x2 - 1, ug + h - 1, z2 - 1);
            int fx = sealWest ? x1 : x2;
            int cz = (z1 + z2) / 2;
            for (int y = ug; y < ug + 5; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    set(fx, y, z, Math.floorMod(y - ug + z - cz, 2) == 0 ? CHAROITE : CHISELED, true);
                }
            }
            return new Box(new Pos(x1 + 1, ug, z1 + 1), new Pos(x2 - 1, ug + h - 1, z2 - 1));
        }

        /** The treasure hall: siftite pillars with charoite capitals, dais with the enchanter, two chests. */
        Pos[] treasureHall(int x1, int z1, int x2, int z2) {
            int h = 9;
            hollow(x1, ug - 1, z1, x2, ug + h, z2, REINFORCED, CHISELED);
            int cx = (x1 + x2) / 2;
            for (int z = z1 + 4; z < z2 - 3; z += 4) { // no pockets under 3 x 3 in the corners (alpha.42e)
                for (int x : new int[] {x1 + 2, x2 - 2}) {
                    box(x, ug, z, x, ug + h - 1, z, SIFTITE, false);
                    set(x, ug + h - 1, z, CHAROITE);
                }
            }
            box(cx - 2, ug, z2 - 5, cx + 2, ug, z2 - 3, CHISELED, true);
            Pos enchanter = new Pos(cx, ug + 1, z2 - 4);
            set(enchanter.x(), enchanter.y(), enchanter.z(), ENCHANTER, true);
            Pos chestA = new Pos(cx - 4, ug, z2 - 2);
            Pos chestB = new Pos(cx + 4, ug, z2 - 2);
            set(chestA.x(), chestA.y(), chestA.z(), CHEST, true);
            set(chestB.x(), chestB.y(), chestB.z(), CHEST, true);
            for (int x : new int[] {cx - 3, cx + 3}) {
                set(x, ug + h - 2, z2 - 4, CHAIN);
                set(x, ug + h - 3, z2 - 4, LANTERN);
            }
            return new Pos[] {enchanter, chestA, chestB};
        }

        /** Brood spawners: 3 in the barracks, 4 in the pillar chambers and 1 in the north chamber, 4 in the inner ring. */
        List<Spawner> spawners() {
            List<Spawner> out = new java.util.ArrayList<>();
            for (int[] c : new int[][] {{-29, -30}, {29, -30}, {-6, -29}}) { // the middle one beside the way through
                out.add(new Spawner(new Pos(c[0], 0, c[1]), 1));
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    // in the middle of the pillar chamber (alpha.42e): the corner where the spoke comes in stays free
                    out.add(new Spawner(new Pos(sx * 25, ug, sz * 25), 2));
                }
            }
            out.add(new Spawner(new Pos(0, ug, -(r - 4)), 2));
            for (int[] c : new int[][] {{15, 6}, {-15, -6}, {6, -15}, {-6, 15}}) {
                out.add(new Spawner(new Pos(c[0], ug, c[1]), 3));
            }
            for (Spawner sp : out) {
                set(sp.pos().x(), sp.pos().y(), sp.pos().z(), SPAWNER, true);
                if (sp.zone() == 2 && sp.pos().x() != 0) {
                    set(sp.pos().x(), sp.pos().y() + 1, sp.pos().z(), CHAROITE, true); // the chamber's pillar stands on it
                } else {
                    set(sp.pos().x(), sp.pos().y() + 1, sp.pos().z(), AIR, false);
                }
            }
            return List.copyOf(out);
        }

        // ------------------------------------------------------------------------------------------- ruin

        void weather() {
            double top = p.top();
            for (Map.Entry<Pos, String> e : b.entrySet()) {
                Pos pos = e.getKey();
                String v = e.getValue();
                if (AIR.equals(v) || keep.contains(pos) || NOT_WEATHERED.contains(v)) {
                    continue;
                }
                double height = Math.max(0, pos.y()) / top;
                double roll = rnd.nextDouble();
                if (pos.y() >= 2 && roll < 0.04 + 0.22 * height) {
                    e.setValue(AIR);
                } else if (roll < 0.12 && (BRICKS.equals(v) || CHISELED.equals(v) || FLOOR.equals(v))) {
                    e.setValue(COBBLED);
                } else if (roll < 0.17 && BRICKS.equals(v)) {
                    e.setValue(GROWTH);
                }
            }
            // a few breaches in walls and towers
            for (int i = 0; i < 7; i++) {
                int cx = rnd.nextInt(-r, r + 1);
                int cz = rnd.nextInt(-r, r + 1);
                int cy = rnd.nextInt(5, Math.min(13, p.wallHeight()));
                for (int x = cx - 4; x <= cx + 4; x++) {
                    for (int y = cy - 3; y <= cy + 3; y++) {
                        for (int z = cz - 4; z <= cz + 4; z++) {
                            Pos pos = new Pos(x, y, z);
                            String v = b.get(pos);
                            double dy = (y - cy) * 1.4;
                            if (v != null && y > 1 && !keep.contains(pos) && !NOT_WEATHERED.contains(v)
                                    && (x - cx) * (x - cx) + dy * dy + (z - cz) * (z - cz) < 10) {
                                b.put(pos, AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Columns that stand on the ground (a solid block of the plan at y -1 or 0): the builder fills a foundation below
     * them down to the terrain, so nothing floats. Banners and other parts higher up hang on the towers.
     */
    /**
     * The brood spawners of layout version 3 (alpha.42a to 42d): in the pillar chambers at the corner the spoke comes in
     * and in the middle barracks on the way through. Temples built then keep them until /sift temple rebuild.
     */
    public static List<Spawner> spawnersOfVersion3(Params p) {
        int ug = -p.depth();
        List<Spawner> out = new java.util.ArrayList<>();
        for (int[] c : new int[][] {{-29, -30}, {29, -30}, {0, -30}}) {
            out.add(new Spawner(new Pos(c[0], 0, c[1]), 1));
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                out.add(new Spawner(new Pos(sx * 23, ug, sz * 23), 2));
            }
        }
        out.add(new Spawner(new Pos(0, ug, -(p.radius() - 4)), 2));
        for (int[] c : new int[][] {{15, 6}, {-15, -6}, {6, -15}, {-6, 15}}) {
            out.add(new Spawner(new Pos(c[0], ug, c[1]), 3));
        }
        return List.copyOf(out);
    }

    public static Set<Long> standingColumns(Layout layout) {
        Set<Long> columns = new HashSet<>();
        for (Map.Entry<Pos, String> e : layout.blocks().entrySet()) {
            Pos pos = e.getKey();
            if ((pos.y() == -1 || pos.y() == 0) && !AIR.equals(e.getValue())) {
                columns.add(column(pos.x(), pos.z()));
            }
        }
        return columns;
    }

    public static long column(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }
}
