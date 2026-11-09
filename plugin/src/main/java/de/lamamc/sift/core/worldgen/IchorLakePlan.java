package de.lamamc.sift.core.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Surface ichor lakes and cave springs (alpha.25), a port of IchorLakeFeature and SiftIchorCaveSpringFeature
 * of the original 1.1.1. The plan only reads the world through {@link World} and returns the block changes,
 * so it can be tested. alpha.25.1 adds the cascade (a lower lake and an upper pond on a mountain shelf,
 * joined by a falling channel) and willow islands in large overgrown lakes.
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

    /** "clear" (air where replaceable), "air" (always), "ichor" (source), "falling" (falling ichor) or a Sift block id. */
    public record Edit(int x, int y, int z, String block) {
    }

    /**
     * @param edits      in order of application
     * @param waterCells x, y, z of the surface block of every ichor column (lake and upper pond)
     * @param shoreFloors floor blocks around the lake that may get plants
     * @param box        minX, minZ, maxX, maxZ the lake reserves
     * @param islandRoot root of the willow of a willow island (x, y, z), or null
     * @param cascade    a cascade instead of a single lake
     */
    public record Result(Variant variant, String reason, int waterY, List<Edit> edits, List<int[]> waterCells,
                         List<int[]> shoreFloors, int[] box, int[] islandRoot, boolean cascade) {
        public boolean built() {
            return reason == null;
        }

        static Result fail(Variant variant, String reason) {
            return new Result(variant, reason, 0, List.of(), List.of(), List.of(), null, null, false);
        }

        /** "MEGA", or "CASCADE" for a cascade, for the statistics. */
        public String kind() {
            return cascade ? "CASCADE" : variant == null ? "-" : variant.name();
        }
    }

    private record Site(int upperX, int upperY, int upperZ, int lowerX, int lowerY, int lowerZ, double forwardX, double forwardZ) {
    }

    private record Shelf(int x, int z, int naturalTop, double normalized, double forward, double side) {
    }

    /** Island root and the island's surface floors. */
    private record Island(int[] root, List<int[]> floors) {
    }

    private record Xz(int x, int z) {
    }

    private record Column(int x, int z, int surfaceY, double normalized) {
    }

    private static final int MAX_EXTENT = 24;
    public static final String CLEAR = "clear";
    public static final String ICHOR = "ichor";
    public static final String AIR = "air";
    public static final String FALLING = "falling";
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
        Site site = relief >= 9 ? cascadeSite(w, centerX, centerZ) : null;
        if (site != null && random.nextFloat() < 0.82f) {
            return cascade(w, random, site);
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
        Map<Xz, Column> beachColumns = validateBeach(w, beach, beachWidth, waterY, variant.maxRelief);
        if (beachColumns.isEmpty()) {
            return Result.fail(variant, "Ufer zu steil");
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
        Island island = null;
        if (variant == Variant.MEGA || variant.rocks && random.nextFloat() < 0.82f) {
            island = rocks(edits, random, water, floors, waterY, basin, shore, salt, w.overgrown());
        }
        if (placed < variant.minArea) {
            return Result.fail(variant, "zu wenig Ichor");
        }
        List<int[]> shoreFloors = shoreFloors(w, water, targets);
        if (island != null) {
            shoreFloors.addAll(island.floors());
        }
        Set<Xz> dry = island == null ? Set.of() : island.floors().stream().map(f -> new Xz(f[0], f[2])).collect(Collectors.toSet());
        List<int[]> cells = water.values().stream().filter(c -> !dry.contains(new Xz(c.x(), c.z())))
                .map(c -> new int[] {c.x(), waterY, c.z()}).toList();
        return new Result(variant, null, waterY, edits, cells, shoreFloors, box, island == null ? null : island.root(), false);
    }

    private static List<int[]> shoreFloors(World w, Map<Xz, Column> water, Map<Xz, Integer> targets) {
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
        return shoreFloors;
    }

    /** IchorLakeFeature#validateBeach: every beach column on natural Sift ground, not too far from the target. */
    private static Map<Xz, Column> validateBeach(World w, Map<Xz, Integer> beach, int width, int waterY, int maxRelief) {
        Map<Xz, Column> columns = new LinkedHashMap<>();
        for (Map.Entry<Xz, Integer> e : beach.entrySet()) {
            Xz cell = e.getKey();
            if (!w.contains(cell.x(), cell.z())) {
                return Map.of();
            }
            int surfaceY = w.surface(cell.x(), cell.z());
            if (surfaceY < 0 || !w.terrain(cell.x(), surfaceY - 1, cell.z())) {
                return Map.of();
            }
            int layer = e.getValue();
            int naturalTop = surfaceY - 1;
            int target = shoreTop(naturalTop, waterY, layer, width);
            if (naturalTop < waterY - width || naturalTop > waterY + maxRelief + width
                    || Math.abs(target - naturalTop) > width || !support(w, cell.x(), naturalTop, cell.z(), 3)) {
                return Map.of();
            }
            columns.put(cell, new Column(cell.x(), cell.z(), surfaceY, 1.0 + (double) layer / width));
        }
        return columns;
    }

    // ---- cascade (alpha.25.1) -------------------------------------------------------------------------------

    /** The best of 16 directions with a drop of 10–34 blocks between a flat upper and a flat lower spot. */
    private static Site cascadeSite(World w, int centerX, int centerZ) {
        Site best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int direction = 0; direction < 16; direction++) {
            double angle = direction * Math.PI * 2.0 / 16.0;
            double fx = Math.cos(angle);
            double fz = Math.sin(angle);
            int upperX = centerX - (int) Math.round(fx * 7.0);
            int upperZ = centerZ - (int) Math.round(fz * 7.0);
            int lowerX = centerX + (int) Math.round(fx * 9.0);
            int lowerZ = centerZ + (int) Math.round(fz * 9.0);
            if (!w.contains(upperX, upperZ) || !w.contains(lowerX, lowerZ)) {
                continue;
            }
            int upperSurface = w.surface(upperX, upperZ);
            int lowerSurface = w.surface(lowerX, lowerZ);
            if (upperSurface < 0 || lowerSurface < 0) {
                continue;
            }
            int drop = upperSurface - lowerSurface;
            if (drop < 10 || drop > 34) {
                continue;
            }
            int upperRelief = relief(w, upperX, upperZ, 4);
            int lowerRelief = relief(w, lowerX, lowerZ, 7);
            if (upperRelief < 0 || lowerRelief < 0 || upperRelief > 4 || lowerRelief > 5) {
                continue;
            }
            int minX = Math.min(upperX - 8, lowerX - 12);
            int maxX = Math.max(upperX + 8, lowerX + 12);
            int minZ = Math.min(upperZ - 8, lowerZ - 12);
            int maxZ = Math.max(upperZ + 8, lowerZ + 12);
            if (!w.contains(minX, minZ) || !w.contains(maxX, maxZ) || w.nearPortal(minX, minZ, maxX, maxZ)) {
                continue;
            }
            int score = drop * 8 - upperRelief * 3 - lowerRelief * 2;
            if (score > bestScore) {
                bestScore = score;
                best = new Site(upperX, upperSurface - 1, upperZ, lowerX, lowerSurface - 1, lowerZ, fx, fz);
            }
        }
        return best;
    }

    private static Result cascade(World w, RandomGenerator random, Site site) {
        long salt = random.nextLong();
        int radiusX = 10 + random.nextInt(2);
        int radiusZ = 9 + random.nextInt(2);
        int beachWidth = 4;
        double angle = Math.atan2(site.forwardZ(), site.forwardX());
        Map<Xz, Column> lower = waterMask(w, site.lowerX(), site.lowerZ(), radiusX, radiusZ, angle, Math.cos(angle), Math.sin(angle), salt);
        if (lower.size() < 135) {
            return cascadeFail("Kaskade: zu wenig Fläche");
        }
        List<Integer> surfaces = lower.values().stream().map(Column::surfaceY).sorted().toList();
        int lowerY = surfaces.get(surfaces.size() / 2) - 1;
        if (surfaces.getLast() - surfaces.getFirst() > 5) {
            return cascadeFail("Kaskade: unten zu uneben");
        }
        Map<Xz, Integer> beach = beachMask(lower, beachWidth);
        Map<Xz, Column> beachColumns = validateBeach(w, beach, beachWidth, lowerY, 5);
        if (beachColumns.isEmpty()) {
            return cascadeFail("Kaskade: Ufer zu steil");
        }
        Map<Xz, Integer> targets = shoreTargets(w, lower, beach, beachColumns, lowerY, beachWidth);
        if (targets.isEmpty()) {
            return cascadeFail("Kaskade: Ufer nicht glatt");
        }
        Map<Xz, Shelf> shelf = shelfMask(w, site, salt);
        if (shelf.size() < 155) {
            return cascadeFail("Kaskade: kein Bergsims");
        }
        Map<Xz, Double> pond = pondMask(site, shelf, salt);
        if (pond.size() < 52) {
            return cascadeFail("Kaskade: Teich zu klein");
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Xz cell : beach.keySet()) {
            minX = Math.min(minX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxZ = Math.max(maxZ, cell.z());
        }
        for (Xz cell : shelf.keySet()) {
            minX = Math.min(minX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxZ = Math.max(maxZ, cell.z());
        }
        minX = Math.min(minX, Math.min(site.upperX(), site.lowerX()) - 5);
        minZ = Math.min(minZ, Math.min(site.upperZ(), site.lowerZ()) - 5);
        maxX = Math.max(maxX, Math.max(site.upperX(), site.lowerX()) + 5);
        maxZ = Math.max(maxZ, Math.max(site.upperZ(), site.lowerZ()) + 5);
        String[] palette = palette(w.overgrown(), random);
        String basin = palette[0];
        String shore = palette[1];
        List<Edit> edits = new ArrayList<>();
        for (Map.Entry<Xz, Column> e : beachColumns.entrySet()) {
            shapeBeach(edits, e.getValue(), targets.get(e.getKey()), basin, shore);
        }
        Map<Xz, Integer> floors = new HashMap<>();
        for (Column c : lower.values()) {
            floors.put(new Xz(c.x(), c.z()), carve(edits, c, lowerY, basin, salt ^ 5498709625288019790L, 5));
        }
        shapeShelf(edits, shelf, site.upperY(), basin, shore);
        carvePond(edits, pond, site.upperY(), basin, salt);
        channel(edits, site, lowerY, basin);
        Island island = null;
        if (random.nextFloat() < 0.88f) {
            island = rocks(edits, random, lower, floors, lowerY, basin, shore, salt ^ 4846246222350271820L, w.overgrown());
        }
        List<int[]> shoreFloors = shoreFloors(w, lower, targets);
        if (island != null) {
            shoreFloors.addAll(island.floors());
        }
        Set<Xz> dry = island == null ? Set.of() : island.floors().stream().map(f -> new Xz(f[0], f[2])).collect(Collectors.toSet());
        List<int[]> cells = new ArrayList<>();
        lower.values().stream().filter(c -> !dry.contains(new Xz(c.x(), c.z()))).forEach(c -> cells.add(new int[] {c.x(), lowerY, c.z()}));
        pond.keySet().forEach(c -> cells.add(new int[] {c.x(), site.upperY(), c.z()}));
        return new Result(null, null, lowerY, edits, cells, shoreFloors, new int[] {minX, minZ, maxX, maxZ},
                island == null ? null : island.root(), true);
    }

    private static Result cascadeFail(String reason) {
        return new Result(null, reason, 0, List.of(), List.of(), List.of(), null, null, true);
    }

    private static Map<Xz, Shelf> shelfMask(World w, Site site, long salt) {
        Map<Xz, Shelf> shelf = new LinkedHashMap<>();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                double forward = dx * site.forwardX() + dz * site.forwardZ();
                double side = -dx * site.forwardZ() + dz * site.forwardX();
                double forwardRadius = forward >= 0 ? 8.5 : 8.8;
                double distance = Math.sqrt(forward * forward / (forwardRadius * forwardRadius) + side * side / 77.44000000000001);
                double edge = 1.0 + (hash01(site.upperX() + dx >> 1, site.upperY(), site.upperZ() + dz >> 1, salt) - 0.5) * 0.13;
                if (distance > edge) {
                    continue;
                }
                int x = site.upperX() + dx;
                int z = site.upperZ() + dz;
                if (!w.contains(x, z)) {
                    return Map.of();
                }
                int surfaceY = w.surface(x, z);
                if (surfaceY < 0 || !support(w, x, surfaceY - 1, z, 3)) {
                    return Map.of();
                }
                int naturalTop = surfaceY - 1;
                if (naturalTop > site.upperY() + 5 || site.upperY() - naturalTop > 26) {
                    return Map.of();
                }
                shelf.put(new Xz(x, z), new Shelf(x, z, naturalTop, distance, forward, side));
            }
        }
        return shelf;
    }

    private static Map<Xz, Double> pondMask(Site site, Map<Xz, Shelf> shelf, long salt) {
        Map<Xz, Double> pond = new LinkedHashMap<>();
        double centerX = site.upperX() - site.forwardX() * 1.8;
        double centerZ = site.upperZ() - site.forwardZ() * 1.8;
        for (Shelf column : shelf.values()) {
            double dx = column.x() - centerX;
            double dz = column.z() - centerZ;
            double forward = dx * site.forwardX() + dz * site.forwardZ();
            double side = -dx * site.forwardZ() + dz * site.forwardX();
            double distance = Math.sqrt(forward * forward / 32.49 + side * side / 27.040000000000003);
            double edge = 1.0 + (hash01(column.x(), site.upperY(), column.z(), salt ^ 6147501750024687438L) - 0.5) * 0.12;
            if (distance <= edge) {
                pond.put(new Xz(column.x(), column.z()), distance / edge);
            }
        }
        return pond;
    }

    private static void shapeShelf(List<Edit> edits, Map<Xz, Shelf> shelf, int topY, String basin, String shore) {
        for (Shelf c : shelf.values()) {
            double edgeStrength = smoothstep(1.0, 0.7, c.normalized());
            boolean core = c.forward() >= -3.0 && c.forward() <= 7.6 && Math.abs(c.side()) <= 3.8;
            double strength = core ? 1.0 : edgeStrength;
            int targetTop = (int) Math.floor(c.naturalTop() + (topY - c.naturalTop()) * strength + 0.5);
            for (int y = targetTop + 1; y <= Math.max(c.naturalTop() + 3, targetTop + 3); y++) {
                edits.add(new Edit(c.x(), y, c.z(), CLEAR));
            }
            for (int y = Math.min(c.naturalTop(), targetTop) - 2; y <= targetTop; y++) {
                edits.add(new Edit(c.x(), y, c.z(), y == targetTop ? shore : y <= targetTop - 2 ? SIFTSLATE : basin));
            }
        }
    }

    private static void carvePond(List<Edit> edits, Map<Xz, Double> pond, int waterY, String lining, long salt) {
        for (Map.Entry<Xz, Double> e : pond.entrySet()) {
            Xz cell = e.getKey();
            double centerStrength = 1.0 - clamp(e.getValue(), 0, 1);
            int depth = clamp(1 + (int) Math.floor(centerStrength * 2.8 + hash01(cell.x(), waterY, cell.z(), salt) * 0.55), 1, 4);
            int floorY = waterY - depth;
            for (int y = floorY - 1; y <= floorY; y++) {
                edits.add(new Edit(cell.x(), y, cell.z(), lining));
            }
            for (int y = floorY + 1; y <= waterY; y++) {
                edits.add(new Edit(cell.x(), y, cell.z(), ICHOR));
            }
            edits.add(new Edit(cell.x(), waterY + 1, cell.z(), AIR));
        }
    }

    /** The channel out of the pond, the falling curtain at its lip and the stream down to the lower lake. */
    private static void channel(List<Edit> edits, Site site, int lowerY, String lining) {
        int upperY = site.upperY();
        double sideX = -site.forwardZ();
        double sideZ = site.forwardX();
        int lipX = site.upperX() + (int) Math.round(site.forwardX() * 7.0);
        int lipZ = site.upperZ() + (int) Math.round(site.forwardZ() * 7.0);
        for (int step = 1; step <= 7; step++) {
            int cx = site.upperX() + (int) Math.round(site.forwardX() * step);
            int cz = site.upperZ() + (int) Math.round(site.forwardZ() * step);
            int half = step < 3 ? 2 : 3;
            for (int lateral = -half; lateral <= half; lateral++) {
                channelCell(edits, cx + (int) Math.round(sideX * lateral), upperY, cz + (int) Math.round(sideZ * lateral), lining);
            }
        }
        for (int lateral = -3; lateral <= 3; lateral++) {
            int x = lipX + (int) Math.round(sideX * lateral);
            int z = lipZ + (int) Math.round(sideZ * lateral);
            for (int y = lowerY; y <= upperY; y++) {
                edits.add(new Edit(x, y, z, y == upperY ? ICHOR : FALLING));
            }
        }
        double dx = site.lowerX() - lipX;
        double dz = site.lowerZ() - lipZ;
        int length = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
        for (int step = 0; step <= length; step++) {
            double progress = (double) step / length;
            int x = (int) Math.floor(lipX + (site.lowerX() - lipX) * progress + 0.5);
            int z = (int) Math.floor(lipZ + (site.lowerZ() - lipZ) * progress + 0.5);
            channelCell(edits, x, lowerY, z, lining);
        }
    }

    private static void channelCell(List<Edit> edits, int x, int y, int z, String lining) {
        edits.add(new Edit(x, y - 1, z, lining));
        edits.add(new Edit(x, y, z, ICHOR));
        edits.add(new Edit(x, y + 1, z, AIR));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3.0 - 2.0 * t);
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

    /** Rock formations, or in a large overgrown lake sometimes (27 %) a willow island instead; returns the island. */
    private static Island rocks(List<Edit> edits, RandomGenerator random, Map<Xz, Column> water, Map<Xz, Integer> floors, int waterY,
                                String basin, String shore, long salt, boolean overgrown) {
        List<Column> candidates = water.values().stream().filter(c -> c.normalized() > 0.2 && c.normalized() < 0.72).toList();
        if (candidates.isEmpty()) {
            return null;
        }
        boolean island = water.size() > 400;
        if (island && overgrown && random.nextFloat() < 0.27f) {
            Column center = willowCenter(candidates, random);
            if (center != null) {
                return willowIsland(edits, random, water, floors, waterY, basin, salt, center);
            }
        }
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
        return null;
    }

    private static Column willowCenter(List<Column> candidates, RandomGenerator random) {
        Column best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int attempt = 0; attempt < 36; attempt++) {
            Column candidate = candidates.get(random.nextInt(candidates.size()));
            double d = candidate.normalized();
            if (d >= 0.2 && d <= 0.46 && Math.abs(d - 0.32) < bestDistance) {
                best = candidate;
                bestDistance = Math.abs(d - 0.32);
            }
        }
        return best;
    }

    /** IchorLakeFeature#placeWillowIsland: a low island of sculk and growth with a willow on it (at least 70 columns). */
    private static Island willowIsland(List<Edit> edits, RandomGenerator random, Map<Xz, Column> water, Map<Xz, Integer> floors,
                                       int waterY, String basin, long salt, Column center) {
        double radiusX = 6.7 + random.nextDouble() * 0.9;
        double radiusZ = 5.5 + random.nextDouble() * 0.9;
        double rotation = random.nextDouble() * Math.PI;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double phase = random.nextDouble() * Math.PI * 2.0;
        long islandSalt = salt ^ 6289642245231954259L;
        int limit = (int) Math.ceil(Math.max(radiusX, radiusZ) + 1.0);
        Map<Xz, Integer> surface = new LinkedHashMap<>();
        for (int dx = -limit; dx <= limit; dx++) {
            for (int dz = -limit; dz <= limit; dz++) {
                Xz cell = new Xz(center.x() + dx, center.z() + dz);
                if (!water.containsKey(cell)) {
                    continue;
                }
                double localX = dx * cos - dz * sin;
                double localZ = dx * sin + dz * cos;
                double normalized = Math.sqrt(localX * localX / (radiusX * radiusX) + localZ * localZ / (radiusZ * radiusZ));
                double theta = Math.atan2(localZ, localX);
                double edge = 1.0 + Math.sin(theta * 3.0 + phase) * 0.075 + Math.sin(theta * 5.0 - phase * 0.63) * 0.045;
                if (normalized <= edge) {
                    surface.put(cell, waterY + clamp((int) Math.floor((1.0 - normalized / edge) * 3.15), 0, 3));
                }
            }
        }
        if (surface.size() < 70) {
            return null;
        }
        List<int[]> islandFloors = new ArrayList<>();
        for (Map.Entry<Xz, Integer> e : surface.entrySet()) {
            Xz cell = e.getKey();
            int topY = e.getValue();
            int bottomY = floors.getOrDefault(cell, waterY - 1);
            double patch = hash01(cell.x() >> 1, topY, cell.z() >> 1, islandSalt);
            String surfaceBlock = patch < 0.13 ? DRY : patch < 0.38 ? HEALTHY : GROWTH;
            String below = basin;
            for (int y = bottomY; y <= topY; y++) {
                String block;
                if (y == topY) {
                    block = topOver(surfaceBlock, below);
                } else if (y >= waterY && patch < 0.38) {
                    block = patch < 0.13 ? DRY : HEALTHY;
                } else {
                    block = y >= topY - 2 ? basin : SIFTSLATE;
                }
                edits.add(new Edit(cell.x(), y, cell.z(), block));
                below = block;
            }
            edits.add(new Edit(cell.x(), topY + 1, cell.z(), CLEAR));
            islandFloors.add(new int[] {cell.x(), topY, cell.z()});
        }
        Xz rootCell = new Xz(center.x(), center.z());
        if (!surface.containsKey(rootCell)) {
            rootCell = surface.keySet().stream().min(Comparator.comparingInt(c -> (c.x() - center.x()) * (c.x() - center.x())
                    + (c.z() - center.z()) * (c.z() - center.z()))).orElse(null);
        }
        int[] root = rootCell == null ? null : new int[] {rootCell.x(), surface.get(rootCell) + 1, rootCell.z()};
        return new Island(root, islandFloors);
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

    /**
     * Where the glow of a spring goes (alpha.36): the air block above its opening, so the light never holds ichor
     * (a waterlogged light is a hidden, permanent source) and never blocks the outflow, which runs sideways and
     * down. Null if that block is not free.
     */
    public static int[] springGlow(SpringWorld w, int x, int y, int z) {
        for (int[] d : OPENINGS) {
            if (d[1] == 0 && w.empty(x + d[0], y, z + d[2])) {
                return w.empty(x + d[0], y + 1, z + d[2]) ? new int[] {x + d[0], y + 1, z + d[2]} : null;
            }
        }
        return null;
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
