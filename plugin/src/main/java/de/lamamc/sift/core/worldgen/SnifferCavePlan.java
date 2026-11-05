package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sniffer caves (alpha.28), a port of SnifferCaveFeature of the original 1.1.1: from the chunk centre a
 * winding tunnel of 14–16 blocks leads 9 blocks down into a nest chamber lined with healthy and dry sculk,
 * with a side chamber holding an ichor basin, torchflowers or pitcher plants on the floor, a nest of
 * willow foliage with plants and sometimes a sniffer egg, and a sniffer family (two adults, a third of the
 * time a baby) that the plugin spawns later on the main thread. Also the sniffer plant patch
 * (SnifferPlantPatchFeature) on the surface.
 */
public final class SnifferCavePlan {

    public static final String AIR = "air";
    public static final String ICHOR = "ichor";
    public static final String HEALTHY = "healthy_sculk";
    public static final String DRY = "dry_healthy_sculk";
    public static final String FOLIAGE = "overgrown_willow_foliage";
    public static final String TORCHFLOWER = "torchflower";
    public static final String PITCHER = "pitcher";
    public static final String EGG = "sniffer_egg";

    /** Cave cells of the original: overgrown 16 chunks with 4 candidates, wastes 28 chunks with 3. */
    public static final long OVERGROWN_SALT = 6002815919507592773L;
    public static final long WASTES_SALT = 6002815919508111699L;

    static final Set<String> TERRAIN = Set.of("siftslate", "siftslate_growth", "dry_healthy_sculk_growth", HEALTHY, DRY);
    static final Set<String> VEGETATION = Set.of("overgrown_fronds", "overgrown_chard", "overgrown_stalks", "siftslate_stalks",
            "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts", "siftslate_hanging_roots", "overgrown_hanging_roots");
    /** Blocks a cave must not cut (portal frames and consoles, willow trunks). */
    static final Set<String> PROTECTED = Set.of("reinforced_siftslate", "sift_portal", "sonorous_deepslate", "overgrown_willow_log",
            "overgrown_willow_wood");
    static final String[] NEST_PLANTS = {"overgrown_fronds", "overgrown_chard", "overgrown_stalks", "siftslate_stalks",
            "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts"};
    /** NORTH, EAST, SOUTH, WEST; clockwise is the next one. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** The blocks a cave reads and writes; Sift ids, {@link #AIR}, anything else under its vanilla key. */
    public interface World {
        /** First free y above WORLD_SURFACE_WG. */
        int height(int x, int z);

        String block(int x, int y, int z);

        /** The chunk and its neighbours minus one block. */
        boolean contains(int x, int z);

        boolean writable(int x, int y, int z);

        /** A Sift id, {@link #AIR} or {@link #ICHOR}. */
        void set(int x, int y, int z, String block);

        /**
         * Places a plant if it can stand there: {@link #TORCHFLOWER}, {@link #PITCHER} (two blocks, {@code age}
         * 3 or 4), {@link #EGG} or a Sift plant. The original lets flowers and pitchers stand on Sift soil.
         */
        boolean plant(int x, int y, int z, String plant, int age);

        boolean nearPortal(int x, int z, int radius);

        int minY();
    }

    /** A built cave: entrance, nest centre, the nest's foliage blocks (sniffers stand on them), the ichor. */
    public record Result(String reason, int[] entrance, int[] nest, List<int[]> nestFloors, List<int[]> ichor, boolean baby,
                         int plants, int eggs, int blocks) {

        public boolean built() {
            return reason == null;
        }

        static Result rejected(String reason, int[] at) {
            return new Result(reason, at, at, List.of(), List.of(), false, 0, 0, 0);
        }
    }

    record PoolPlan(int[] chamberCenter, int outward, int variant, double outwardRadius, double verticalRadius, double crossRadius) {
    }

    record Basin(Set<Long> fluid, Set<Long> lining, Set<Long> clearance) {
    }

    private SnifferCavePlan() {
    }

    /** Is this chunk one of the {@code candidates} cave candidates of its cell? */
    public static boolean selected(long seed, int chunkX, int chunkZ, int cellChunks, long salt, int candidates) {
        return LandmarkPlacement.isSelectedChunk(seed, chunkX, chunkZ, cellChunks, salt, candidates);
    }

    public static Result place(World w, Random random, int chunkX, int chunkZ, int portalClearance) {
        int length = 14 + random.nextInt(3);
        int x0 = (chunkX << 4) + 8;
        int z0 = (chunkZ << 4) + 8;
        int surface = w.height(x0, z0);
        int[] entrance = {x0, surface, z0};
        if (surface <= w.minY() + 18 || !TERRAIN.contains(w.block(x0, surface - 1, z0))) {
            return Result.rejected("kein Sift-Boden am Eingang", entrance);
        }
        int direction = chooseDirection(w, entrance, length, random);
        if (direction < 0) {
            return Result.rejected("keine Richtung mit genug Deckung", entrance);
        }
        Set<Long> interior = new LinkedHashSet<>();
        Set<Long> shell = new LinkedHashSet<>();
        int sideways = (direction + 1) % 4;
        double phase = random.nextDouble() * Math.PI * 2.0;
        int[] last = entrance;
        for (int distance = 0; distance <= length; distance += 2) {
            double progress = (double) distance / length;
            int lateral = (int) Math.round(Math.sin(phase + progress * Math.PI * 1.7) * (0.6 + progress * 1.35));
            int lift = (int) Math.round(Math.sin(progress * Math.PI) * 0.9) - (int) Math.round(progress * 9.0);
            int[] center = {entrance[0] + HORIZONTAL[direction][0] * distance + HORIZONTAL[sideways][0] * lateral, entrance[1] + lift,
                    entrance[2] + HORIZONTAL[direction][1] * distance + HORIZONTAL[sideways][1] * lateral};
            double swell = Math.sin(progress * Math.PI);
            double radiusXz = 2.1 + swell * 1.65 + random.nextDouble() * 0.35;
            double radiusY = 1.9 + swell * 1.15 + random.nextDouble() * 0.25;
            ellipsoid(center, radiusXz, radiusY, radiusXz * 0.92, interior, shell);
            last = center;
        }
        int[] nest = {last[0] + HORIZONTAL[direction][0], last[1] + 1, last[2] + HORIZONTAL[direction][1]};
        ellipsoid(nest, 5.2, 3.7, 5.0, interior, shell);
        PoolPlan pool = poolPlan(nest, sideways, random);
        boolean alongX = HORIZONTAL[pool.outward()][0] != 0;
        ellipsoid(pool.chamberCenter(), alongX ? pool.outwardRadius() : pool.crossRadius(), pool.verticalRadius(),
                alongX ? pool.crossRadius() : pool.outwardRadius(), interior, shell);
        shell.removeAll(interior);
        Basin basin = basin(pool);
        Set<Long> writes = new LinkedHashSet<>(interior);
        writes.addAll(shell);
        writes.addAll(basin.fluid());
        writes.addAll(basin.lining());
        writes.addAll(basin.clearance());
        if (w.nearPortal(nest[0], nest[2], portalClearance)) {
            return Result.rejected("zu nah am Haupt-Portal", entrance);
        }
        if (!validMass(w, nest)) {
            return Result.rejected("zu wenig Fels um das Nest", entrance);
        }
        String carve = canCarveAll(w, writes);
        if (carve != null) {
            return Result.rejected(carve, entrance);
        }
        int blocks = 0;
        for (long p : shell) {
            int x = x(p), y = y(p), z = z(p);
            if (TERRAIN.contains(w.block(x, y, z)) && w.writable(x, y, z)) {
                float healthyChance = 0.18f + (distanceSquared(x, y, z, nest) < 90 ? 0.13f : 0.0f);
                w.set(x, y, z, random.nextFloat() < healthyChance ? HEALTHY : DRY);
                blocks++;
            }
        }
        for (long p : interior) {
            if (w.contains(x(p), z(p)) && w.writable(x(p), y(p), z(p))) {
                w.set(x(p), y(p), z(p), AIR);
                blocks++;
            }
        }
        for (long p : basin.clearance()) {
            w.set(x(p), y(p), z(p), AIR);
        }
        for (long p : basin.lining()) {
            w.set(x(p), y(p), z(p), random.nextFloat() < 0.55f ? HEALTHY : DRY);
        }
        List<int[]> ichor = new ArrayList<>();
        for (long p : basin.fluid()) {
            w.set(x(p), y(p), z(p), ICHOR);
            ichor.add(new int[] {x(p), y(p), z(p)});
        }
        int plants = scatterPlants(w, random, interior, nest);
        List<int[]> nestFloors = buildNest(w, random, nest);
        int eggs = 0;
        boolean baby = false;
        if (!nestFloors.isEmpty()) {
            eggs = random.nextFloat() < 0.5f ? 1 : 0;
            if (eggs == 1 && random.nextFloat() < 0.1f) {
                eggs++;
            }
            for (int i = 0; i < eggs; i++) {
                onFreeNestBlock(w, random, nestFloors, EGG);
            }
            int nestPlants = 4 + random.nextInt(5);
            for (int i = 0; i < nestPlants; i++) {
                if (onFreeNestBlock(w, random, nestFloors, NEST_PLANTS[random.nextInt(NEST_PLANTS.length)])) {
                    plants++;
                }
            }
            baby = random.nextFloat() < 0.33f;
        }
        return new Result(null, entrance, nest, nestFloors, ichor, baby, plants, eggs, blocks);
    }

    /** The direction (0–3) whose tunnel end lies deepest under the surface, at least 5; -1 if none. */
    static int chooseDirection(World w, int[] entrance, int length, Random random) {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int start = random.nextInt(4);
        for (int index = 0; index < 4; index++) {
            int d = (start + index) % 4;
            int endX = entrance[0] + HORIZONTAL[d][0] * (length + 1);
            int endY = entrance[1] - 8;
            int endZ = entrance[2] + HORIZONTAL[d][1] * (length + 1);
            if (w.contains(endX + 6, endZ + 6) && w.contains(endX - 6, endZ - 6)) {
                int cover = w.height(endX, endZ) - endY;
                if (cover >= 5) {
                    int score = cover + random.nextInt(4);
                    if (score > bestScore) { // Stream.max (BinaryOperator.maxBy) keeps the first of equal scores
                        bestScore = score;
                        best = d;
                    }
                }
            }
        }
        return best;
    }

    static void ellipsoid(int[] c, double radiusX, double radiusY, double radiusZ, Set<Long> interior, Set<Long> shell) {
        int maxX = (int) Math.ceil(radiusX + 1.0);
        int maxY = (int) Math.ceil(radiusY + 1.0);
        int maxZ = (int) Math.ceil(radiusZ + 1.0);
        for (int dx = -maxX; dx <= maxX; dx++) {
            for (int dy = -maxY; dy <= maxY; dy++) {
                for (int dz = -maxZ; dz <= maxZ; dz++) {
                    long pos = pack(c[0] + dx, c[1] + dy, c[2] + dz);
                    if (square(dx / radiusX) + square(dy / radiusY) + square(dz / radiusZ) <= 1.0) {
                        interior.add(pos);
                    } else if (square(dx / (radiusX + 1.0)) + square(dy / (radiusY + 1.0)) + square(dz / (radiusZ + 1.0)) <= 1.0) {
                        shell.add(pos);
                    }
                }
            }
        }
    }

    static PoolPlan poolPlan(int[] nest, int sideways, Random random) {
        int variant = random.nextInt(4);
        int outward = random.nextBoolean() ? sideways : (sideways + 2) % 4;
        double chamberOutward = switch (variant) {
            case 1 -> 4.4;
            case 2 -> 3.8;
            case 3 -> 4.1;
            default -> 4.0;
        };
        double chamberCross = switch (variant) {
            case 1 -> 2.7;
            case 2 -> 3.5;
            case 3 -> 3.1;
            default -> 3.0;
        };
        double chamberVertical = variant == 3 ? 3.0 : 2.7;
        int[] center = {nest[0] + HORIZONTAL[outward][0] * 5, nest[1] - 1, nest[2] + HORIZONTAL[outward][1] * 5};
        return new PoolPlan(center, outward, variant, chamberOutward, chamberVertical, chamberCross);
    }

    static Basin basin(PoolPlan plan) {
        int outwardRadius = plan.variant() == 1 || plan.variant() == 3 ? 3 : 2;
        int crossRadius = plan.variant() == 2 ? 3 : 2;
        int[] out = HORIZONTAL[plan.outward()];
        int[] cross = HORIZONTAL[(plan.outward() + 1) % 4];
        Set<Long> surface = new LinkedHashSet<>();
        Set<Long> fluid = new LinkedHashSet<>();
        Set<Long> lining = new LinkedHashSet<>();
        Set<Long> clearance = new LinkedHashSet<>();
        int[] c = plan.chamberCenter();
        for (int along = -outwardRadius; along <= outwardRadius; along++) {
            for (int across = -crossRadius; across <= crossRadius; across++) {
                double normalized = square(along / (outwardRadius + 0.35)) + square(across / (crossRadius + 0.35));
                double edgeWobble = Math.sin(along * 2.17 + across * 1.41 + plan.variant() * 0.83) * 0.11;
                if (normalized <= 1.0 + edgeWobble) {
                    long top = pack(c[0] + out[0] * along + cross[0] * across, c[1] - 3, c[2] + out[1] * along + cross[1] * across);
                    surface.add(top);
                    fluid.add(top);
                    if (plan.variant() == 3 && normalized < 0.55) {
                        fluid.add(offset(top, 0, -1, 0));
                    }
                }
            }
        }
        for (long wet : fluid) {
            lining.add(offset(wet, 0, -1, 0));
            for (int[] side : HORIZONTAL) {
                lining.add(offset(wet, side[0], 0, side[1]));
            }
        }
        lining.removeAll(fluid);
        for (long top : surface) {
            for (int dy = 1; dy <= 3; dy++) {
                clearance.add(offset(top, 0, dy, 0));
            }
            for (int[] side : HORIZONTAL) {
                long bank = offset(top, side[0], 0, side[1]);
                if (!surface.contains(bank)) {
                    lining.add(offset(bank, 0, -1, 0));
                    lining.add(offset(bank, 0, -2, 0));
                    clearance.add(offset(bank, 0, 1, 0));
                    clearance.add(offset(bank, 0, 2, 0));
                }
            }
        }
        lining.removeAll(fluid);
        return new Basin(fluid, lining, clearance);
    }

    /** Null if every block may be carved, else why not. */
    static String canCarveAll(World w, Set<Long> writes) {
        int siftMass = 0;
        for (long p : writes) {
            int x = x(p), y = y(p), z = z(p);
            if (!w.contains(x, z) || !w.writable(x, y, z)) {
                return "reicht über den Bereich hinaus";
            }
            String block = w.block(x, y, z);
            if (PROTECTED.contains(block)) {
                return "Portal oder Weidenstamm im Weg";
            } else if (TERRAIN.contains(block)) {
                siftMass++;
            } else if (!VEGETATION.contains(block) && !AIR.equals(block)) {
                return "fremder Block im Weg (" + block + ")";
            }
        }
        return siftMass >= writes.size() * 0.4 ? null : "zu wenig Fels";
    }

    static boolean validMass(World w, int[] c) {
        int solid = 0;
        int total = 0;
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dy = -3; dy <= 3; dy += 2) {
                for (int dz = -5; dz <= 5; dz += 2) {
                    total++;
                    if (!w.contains(c[0] + dx, c[2] + dz)) {
                        return false;
                    }
                    if (TERRAIN.contains(w.block(c[0] + dx, c[1] + dy, c[2] + dz))) {
                        solid++;
                    }
                }
            }
        }
        return solid >= total * 0.56;
    }

    private static int scatterPlants(World w, Random random, Set<Long> interior, int[] nest) {
        List<Long> sample = new ArrayList<>(interior);
        int placed = 0;
        boolean torch = random.nextFloat() < 0.6f;
        for (int attempt = 0; attempt < 72 && placed < 14; attempt++) {
            long air = sample.get(random.nextInt(sample.size()));
            int[] floor = findFloor(w, x(air), z(air), nest[1]);
            if (floor != null) {
                String floorBlock = w.block(floor[0], floor[1], floor[2]);
                if ((HEALTHY.equals(floorBlock) || DRY.equals(floorBlock)) && blobPlant(w, random, floor[0], floor[1] + 1, floor[2], torch)) {
                    placed++;
                }
            }
        }
        return placed;
    }

    /** SiftSnifferPlantPlacement#place: a torchflower, or a pitcher plant (two blocks). */
    static boolean blobPlant(World w, Random random, int x, int y, int z, boolean torch) {
        if (!AIR.equals(w.block(x, y, z)) || !w.writable(x, y, z)) {
            return false;
        }
        if (torch) {
            return w.plant(x, y, z, TORCHFLOWER, 0);
        }
        if (!AIR.equals(w.block(x, y + 1, z)) || !w.writable(x, y + 1, z)) {
            return false;
        }
        return w.plant(x, y, z, PITCHER, 3 + random.nextInt(2));
    }

    private static List<int[]> buildNest(World w, Random random, int[] c) {
        List<int[]> nest = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double distance = square(dx / 3.25) + square(dz / 3.25);
                if (distance > 1.0 || distance > 0.68 && random.nextFloat() < 0.34f) {
                    continue;
                }
                int[] floor = findFloor(w, c[0] + dx, c[2] + dz, c[1]);
                if (floor != null) {
                    String current = w.block(floor[0], floor[1], floor[2]);
                    if (HEALTHY.equals(current) || DRY.equals(current)) {
                        w.set(floor[0], floor[1], floor[2], FOLIAGE);
                        nest.add(floor);
                    }
                }
            }
        }
        return nest;
    }

    private static boolean onFreeNestBlock(World w, Random random, List<int[]> nest, String plant) {
        for (int attempt = 0; attempt < 18; attempt++) {
            int[] floor = nest.get(random.nextInt(nest.size()));
            if (AIR.equals(w.block(floor[0], floor[1] + 1, floor[2])) && w.plant(floor[0], floor[1] + 1, floor[2], plant, 0)) {
                return true;
            }
        }
        return false;
    }

    /** The Sift floor under air in y aroundY+3 … aroundY-7. */
    static int[] findFloor(World w, int x, int z, int aroundY) {
        for (int y = aroundY + 3; y >= aroundY - 7; y--) {
            if (AIR.equals(w.block(x, y, z)) && TERRAIN.contains(w.block(x, y - 1, z))) {
                return new int[] {x, y - 1, z};
            }
        }
        return null;
    }

    /**
     * SnifferPlantPatchFeature: 5–9 torchflowers or pitcher plants on healthy sculk, dry sculk or siftslate
     * growth around the chunk centre. Returns the number placed.
     */
    public static int plantPatch(World w, Random random, int chunkX, int chunkZ) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int target = 5 + random.nextInt(5);
        int placed = 0;
        boolean torch = random.nextFloat() < 0.58f;
        for (int attempt = 0; attempt < 28 && placed < target; attempt++) {
            int x = centerX + random.nextInt(13) - 6;
            int z = centerZ + random.nextInt(13) - 6;
            int y = w.height(x, z);
            String floor = w.block(x, y - 1, z);
            if ((HEALTHY.equals(floor) || DRY.equals(floor) || "siftslate_growth".equals(floor)) && AIR.equals(w.block(x, y, z))
                    && w.writable(x, y, z) && blobPlant(w, random, x, y, z, torch)) {
                placed++;
            }
        }
        return placed;
    }

    private static int distanceSquared(int x, int y, int z, int[] c) {
        int dx = x - c[0];
        int dy = y - c[1];
        int dz = z - c[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    static int x(long p) {
        return (int) (p >> 38);
    }

    static int y(long p) {
        return (int) (p << 52 >> 52);
    }

    static int z(long p) {
        return (int) (p << 26 >> 38);
    }

    private static long offset(long p, int dx, int dy, int dz) {
        return pack(x(p) + dx, y(p) + dy, z(p) + dz);
    }
}
