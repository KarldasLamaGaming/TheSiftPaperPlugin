package de.lamamc.sift.core.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Charoite Wisp (alpha.42d, our own mob for zone 3 of the temple brood spawners): a flying ranged mob that keeps its
 * distance, shoots slow, slightly homing charoite shards and blinks away when hit. Pure numbers and geometry; the plugin
 * moves the invisible base mob and the shard displays.
 *
 * @param aggroRange        players within this many blocks are targets
 * @param preferredDistance it hovers this far from its target (horizontally)
 * @param minDistance       closer than this it backs off
 * @param hoverHeight       blocks above the target's feet
 * @param shootRange        it only shoots within this distance and with line of sight
 * @param shootCooldown     ticks between two shards
 * @param shardSpeed        blocks per tick
 * @param shardTurn         degrees a shard may turn towards its target per tick (homing)
 * @param shardLifetime     ticks before a shard fades
 * @param shardDamage       damage of a hit
 * @param shardHitRadius    a shard hits what comes this close to its path
 * @param blinkDistance     blocks it blinks away when hit
 * @param blinkCooldown     ticks between two blinks
 */
public record CharoiteWispRules(double aggroRange, double preferredDistance, double minDistance, double hoverHeight, double shootRange,
                                int shootCooldown, double shardSpeed, double shardTurn, int shardLifetime, double shardDamage,
                                double shardHitRadius, double blinkDistance, int blinkCooldown) {

    public CharoiteWispRules {
        minDistance = Math.max(0, Math.min(minDistance, preferredDistance));
        shootCooldown = Math.max(1, shootCooldown);
        shardLifetime = Math.max(1, shardLifetime);
        blinkCooldown = Math.max(0, blinkCooldown);
    }

    public static CharoiteWispRules defaults() {
        return new CharoiteWispRules(24, 10, 7, 3, 18, 40, 0.45, 4, 80, 5, 0.45, 4, 120);
    }

    /**
     * Where to hover: {@code preferredDistance} from the target on the side the wisp is on, turned by {@code strafe}
     * degrees around the target (it circles slowly), {@code hoverHeight} above the target's feet.
     */
    public double[] desired(double[] self, double[] target, double strafe) {
        double dx = self[0] - target[0];
        double dz = self[2] - target[2];
        double length = Math.hypot(dx, dz);
        if (length < 1e-6) {
            dx = 1;
            dz = 0;
            length = 1;
        }
        double angle = Math.atan2(dz, dx) + Math.toRadians(strafe);
        return new double[] {target[0] + Math.cos(angle) * preferredDistance, target[1] + hoverHeight,
            target[2] + Math.sin(angle) * preferredDistance};
    }

    /** Too close: it backs off (moves to {@link #desired}) before anything else. */
    public boolean tooClose(double distanceSquared) {
        return distanceSquared < minDistance * minDistance;
    }

    public boolean canShoot(int cooldown, double distanceSquared, boolean lineOfSight) {
        return cooldown <= 0 && lineOfSight && distanceSquared <= shootRange * shootRange;
    }

    /** The new unit direction of a shard: towards the target, turned by at most {@link #shardTurn} degrees. */
    public double[] steer(double[] direction, double[] toTarget) {
        double[] a = normalize(direction);
        double[] b = normalize(toTarget);
        if (b == null) {
            return a;
        }
        if (a == null) {
            return b;
        }
        double dot = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
        double angle = Math.acos(dot);
        double max = Math.toRadians(shardTurn);
        if (angle <= max || angle < 1e-9) {
            return b;
        }
        // slerp from a towards b by max
        double t = max / angle;
        double sin = Math.sin(angle);
        double wa = Math.sin((1 - t) * angle) / sin;
        double wb = Math.sin(t * angle) / sin;
        return normalize(new double[] {a[0] * wa + b[0] * wb, a[1] * wa + b[1] * wb, a[2] * wa + b[2] * wb});
    }

    /** Candidate offsets for a blink, in random horizontal directions, a little up or down; tried in this order. */
    public List<double[]> blinkOffsets(RandomGenerator random, int tries) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double up = random.nextDouble() * 2 - 0.5;
            out.add(new double[] {Math.cos(angle) * blinkDistance, up, Math.sin(angle) * blinkDistance});
        }
        return out;
    }

    static double[] normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return length < 1e-9 ? null : new double[] {v[0] / length, v[1] / length, v[2] / length};
    }
}
