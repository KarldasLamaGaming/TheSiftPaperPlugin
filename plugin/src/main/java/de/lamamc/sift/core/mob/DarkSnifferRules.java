package de.lamamc.sift.core.mob;

/**
 * Combat numbers of the Dark Sniffer (DarkSnifferEntity of the original 1.1.1). Melee within
 * {@code reach} blocks every {@code attackCooldown} ticks. When the target stayed out of reach for
 * {@code outOfReachTicks}, is at least {@code sonicMinDistance} away and in sight, the sniffer stops,
 * sniffs for {@code sonicWindup} ticks and sends a sound wave ({@code waveSpeed} blocks per tick, aimed
 * ahead of a moving target, flying {@code overshoot} blocks past it). The wave does no damage: everything it
 * touches (within {@code hitRadius}) is pulled once towards the sniffer.
 */
public record DarkSnifferRules(double aggroRange, double reach, int attackCooldown, int sonicWindup, int sonicCooldown,
                               int outOfReachTicks, double sonicMinDistance, double waveSpeed, double hitRadius,
                               double overshoot, double maxLeadTicks, double pullStrength) {

    public static DarkSnifferRules defaults() {
        return new DarkSnifferRules(32, 3.15, 24, 10, 100, 8, 5.0, 1.5, 0.8, 7.5, 20.0, 0.3);
    }

    public boolean inReach(double distanceSquared) {
        return distanceSquared <= reach * reach;
    }

    public boolean sonicReady(int cooldown, int ticksOutOfReach, double distanceSquared, boolean lineOfSight) {
        return cooldown <= 0 && ticksOutOfReach >= outOfReachTicks && distanceSquared >= sonicMinDistance * sonicMinDistance
                && lineOfSight;
    }

    /** Target choice: the nearer of the nearest player and the nearest ordinary sniffer (squared distances, -1 = none). */
    public static int chooseTarget(double playerDistanceSquared, double snifferDistanceSquared) {
        if (snifferDistanceSquared >= 0 && (playerDistanceSquared < 0 || snifferDistanceSquared < playerDistanceSquared)) {
            return 2;
        }
        return playerDistanceSquared >= 0 ? 1 : 0;
    }

    /** A launched wave: unit direction and how many ticks it flies. */
    public record Wave(double[] origin, double[] direction, int flightTicks) {

        public double[] position(int age, double speed) {
            return new double[] {origin[0] + direction[0] * speed * age, origin[1] + direction[1] * speed * age,
                origin[2] + direction[2] * speed * age};
        }
    }

    /** Aims at where the target will be (horizontal velocity × lead time), or null when it is too close. */
    public Wave aim(double[] start, double[] target, double[] targetVelocity) {
        double dx = target[0] - start[0];
        double dy = target[1] - start[1];
        double dz = target[2] - start[2];
        double direct = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (direct < 0.001) {
            return null;
        }
        double lead = Math.min(maxLeadTicks, direct / waveSpeed);
        double px = target[0] + targetVelocity[0] * lead - start[0];
        double py = dy;
        double pz = target[2] + targetVelocity[2] * lead - start[2];
        double predicted = Math.sqrt(px * px + py * py + pz * pz);
        if (predicted < 0.001) {
            px = dx;
            pz = dz;
            predicted = direct;
        }
        double[] direction = {px / predicted, py / predicted, pz / predicted};
        int flight = Math.max(1, (int) Math.ceil((predicted + overshoot) / waveSpeed));
        return new Wave(start.clone(), direction, flight);
    }

    /** Whether the segment p0 → p1 passes through the box (min x/y/z, max x/y/z) widened by the hit radius. */
    public boolean hits(double[] box, double[] p0, double[] p1) {
        double[] min = {box[0] - hitRadius, box[1] - hitRadius, box[2] - hitRadius};
        double[] max = {box[3] + hitRadius, box[4] + hitRadius, box[5] + hitRadius};
        double t0 = 0;
        double t1 = 1;
        for (int axis = 0; axis < 3; axis++) {
            double d = p1[axis] - p0[axis];
            if (Math.abs(d) < 1e-12) {
                if (p0[axis] < min[axis] || p0[axis] > max[axis]) {
                    return false;
                }
                continue;
            }
            double a = (min[axis] - p0[axis]) / d;
            double b = (max[axis] - p0[axis]) / d;
            t0 = Math.max(t0, Math.min(a, b));
            t1 = Math.min(t1, Math.max(a, b));
            if (t0 > t1) {
                return false;
            }
        }
        return true;
    }

    /** Push for a victim hit by the wave: towards the sniffer, scaled by the pull strength. */
    public double[] pull(double[] sniffer, double[] victim) {
        return new double[] {(sniffer[0] - victim[0]) * pullStrength, (sniffer[1] - victim[1]) * pullStrength,
            (sniffer[2] - victim[2]) * pullStrength};
    }
}
