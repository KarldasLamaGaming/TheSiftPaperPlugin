package de.lamamc.sift.core.siftite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Siftite kept on death (alpha.23, values of the original 1.1.1): which items, the pending list kept until
 * each item is really in the inventory again, and how the returning items fly. No Bukkit types.
 */
public final class SiftiteRecovery {

    /** The returning item moves like the original's: old velocity × 0.65 + pull × 0.38 × 0.35, capped. */
    public static final double KEEP = 0.65;
    public static final double PULL = 0.38 * 0.35;
    public static final double MAX_SPEED = 1.25;
    public static final double ORBIT_SPEED = 0.45;
    public static final double PICKUP_DISTANCE = 0.65;

    private SiftiteRecovery() {
    }

    /** Sift ids (without namespace) of the tag the_sift:siftite_items, from its JSON "values". */
    public static Set<String> idsFromTag(List<String> values) {
        Set<String> ids = new TreeSet<>();
        for (String value : values) {
            ids.add(value.startsWith("the_sift:") ? value.substring("the_sift:".length()) : value);
        }
        return ids;
    }

    /** New pending list: what is still waiting, then this death's items (order of delivery). */
    public static <T> List<T> append(List<T> pending, List<T> taken) {
        List<T> out = new ArrayList<>(pending);
        out.addAll(taken);
        return out;
    }

    /**
     * Pending list after the first entry went (partly) into the inventory: gone when nothing is left over,
     * else replaced by the rest. Never loses or doubles an entry.
     */
    public static <T> List<T> afterDelivery(List<T> pending, T leftover) {
        if (pending.isEmpty()) {
            return pending;
        }
        List<T> out = new ArrayList<>(pending.subList(1, pending.size()));
        if (leftover != null) {
            out.addFirst(leftover);
        }
        return out;
    }

    /** One step towards a target: returns {x, y, z, vx, vy, vz}. */
    public static double[] step(double[] pos, double[] vel, double[] target, double keep, double pull, double max) {
        double[] v = new double[3];
        double length = 0;
        for (int i = 0; i < 3; i++) {
            v[i] = vel[i] * keep + (target[i] - pos[i]) * pull;
            length += v[i] * v[i];
        }
        length = Math.sqrt(length);
        if (length > max) {
            for (int i = 0; i < 3; i++) {
                v[i] *= max / length;
            }
        }
        return new double[] {pos[0] + v[0], pos[1] + v[1], pos[2] + v[2], v[0], v[1], v[2]};
    }

    /** Where a waiting item circles: radius 1.65 around the player, angle tick × 0.17 (+ its place in line). */
    public static double[] orbit(double px, double py, double pz, long tick, int index, int count) {
        double angle = tick * 0.17 + (count <= 0 ? 0 : Math.PI * 2 * index / count);
        return new double[] {px + Math.cos(angle) * 1.65, py + 0.35 + Math.sin(tick * 0.11 + index) * 0.35, pz + Math.sin(angle) * 1.65};
    }

    public static boolean reached(double[] pos, double[] target) {
        double dx = pos[0] - target[0];
        double dy = pos[1] - target[1];
        double dz = pos[2] - target[2];
        return dx * dx + dy * dy + dz * dz <= PICKUP_DISTANCE * PICKUP_DISTANCE;
    }
}
