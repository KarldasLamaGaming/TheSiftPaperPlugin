package de.lamamc.sift.core.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The shape of a soul canyon (SoulCanyonFeature of the original, version 1.1.1): a meandering, diagonal
 * trench whose floor ramps down from the entrance, with soul blocks set into the floor of its deep part as
 * a winding vein and two pockets. Pure geometry; the terrain is asked through {@link Terrain}.
 */
public final class SoulCanyonPlan {

    /** The terrain a plan is laid on. */
    public interface Terrain {
        /** Topmost landmark ground within 32 blocks below the surface, or Integer.MIN_VALUE (SiftLandmarkTerrain). */
        int surfaceY(int x, int z);

        /** Inside the area the canyon may use (the 3 × 3 chunks around its chunk). */
        boolean contains(int x, int z);

        int minY();
    }

    /** Whether all blocks of the columns may be carved (floor − 2 up to the headroom). */
    public interface CarveCheck {
        boolean canCarve(Collection<Column> columns);
    }

    public record Pos(int x, int y, int z) {
    }

    /** One column of the canyon: its terrain surface and its new floor (air from floor + 1 up). */
    public record Column(int x, int z, int surfaceY, int floorY, int depth, double progress, int alongStep, int sideDistance,
                         double signedSide) {

        /** Highest y that becomes air. */
        public int topY() {
            return Math.max(surfaceY + 3, floorY + 3);
        }

        Column withFloor(int floor) {
            return new Column(x, z, surfaceY, floor, surfaceY - floor, progress, alongStep, sideDistance, signedSide);
        }
    }

    /** Random ranges and limits of the original. */
    public record Settings(int lengthMin, int lengthSpread, int baseRadius, int depthMin, int depthSpread, int attempts,
                           int minSouls) {

        public static Settings defaults() {
            return new Settings(45, 3, 4, 18, 3, 8, 8);
        }

        public Settings withMinSouls(int souls) {
            return new Settings(lengthMin, lengthSpread, baseRadius, depthMin, depthSpread, attempts, souls);
        }
    }

    /** A canyon that can be built. */
    public record Result(Map<Long, Column> columns, List<Pos> souls, Pos entrance, Pos midpoint, Pos cluster, int length,
                         int attempt) {
    }

    private record Shape(Map<Long, Column> columns, Pos entrance, Pos midpoint, int length, int baseRadius) {
    }

    private static final double DIAGONAL = 0.7071067811865476;
    private static final double PHASE_STEP = 1.941611038725466;

    private SoulCanyonPlan() {
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * Tries up to {@code attempts} orientations around the chunk centre; the first shape that fits the terrain,
     * can be carved and holds at least {@code minSouls} soul blocks wins. Null when none does.
     */
    public static Result plan(Terrain terrain, CarveCheck carve, int centerX, int centerZ, RandomGenerator random, Settings settings) {
        int length = settings.lengthMin() + random.nextInt(Math.max(1, settings.lengthSpread()));
        int baseRadius = settings.baseRadius();
        int maximumDepth = settings.depthMin() + random.nextInt(Math.max(1, settings.depthSpread()));
        double phase = random.nextDouble() * Math.PI * 2.0;
        int firstOrientation = random.nextInt(4);
        for (int attempt = 0; attempt < settings.attempts(); attempt++) {
            int orientation = firstOrientation + attempt & 3;
            double candidatePhase = phase + attempt / 4 * PHASE_STEP;
            Shape shape = shape(terrain, centerX, centerZ, length, baseRadius, maximumDepth, candidatePhase, orientation);
            if (shape != null && carve.canCarve(shape.columns().values())) {
                List<Pos> souls = selectSoulVeins(shape, candidatePhase, maximumDepth);
                if (souls.size() >= settings.minSouls()) {
                    return new Result(shape.columns(), souls, shape.entrance(), shape.midpoint(), clusterCenter(souls), length,
                            attempt);
                }
            }
        }
        return null;
    }

    private static Shape shape(Terrain terrain, int centerX, int centerZ, int length, int baseRadius, int maximumDepth,
                               double phase, int orientation) {
        double directionX = (orientation & 1) == 0 ? DIAGONAL : -DIAGONAL;
        double directionZ = (orientation & 2) == 0 ? DIAGONAL : -DIAGONAL;
        double sideX = -directionZ;
        double sideZ = directionX;
        int halfLength = length / 2;
        Map<Long, Column> columns = new HashMap<>();
        Set<Long> route = new HashSet<>();
        Pos entrance = null;
        Pos midpoint = null;
        int previousFloor = Integer.MIN_VALUE;
        int previousSurface = Integer.MIN_VALUE;
        int midpointStep = Math.round((length - 1) * 0.38F);

        for (int step = 0; step < length; step++) {
            int offset = step - halfLength;
            double meander = Math.sin(phase + step * 0.22) * 1.65 + Math.sin(phase * 1.73 + step * 0.071) * 0.7;
            int radius = Math.max(3, baseRadius + (int) Math.round(Math.sin(phase * 0.63 + step * 0.39)));
            double exactX = centerX + directionX * offset + sideX * meander;
            double exactZ = centerZ + directionZ * offset + sideZ * meander;
            int stepX = (int) Math.round(exactX);
            int stepZ = (int) Math.round(exactZ);
            if (!terrain.contains(stepX, stepZ)) {
                return null;
            }
            int centerSurface = terrain.surfaceY(stepX, stepZ);
            if (centerSurface == Integer.MIN_VALUE) {
                return null;
            }
            if (previousSurface != Integer.MIN_VALUE && Math.abs(centerSurface - previousSurface) > 8) {
                return null;
            }
            previousSurface = centerSurface;
            double progress = (double) step / Math.max(1, length - 1);
            int rampDepth = Math.min(maximumDepth, (int) Math.round(step * 0.6));
            double farTaper = smootherStep(clamp01((1.0 - progress) / 0.34));
            int centerFloorDepth = (int) Math.round(rampDepth * farTaper);
            int desired = centerSurface - centerFloorDepth;
            int floor = previousFloor == Integer.MIN_VALUE ? desired
                    : Math.max(previousFloor - 1, Math.min(previousFloor + 1, desired));
            if (centerSurface + 2 < floor || centerSurface - floor > maximumDepth + 14) {
                return null;
            }
            previousFloor = floor;
            Pos feet = new Pos(stepX, floor + 1, stepZ);
            if (step == 0) {
                entrance = feet;
            }
            if (step == midpointStep) {
                midpoint = feet;
            }
            int raster = radius + 2;
            int minX = (int) Math.floor(exactX) - raster;
            int maxX = (int) Math.ceil(exactX) + raster;
            int minZ = (int) Math.floor(exactZ) - raster;
            int maxZ = (int) Math.ceil(exactZ) + raster;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double relX = x - exactX;
                    double relZ = z - exactZ;
                    double along = Math.abs(relX * directionX + relZ * directionZ);
                    double signedSide = relX * sideX + relZ * sideZ;
                    double side = Math.abs(signedSide);
                    if (along > 0.82 || side > radius + 0.35) {
                        continue;
                    }
                    if (!terrain.contains(x, z)) {
                        return null;
                    }
                    double sideRatio = side / (radius + 0.35);
                    double arch = Math.sqrt(Math.max(0.0, 1.0 - sideRatio * sideRatio));
                    int surfaceY = terrain.surfaceY(x, z);
                    if (surfaceY == Integer.MIN_VALUE) {
                        return null;
                    }
                    boolean corridor = side <= 2.25;
                    if (corridor && (surfaceY + 2 < floor || Math.abs(surfaceY - centerSurface) > 9)) {
                        return null;
                    }
                    int depth = Math.max(1, (int) Math.round((surfaceY - floor) * arch));
                    if (side > radius - 0.35 && hashUnit(x, z, maximumDepth) < 0.18) {
                        continue; // ragged edge
                    }
                    int floorY = corridor ? floor : surfaceY - depth;
                    if (floorY <= terrain.minY() + 3) {
                        return null;
                    }
                    Column column = new Column(x, z, surfaceY, floorY, surfaceY - floorY, progress, step,
                            (int) Math.round(side), signedSide);
                    long key = key(x, z);
                    if (corridor) {
                        route.add(key);
                    }
                    Column previous = columns.get(key);
                    if (previous == null || column.floorY() < previous.floorY()) {
                        columns.put(key, column);
                    }
                }
            }
        }
        if (!smoothRoute(columns, route)) {
            return null;
        }
        Pos verifiedEntrance = routeFeet(columns, entrance);
        Pos verifiedMidpoint = routeFeet(columns, midpoint);
        return verifiedEntrance == null || verifiedMidpoint == null ? null
                : new Shape(columns, verifiedEntrance, verifiedMidpoint, length, baseRadius);
    }

    /** No route column may lie more than one block below a neighbouring route column (walkable ramp). */
    private static boolean smoothRoute(Map<Long, Column> columns, Set<Long> route) {
        ArrayDeque<Long> queue = new ArrayDeque<>(route);
        while (!queue.isEmpty()) {
            Column current = columns.get(queue.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    long key = key(current.x() + dx, current.z() + dz);
                    if (!route.contains(key)) {
                        continue;
                    }
                    Column next = columns.get(key);
                    int floor = current.floorY() - 1;
                    if (next.floorY() < floor) {
                        if (floor > next.surfaceY() + 2) {
                            return false;
                        }
                        columns.put(key, next.withFloor(floor));
                        queue.addLast(key);
                    }
                }
            }
        }
        return true;
    }

    private static Pos routeFeet(Map<Long, Column> columns, Pos marker) {
        if (marker == null) {
            return null;
        }
        Column column = columns.get(key(marker.x(), marker.z()));
        return column == null ? null : new Pos(marker.x(), column.floorY() + 1, marker.z());
    }

    private static List<Pos> selectSoulVeins(Shape shape, double phase, int maximumDepth) {
        List<Pos> result = new ArrayList<>();
        Set<Long> reachable = reachableFloor(shape);
        if (!reachable.contains(key(shape.midpoint().x(), shape.midpoint().z()))) {
            return result;
        }
        for (Column column : shape.columns().values()) {
            if (column.depth() < maximumDepth * 0.5 || column.floorY() > shape.entrance().y() - 5
                    || column.progress() < 0.53 || column.progress() > 0.86 || column.sideDistance() > shape.baseRadius()
                    || !reachable.contains(key(column.x(), column.z())) || !hasGolemSizedApproach(shape, column)) {
                continue;
            }
            double along = column.alongStep();
            double veinCenter = Math.sin(phase + along * 0.31) * 1.15 + Math.sin(phase * 0.71 + along * 0.13) * 0.48;
            double width = 0.82 + (Math.sin(phase * 1.37 + along * 0.43) + 1.0) * 0.24;
            double edgeRatio = Math.abs(column.signedSide() - veinCenter) / width;
            double grain = hashUnit(column.x(), column.z(), maximumDepth * 31);
            boolean vein = edgeRatio <= 1.0 && (edgeRatio < 0.48 || grain > 0.2 + edgeRatio * 0.42);
            double pocketOne = square((column.progress() - 0.63) / 0.055)
                    + square((column.signedSide() + 1.65 + Math.sin(phase) * 0.55) / 1.55);
            double pocketTwo = square((column.progress() - 0.77) / 0.048)
                    + square((column.signedSide() - 1.35 - Math.cos(phase) * 0.45) / 1.35);
            boolean pocket = Math.min(pocketOne, pocketTwo) <= 1.0 && grain > 0.16;
            if (vein || pocket) {
                result.add(new Pos(column.x(), column.floorY(), column.z()));
            }
        }
        result.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        return result;
    }

    /** Floor columns a 2 × 2 walker reaches from the entrance with steps of at most one block. */
    private static Set<Long> reachableFloor(Shape shape) {
        Set<Long> visited = new HashSet<>();
        Set<Long> reachable = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        for (int x = shape.entrance().x() - 1; x <= shape.entrance().x(); x++) {
            for (int z = shape.entrance().z() - 1; z <= shape.entrance().z(); z++) {
                int floor = padFloor(shape, x, z);
                if (floor != Integer.MIN_VALUE) {
                    visited.add(key(x, z));
                    queue.add(new Pos(x, floor, z));
                }
            }
        }
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (!queue.isEmpty()) {
            Pos current = queue.removeFirst();
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    reachable.add(key(current.x() + dx, current.z() + dz));
                }
            }
            for (int[] d : directions) {
                int x = current.x() + d[0];
                int z = current.z() + d[1];
                long key = key(x, z);
                if (!visited.contains(key)) {
                    int floor = padFloor(shape, x, z);
                    if (floor != Integer.MIN_VALUE && Math.abs(floor - current.y()) <= 1) {
                        visited.add(key);
                        queue.addLast(new Pos(x, floor, z));
                    }
                }
            }
        }
        return reachable;
    }

    /** Highest floor of the 2 × 2 pad at x/z, if its floors differ by at most one block. */
    private static int padFloor(Shape shape, int x, int z) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                Column column = shape.columns().get(key(x + dx, z + dz));
                if (column == null) {
                    return Integer.MIN_VALUE;
                }
                lowest = Math.min(lowest, column.floorY());
                highest = Math.max(highest, column.floorY());
            }
        }
        return highest - lowest <= 1 ? highest : Integer.MIN_VALUE;
    }

    /** A 2 × 2 pad with the soul column whose floors are at the soul's height or one below (room for the golem). */
    private static boolean hasGolemSizedApproach(Shape shape, Column target) {
        for (int startX : new int[] {target.x() - 1, target.x()}) {
            for (int startZ : new int[] {target.z() - 1, target.z()}) {
                boolean valid = true;
                boolean hasStep = false;
                for (int dx = 0; dx <= 1 && valid; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        Column column = shape.columns().get(key(startX + dx, startZ + dz));
                        if (column == null || column.floorY() > target.floorY() || column.floorY() < target.floorY() - 1) {
                            valid = false;
                            break;
                        }
                        if (column.x() != target.x() || column.z() != target.z()) {
                            hasStep = true;
                        }
                    }
                }
                if (valid && hasStep) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Pos clusterCenter(List<Pos> positions) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (Pos pos : positions) {
            x += pos.x();
            y += pos.y();
            z += pos.z();
        }
        int size = positions.size();
        return new Pos((int) (x / size), (int) (y / size), (int) (z / size));
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double smootherStep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    /** Deterministic noise in [0, 1) per column (float precision like the original). */
    public static double hashUnit(int x, int z, int salt) {
        long value = x * -7046029254386353131L ^ z * -4417276706812531889L ^ salt * -7723592293110705685L;
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        return ((value ^ value >>> 31) >>> 11) * 1.110223E-16F;
    }
}
