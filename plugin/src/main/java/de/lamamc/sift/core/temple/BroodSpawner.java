package de.lamamc.sift.core.temple;

import java.util.List;

/**
 * A brood spawner of a temple (phase 4, alpha.42a): wakes when players come near, sends waves of Sift mobs, is exhausted
 * after the last wave and wakes again after a cooldown (or never). Pure rules, ticked by the plugin about once a second.
 * <ul>
 * <li>WAITING: a player within the activation radius starts wave 1.</li>
 * <li>ACTIVE: when the wave is dead, the next one comes after a short delay; after the last one the spawner is exhausted.
 * When every player has left and its mobs are gone (despawned), it falls back to WAITING and starts again with wave 1.</li>
 * <li>EXHAUSTED: until the cooldown ends (0 = for good), then WAITING.</li>
 * <li>OFF: the temple's boss is defeated; stays off.</li>
 * </ul>
 */
public final class BroodSpawner {

    public enum Phase { WAITING, ACTIVE, EXHAUSTED, OFF }

    /** What a zone sends: waves, mobs per wave for one player plus per further player (scaled up to maxPlayers). */
    public record Zone(int waves, int mobsPerWave, int perExtraPlayer, int maxPlayers, List<String> mobs) {

        public Zone {
            waves = Math.max(1, waves);
            mobsPerWave = Math.max(1, mobsPerWave);
            perExtraPlayer = Math.max(0, perExtraPlayer);
            maxPlayers = Math.max(1, maxPlayers);
            mobs = List.copyOf(mobs);
        }

        public int mobs(int players) {
            return mobsPerWave + perExtraPlayer * (Math.min(Math.max(1, players), maxPlayers) - 1);
        }
    }

    /** @param cooldownMillis 0 = exhausted for good (until an admin resets the temple's spawners) */
    public record Timing(long waveDelayMillis, long cooldownMillis) {
    }

    /** readyAt: when the next wave may come (0 = not scheduled); until: end of the exhaustion (MAX = for good). */
    public record State(Phase phase, int wave, long readyAt, long until) {

        public static final State FRESH = new State(Phase.WAITING, 0, 0, 0);

        public String serialize() {
            return phase + " " + wave + " " + readyAt + " " + until;
        }

        public static State parse(String text) {
            String[] p = text.trim().split(" ");
            try {
                return new State(Phase.valueOf(p[0]), Integer.parseInt(p[1]), Long.parseLong(p[2]), Long.parseLong(p[3]));
            } catch (RuntimeException e) {
                return FRESH;
            }
        }

        /** Counts as done for the altar gate. */
        public boolean done() {
            return phase == Phase.EXHAUSTED || phase == Phase.OFF;
        }
    }

    /** The new state and how many mobs to spawn now. */
    public record Step(State state, int spawn) {
    }

    /**
     * Experience of a brood mob (alpha.42i): only when a player killed it (like vanilla), a fixed amount per kind
     * (phase4.temples.spawners.experience), else the mob's normal experience times {@code factor}. Never more for zone or
     * wave: the waves are limited and the spawner rests afterwards, so a temple is no farm.
     */
    public static int experience(String kind, int normal, boolean byPlayer, java.util.Map<String, Integer> perKind, double factor) {
        if (!byPlayer) {
            return 0;
        }
        Integer fixed = perKind.get(kind);
        return fixed != null ? Math.max(0, fixed) : (int) Math.floor(Math.max(0, normal) * Math.max(0, factor));
    }

    private BroodSpawner() {
    }

    /**
     * @param players players within the activation radius
     * @param alive   living mobs of this spawner
     */
    public static Step tick(State s, Zone zone, Timing timing, int players, int alive, long now) {
        switch (s.phase()) {
            case OFF:
                return new Step(s, 0);
            case EXHAUSTED:
                if (s.until() != Long.MAX_VALUE && now >= s.until()) {
                    return tick(State.FRESH, zone, timing, players, alive, now);
                }
                return new Step(s, 0);
            case WAITING:
                if (players > 0) {
                    return new Step(new State(Phase.ACTIVE, 1, 0, 0), zone.mobs(players));
                }
                return new Step(s, 0);
            default:
                break;
        }
        // ACTIVE
        if (alive > 0) {
            return new Step(s.readyAt() == 0 ? s : new State(Phase.ACTIVE, s.wave(), 0, 0), 0);
        }
        if (players == 0) {
            return new Step(State.FRESH, 0); // everyone left and the brood is gone: start over next time
        }
        if (s.wave() >= zone.waves()) {
            long until = timing.cooldownMillis() <= 0 ? Long.MAX_VALUE : now + timing.cooldownMillis();
            return new Step(new State(Phase.EXHAUSTED, s.wave(), 0, until), 0);
        }
        if (s.readyAt() == 0) {
            return new Step(new State(Phase.ACTIVE, s.wave(), now + timing.waveDelayMillis(), 0), 0);
        }
        if (now >= s.readyAt()) {
            return new Step(new State(Phase.ACTIVE, s.wave() + 1, 0, 0), zone.mobs(players));
        }
        return new Step(s, 0);
    }
}
