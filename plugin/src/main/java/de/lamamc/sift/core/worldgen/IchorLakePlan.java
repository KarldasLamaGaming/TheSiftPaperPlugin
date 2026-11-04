package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Surface ichor lakes and cave springs (alpha.25), a port of IchorLakeFeature and SiftIchorCaveSpringFeature
 * of the original 1.1.1. The plan only reads the world through {@link World} and returns the block changes,
 * so it can be tested. Not ported yet: the cascade variant (two lakes with a falling channel) and willow
 * islands in large overgrown lakes.
 */
public final class IchorLakePlan {

    /** What the plan needs to know about the world. */
    public interface World {
        /** SiftMonolithFeature#findTerrainSurface: y above the topmost Sift terrain within 8 blocks below the WG surface, or -1. */
        int surface(int x, int z);

        boolean terrain(int x, int y, int z);

        boolean ichor(int x, int y, int z);

        /** Inside the area the lake may use (the 3 × 3 chunks around its chunk, minus a margin). */
        boolean contains(int x, int z);

        /** Touches the main portal area. */
        boolean nearPortal(int minX, int minZ, int maxX, int maxZ);

        boolean overgrown();

        int minY();

        int maxY();
    }

    /** SMALL 4–7, MEDIUM 8–12, LARGE 12–15, MEGA 14 (radius; beach, depth, relief, minimum area, rocks). */
    public enum Variant {
        SMALL(4, 7, 1, 2, 3, 4, 28, false),
        MEDIUM(8, 12, 2, 3, 4, 4, 90, false),
        LARGE(12, 15, 4, 5, 5, 5, 300, true),
        MEGA(14, 14, 6, 6, 6, 4, 450, true);

        final int minRadius;
        final int maxRadius;
        final int minBeach;
        final int maxBeach;
        final int maxDepth;
        final int maxRelief;
        final int minArea;
        final boolean rocks;

        Variant(int minRadius, int maxRadius, int minBeach, int maxBeach, int maxDepth, int maxRelief, int minArea, boolean rocks) {
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.minBeach = minBeach;
            this.maxBeach = maxBeach;
            this.maxDepth = maxDepth;
            this.maxRelief = maxRelief;
            this.minArea = minArea;
            this.rocks = rocks;
        }
    }

    /** "clear" (air where replaceable), "ichor" (source) or a Sift block id. */
    public record Edit(int x, int y, int z, String block) {
    }

    /**
     * @param edits      in order of application
     * @param waterCells x, z of every ichor column (surface at waterY)
     * @param shoreFloors floor blocks around the lake that may get plants
     * @param box        minX, minZ, maxX, maxZ the lake reserves
     */
    public record Result(Variant variant, String reason, int waterY, List<Edit> edits, List<int[]> waterCells,
                         List<int[]> shoreFloors, int[] box) {
        public boolean built() {
            return reason == null;
        }

        static Result fail(Variant variant, String reason) {
            return new Result(variant, reason, 0, List.of(), List.of(), List.of(), null);
        }
    }

    private record Xz(int x, int z) {
    }

    private record Column(int x, int z, int surfaceY, double normalized) {
    }

    private static final int MAX_EXTENT = 24;
    public static final String CLEAR = "clear";
    public static final String ICHOR = "ichor";
    private static final String SIFTSLATE = "siftslate";
    private static final String GROWTH = "siftslate_growth";
    private static final String DRY = "dry_healthy_sculk";
    private static final String DRY_GROWTH = "dry_healthy_sculk_growth";
    private static final String HEALTHY = "healthy_sculk";

    private IchorLakePlan() {
    }

    public static Result plan(World w, RandomGenerator random, int chunkX, int chunkZ) {
        int centerX = (chunkX << 4) + 9;
        int centerZ = (chunkZ << 4) + 9;
        if (w.surface(centerX, centerZ) < 0) {
            return Result.fail(null, "kein Gelände");
        }
        int relief = relief(w, centerX, centerZ, 16);
        if (relief < 0) {
            return Result.fail(null, "Rand oder kein Gelände");
        }
        Variant variant = variant(random.nextInt(100), relief);
        if (nearbyIchor(w, centerX, centerZ, variant == Variant.MEGA ? 20 : 15)) {
            return Result.fail(variant, "Ichor in der Nähe");
        }
        boolean expanded = variant == Variant.MEGA && random.nextFloat() < 0.68f;
        int radiusX = expanded ? 15 + random.nextInt(2) : variant.minRadius + random.nextInt(variant.maxRadius - variant.minRadius + 1);
        int radiusZ = expanded ? clamp(radiusX + random.nextInt(5) - 2, 14, 16)
                : clamp(radiusX + random.nextInt(7) - 3, variant.minRadius, variant.maxRadius);
        int beachWidth = expanded ? 5 : variant.minBeach + random.nextInt(variant.maxBeach - variant.minBeach + 1);
        int extent = (int) Math.ceil(Math.max(radiusX, radiusZ) * 1.14) + beachWidth;
        if (extent > MAX_EXTENT) {
            return Result.fail(variant, "zu groß");
        }
        int[] box = {centerX - extent, centerZ - extent, centerX + extent, centerZ + extent};
        if (w.nearPortal(box[0], box[1], box[2], box[3])) {
            return Result.fail(variant, "Portal");
        }
        double angle = random.nextDouble() * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        long salt = random.nextLong();
        Map<Xz, Column> water = waterMask(w, centerX, centerZ, radiusX, radiusZ, angle, cos, sin, salt);
        if (water.size() < variant.minArea) {
            return Result.fail(variant, "zu wenig Fläche");
        }
        Map<Xz, Integer> beach = beachMask(water, beachWidth);
        List<Integer> surfaces = new ArrayList<>(water.size());
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Column c : water.values()) {
            surfaces.add(c.surfaceY());
            min = Math.min(min, c.surfaceY());
            max = Math.max(max, c.surfaceY());
        }
        if (max - min > variant.maxRelief) {
            return Result.fail(variant, "zu uneben");
        }
        surfaces.sort(Comparator.naturalOrder());
        int waterY = surfaces.get((int) Math.floor((surfaces.size() - 1) * 0.36)) - 1;
        if (waterY < w.minY() + 6 || waterY > w.maxY() - 7) {
            return Result.fail(variant, "Höhe");
        }
        Map<Xz, Column> beachColumns = new LinkedHashMap<>();
        for (Map.Entry<Xz, Integer> e : beach.entrySet()) {
            Xz cell = e.getKey();
            if (!w.contains(cell.x(), cell.z())) {
                return Result.fail(variant, "Rand");
            }
            int surfaceY = w.surface(cell.x(), cell.z());
            if (surfaceY < 0 || !w.terrain(cell.x(), surfaceY - 1, cell.z())) {
                return Result.fail(variant, "Ufer ohne Gelände");
            }
            int layer = e.getValue();
            int naturalTop = surfaceY - 1;
            int target = shoreTop(naturalTop, waterY, layer, beachWidth);
            if (naturalTop < waterY - beachWidth || naturalTop > waterY + variant.maxRelief + beachWidth
                    || Math.abs(target - naturalTop) > beachWidth || !support(w, cell.x(), naturalTop, cell.z(), 3)) {
                return Result.fail(variant, "Ufer zu steil");
            }
            beachColumns.put(cell, new Column(cell.x(), cell.z(), surfaceY, 1.0 + (double) layer / beachWidth));
        }
        Map<Xz, Integer> targets = shoreTargets(w, water, beach, beachColumns, waterY, beachWidth);
        if (targets.isEmpty()) {
            return Result.fail(variant, "Ufer nicht glatt");
        }
        String[] palette = palette(w.overgrown(), random);
        String basin = palette[0];
        String shore = palette[1];
        List<Edit> edits = new ArrayList<>();
        for (Map.Entry<Xz, Column> e : beachColumns.entrySet()) {
            shapeBeach(edits, e.getValue(), targets.get(e.getKey()), basin, shore);
        }
        Map<Xz, Integer> floors = new HashMap<>();
        int placed = 0;
        for (Column c : water.values()) {
            int floorY = carve(edits, c, waterY, basin, salt, variant.maxDepth);
            floors.put(new Xz(c.x(), c.z()), floorY);
            placed += waterY - floorY;
        }
        if (variant == Variant.MEGA || variant.rocks && random.nextFloat() < 0.82f) {
            rocks(edits, random, water, floors, waterY, basin, shore, salt);
        }
        if (placed < variant.minArea) {
            return Result.fail(variant, "zu wenig Ichor");
        }
        List<int[]> shoreFloors = new ArrayList<>();
        for (Xz cell : beachMask(water, 4).keySet()) {
            if (w.contains(cell.x(), cell.z())) {
                Integer shaped = targets.get(cell);
                int floorY = shaped != null ? shaped : w.surface(cell.x(), cell.z()) - 1;
                if (floorY >= w.minY()) {
                    shoreFloors.add(new int[] {cell.x(), floorY, cell.z()});
                }
            }
        }
        List<int[]> cells = water.values().stream().map(c -> new int[] {c.x(), c.z()}).toList();
        return new Result(variant, null, waterY, edits, cells, shoreFloors, box);
    }

    /** Relief ≤ 3: MEGA; ≤ 5 with roll < 72: LARGE; ≤ 7 with roll < 82: MEDIUM; else SMALL. */
    public static Variant variant(int roll, int relief) {
        if (relief <= 3) {
            return Variant.MEGA;
        }
        if (relief <= 5 && roll < 72) {
            return Variant.LARGE;
        }
        return relief <= 7 && roll < 82 ? Variant.MEDIUM : Variant.SMALL;
    }

    static int relief(World w, int centerX, int centerZ, int radius) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                if (!w.contains(centerX + dx, centerZ + dz)) {
                    return -1;
                }
                int y = w.surface(centerX + dx, centerZ + dz);
                if (y < 0) {
                    return -1;
                }
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        return max - min;
    }

    private static Map<Xz, Column> waterMask(World w, int centerX, int centerZ, int radiusX, int radiusZ, double angle,
                                             double cos, double sin, long salt) {
        Map<Xz, Column> water = new LinkedHashMap<>();
        int limit = (int) Math.ceil(Math.max(radiusX, radiusZ) * 1.14);
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!w.contains(x, z)) {
                    continue;
                }
                double localX = (dx * cos - dz * sin) / radiusX;
                double localZ = (dx * sin + dz * cos) / radiusZ;
                double warpX = localX + Math.sin(localZ * 2.7 + angle * 1.9) * 0.045;
                double warpZ = localZ + Math.sin(localX * 2.2 - angle * 1.3) * 0.04;
                double distance = Math.sqrt(warpX * warpX + warpZ * warpZ);
                double polar = Math.atan2(localZ, localX);
                double shoreline = 0.95 + Math.sin(polar * 3.0 + angle * 1.7) * 0.09 + Math.sin(polar * 5.0 - angle * 0.8) * 0.055
                        + Math.cos(polar * 7.0 + angle * 0.37) * 0.028 + (hash01(x >> 2, 0, z >> 2, salt) - 0.5) * 0.028;
                if (distance > shoreline) {
                    continue;
                }
                int surfaceY = w.surface(x, z);
                if (surfaceY < 0 || !w.terrain(x, surfaceY - 1, z)) {
                    return Map.of();
                }
                water.put(new Xz(x, z), new Column(x, z, surfaceY, distance / Math.max(0.01, shoreline)));
            }
        }
        return water;
    }

    private static Map<Xz, Integer> beachMask(Map<Xz, ?> water, int width) {
        Map<Xz, Integer> beach = new LinkedHashMap<>();
        for (Xz source : water.keySet()) {
            for (int dx = -width; dx <= width; dx++) {
                for (int dz = -width; dz <= width; dz++) {
                    int layer = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
                    if (layer == 0 || layer > width) {
                        continue;
                    }
                    Xz candidate = new Xz(source.x() + dx, source.z() + dz);
                    if (!water.containsKey(candidate)) {
                        beach.merge(candidate, layer, Math::min);
                    }
                }
            }
        }
        return beach;
    }

    /** Basin 70 % dry / 21 % healthy / 9 % siftslate; shore in overgrown biomes 58 % growth, else mostly dry. */
    static String[] palette(boolean overgrown, RandomGenerator random) {
        int basinRoll = random.nextInt(100);
        String basin = basinRoll < 70 ? DRY : basinRoll < 91 ? HEALTHY : SIFTSLATE;
        int shoreRoll = random.nextInt(100);
        String shore = overgrown && shoreRoll < 58 ? GROWTH
                : shoreRoll < (overgrown ? 79 : 72) ? DRY : shoreRoll < 93 ? HEALTHY : SIFTSLATE;
        return new String[] {basin, shore};
    }

    /** Height of the shore at a beach layer: the water level next to the water, the natural ground at the edge. */
    public static int shoreTop(int naturalTop, int waterY, int layer, int width) {
        if (layer <= 1 || width <= 1) {
            return waterY;
        }
        double outward = clamp((double) (layer - 1) / (width - 1), 0, 1);
        double smooth = outward * outward * (3.0 - 2.0 * outward);
        return (int) Math.floor(waterY + (naturalTop - waterY) * smooth + 0.5);
    }

    private static Map<Xz, Integer> shoreTargets(World w, Map<Xz, Column> water, Map<Xz, Integer> beach, Map<Xz, Column> beachColumns,
                                                 int waterY, int width) {
        Map<Xz, Integer> targets = new LinkedHashMap<>();
        for (Map.Entry<Xz, Column> e : beachColumns.entrySet()) {
            int layer = beach.get(e.getKey());
            int naturalTop = e.getValue().surfaceY() - 1;
            targets.put(e.getKey(), layer == width ? naturalTop : shoreTop(naturalTop, waterY, layer, width));
        }
        int[] steps = {-1, 0, 1, 0, -1};
        for (int pass = 0; pass < width * 6; pass++) {
            Map<Xz, Integer> next = new LinkedHashMap<>(targets.size());
            for (Map.Entry<Xz, Column> e : beachColumns.entrySet()) {
                Xz cell = e.getKey();
                int layer = beach.get(cell);
                if (layer == 1) {
                    next.put(cell, waterY);
                } else if (layer == width) {
                    next.put(cell, e.getValue().surfaceY() - 1);
                } else {
                    int sum = shoreTop(e.getValue().surfaceY() - 1, waterY, layer, width) * 3;
                    int weight = 3;
                    for (int d = 0; d < 4; d++) {
                        Xz n = new Xz(cell.x() + steps[d], cell.z() + steps[d + 1]);
                        if (water.containsKey(n)) {
                            sum += waterY * 3;
                            weight += 3;
                        } else if (targets.containsKey(n)) {
                            sum += targets.get(n);
                            weight++;
                        }
                    }
                    next.put(cell, clamp((int) Math.floor((double) sum / weight + 0.5), waterY - layer, waterY + layer));
                }
            }
            targets = next;
        }
        for (Map.Entry<Xz, Integer> e : targets.entrySet()) {
            Xz cell = e.getKey();
            int layer = beach.get(cell);
            for (int d = 0; d < 4; d++) {
                Xz n = new Xz(cell.x() + steps[d], cell.z() + steps[d + 1]);
                int neighbourTop;
                if (water.containsKey(n)) {
                    neighbourTop = waterY;
                } else if (targets.containsKey(n)) {
                    neighbourTop = targets.get(n);
                } else {
                    if (layer != width) {
                        continue;
                    }
                    int surface = w.surface(n.x(), n.z());
                    if (surface < 0) {
                        return Map.of();
                    }
                    neighbourTop = surface - 1;
                }
                if (Math.abs(e.getValue() - neighbourTop) > 1) {
                    return Map.of();
                }
            }
        }
        return targets;
    }

    private static void shapeBeach(List<Edit> edits, Column c, int targetTop, String basin, String shore) {
        int naturalTop = c.surfaceY() - 1;
        for (int y = targetTop + 1; y <= Math.max(c.surfaceY() + 3, targetTop + 3); y++) {
            edits.add(new Edit(c.x(), y, c.z(), CLEAR));
        }
        int supportBottom = Math.min(naturalTop, targetTop) - 2;
        for (int y = supportBottom; y < targetTop; y++) {
            edits.add(new Edit(c.x(), y, c.z(), y >= targetTop - 2 ? basin : SIFTSLATE));
        }
        edits.add(new Edit(c.x(), targetTop, c.z(), topOver(shore, basin)));
    }

    /** Growth on dry sculk becomes dry sculk growth (SiftLakeFeature#shorelineState / islandSurfaceState). */
    static String topOver(String requested, String below) {
        return requested.equals(GROWTH) && below.equals(DRY) ? DRY_GROWTH : requested;
    }

    /** Carves one column and fills it with ichor; returns its new floor. */
    private static int carve(List<Edit> edits, Column c, int waterY, String lining, long salt, int maxDepth) {
        double centerStrength = 1.0 - clamp(c.normalized(), 0, 1);
        int depth = clamp(1 + (int) Math.floor(centerStrength * (maxDepth - 0.55)
                + hash01(c.x(), waterY, c.z(), salt ^ -6752110988234923001L) * 0.7), 1, maxDepth);
        int floorY = waterY - depth;
        for (int y = floorY + 1; y <= Math.max(c.surfaceY() + 2, waterY + 1); y++) {
            edits.add(new Edit(c.x(), y, c.z(), CLEAR));
        }
        for (int y = floorY - 1; y <= floorY; y++) {
            edits.add(new Edit(c.x(), y, c.z(), lining));
        }
        for (int y = floorY + 1; y <= waterY; y++) {
            edits.add(new Edit(c.x(), y, c.z(), ICHOR));
        }
        return floorY;
    }

    private static void rocks(List<Edit> edits, RandomGenerator random, Map<Xz, Column> water, Map<Xz, Integer> floors, int waterY,
                              String basin, String shore, long salt) {
        List<Column> candidates = water.values().stream().filter(c -> c.normalized() > 0.2 && c.normalized() < 0.72).toList();
        if (candidates.isEmpty()) {
            return;
        }
        boolean island = water.size() > 400;
        int formations = island ? 3 + random.nextInt(2) : 1 + random.nextInt(3);
        List<Xz> used = new ArrayList<>();
        for (int f = 0; f < formations; f++) {
            Column center = null;
            for (int attempt = 0; attempt < 24 && center == null; attempt++) {
                Column candidate = candidates.get(random.nextInt(candidates.size()));
                boolean separated = used.stream().noneMatch(u -> {
                    int dx = candidate.x() - u.x();
                    int dz = candidate.z() - u.z();
                    return dx * dx + dz * dz < (island ? 49 : 16);
                });
                if (separated) {
                    center = candidate;
                }
            }
            if (center == null) {
                continue;
            }
            used.add(new Xz(center.x(), center.z()));
            int radius = island ? 2 + random.nextInt(3) : 1 + random.nextInt(2);
            int peak = island ? 2 + random.nextInt(4) : 1 + random.nextInt(4);
            long formationSalt = salt ^ (long) f * -7046029254386353131L;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = center.x() + dx;
                    int z = center.z() + dz;
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double irregular = radius + (hash01(x, waterY, z, formationSalt) - 0.5) * 0.85;
                    Integer floorY = floors.get(new Xz(x, z));
                    if (distance > irregular || floorY == null) {
                        continue;
                    }
                    int height = Math.max(0, peak - (int) Math.floor(distance * 1.35))
                            + (hash01(x, waterY, z, formationSalt) > 0.78 ? 1 : 0);
                    for (int y = floorY; y <= waterY + height; y++) {
                        String rock = y == waterY + height ? topOver(shore, basin) : y >= waterY - 1 ? basin : SIFTSLATE;
                        edits.add(new Edit(x, y, z, rock));
                    }
                }
            }
        }
    }

    private static boolean nearbyIchor(World w, int centerX, int centerZ, int radius) {
        for (int dx = -radius; dx <= radius; dx += 5) {
            for (int dz = -radius; dz <= radius; dz += 5) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!w.contains(x, z)) {
                    continue;
                }
                int surface = w.surface(x, z);
                if (surface < 0) {
                    continue;
                }
                for (int dy = -6; dy <= 1; dy++) {
                    if (w.ichor(x, surface + dy, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean support(World w, int x, int y, int z, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!w.terrain(x, y - i, z)) {
                return false;
            }
        }
        return true;
    }

    // ---- cave springs (SiftIchorCaveSpringFeature) ----------------------------------------------------------

    /** What a spring needs to know. */
    public interface SpringWorld {
        /** siftslate, siftslate growth, healthy sculk, dry healthy sculk (growth). */
        boolean rock(int x, int y, int z);

        boolean empty(int x, int y, int z);

        /** WORLD_SURFACE_WG height. */
        int surface(int x, int z);
    }

    private static final int[][] OPENINGS = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}};

    /** Rock above and below, of N/S/W/E/down exactly 4 rock and 1 open, and no opening near the surface. */
    public static boolean spring(SpringWorld w, int x, int y, int z) {
        for (int[] d : OPENINGS) {
            if (w.empty(x + d[0], y + d[1], z + d[2]) && y + d[1] >= w.surface(x + d[0], z + d[2]) - 1) {
                return false;
            }
        }
        if (!w.rock(x, y + 1, z) || !w.rock(x, y - 1, z)) {
            return false;
        }
        if (!w.empty(x, y, z) && !w.rock(x, y, z)) {
            return false;
        }
        int rock = 0;
        int holes = 0;
        for (int[] d : OPENINGS) {
            if (w.rock(x + d[0], y + d[1], z + d[2])) {
                rock++;
            }
            if (w.empty(x + d[0], y + d[1], z + d[2])) {
                holes++;
            }
        }
        return rock == 4 && holes == 1;
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    public static double hash01(int x, int y, int z, long salt) {
        long value = salt ^ (long) x * -7046029254386353131L;
        value ^= (long) y * -4417276706812531889L;
        value ^= (long) z * 1609587929392839161L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        value ^= value >>> 31;
        return (value >>> 11) * (double) 1.110223E-16F;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
