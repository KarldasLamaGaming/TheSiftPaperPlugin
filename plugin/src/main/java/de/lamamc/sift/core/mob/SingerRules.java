package de.lamamc.sift.core.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Timing and targeting of the Singer's song (SingerEntity of the original 1.1.1). A called Singer appears,
 * sings and goes away again (each phase as long as its animation). While singing it sends a sound wave
 * to up to 8 sleeping consoles at fixed moments of the song; a wave travels {@code waveSpeed} blocks per
 * tick from its head and wakes the console when it arrives.
 */
public record SingerRules(int appearTicks, int singTicks, int disappearTicks, int[] waveTicks, double waveSpeed, double headHeight) {

    /** Seconds into the song at which the original sends its 8 waves. */
    public static final double[] WAVE_SECONDS = {1 / 6.0, 1.25, 2.0833333333333335, 2.8333333333333335, 3.75,
        4.583333333333333, 4.916666666666667, 5.833333333333333};

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.ceil(Math.max(0.05, seconds) * 20.0));
    }

    public static int[] defaultWaveTicks() {
        int[] ticks = new int[WAVE_SECONDS.length];
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = (int) Math.round(WAVE_SECONDS[i] * 20.0);
        }
        return ticks;
    }

    public enum Phase {
        APPEAR, SING, DISAPPEAR, GONE
    }

    public Phase phase(int tick) {
        if (tick < appearTicks) {
            return Phase.APPEAR;
        }
        if (tick < appearTicks + singTicks) {
            return Phase.SING;
        }
        return tick < appearTicks + singTicks + disappearTicks - 1 ? Phase.DISAPPEAR : Phase.GONE;
    }

    /** Index of the wave that starts at this tick of the sequence, or -1. */
    public int waveAt(int tick) {
        int sing = tick - appearTicks;
        for (int i = 0; i < waveTicks.length; i++) {
            if (waveTicks[i] == sing) {
                return i;
            }
        }
        return -1;
    }

    public int travelTicks(double distance) {
        return Math.max(1, (int) Math.ceil(distance / waveSpeed));
    }

    /**
     * Order of the consoles like the original: sorted from the Singer's right to its left, at most 8; the
     * right half from far to near, then the left half from near to far.
     */
    public static List<double[]> order(List<double[]> consoles, double[] singer, double yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        List<double[]> sorted = new ArrayList<>(consoles);
        sorted.sort(Comparator.comparingDouble((double[] c) -> (c[0] - singer[0]) * rx + (c[2] - singer[2]) * rz).reversed());
        if (sorted.size() > 8) {
            sorted = new ArrayList<>(sorted.subList(0, 8));
        }
        int split = (sorted.size() + 1) / 2;
        List<double[]> first = new ArrayList<>(sorted.subList(0, split));
        List<double[]> second = new ArrayList<>(sorted.subList(split, sorted.size()));
        Comparator<double[]> byDistance = Comparator.comparingDouble(c -> distanceSquared(c, singer));
        first.sort(byDistance.reversed());
        second.sort(byDistance);
        List<double[]> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static double distanceSquared(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** Yaw (Minecraft degrees) of the cardinal direction from the Singer towards a block, like getCardinalDirection. */
    public static float cardinalYaw(double[] from, double[] to) {
        if (to == null) {
            return 0f; // south
        }
        double dx = to[0] - from[0];
        double dz = to[2] - from[2];
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? 270f : 90f; // east : west
        }
        return dz >= 0 ? 0f : 180f; // south : north
    }

    /** Whether every point of a path has at least {@code height} free blocks above its floor. */
    public static boolean headroomOk(int[] freeAbove, int height) {
        for (int free : freeAbove) {
            if (free < height) {
                return false;
            }
        }
        return true;
    }
}
