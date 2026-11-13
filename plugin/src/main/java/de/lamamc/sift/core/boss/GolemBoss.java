package de.lamamc.sift.core.boss;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rules of the Great Echo Golem fight (alpha.42b), without Bukkit: health scaled by the players at the start, the three
 * phases (armour, sound, rage), the soul plates and who has earned loot.
 *
 * <p>The golem keeps its own health pool (the base mob only carries the hitbox), so the numbers here are the real ones.
 * Phase 1 ends only when every plate is broken: each plate broken in melee costs the golem an equal share of the armour
 * phase's health, projectiles bounce off while plates are on. Phase 2 takes normal damage; plates its helpers bring back
 * (at most {@link Settings#rearmorPlates()}) only absorb a melee hit each. Below {@link Settings#enrageBelow()} the
 * remaining plates shatter and the golem rages.
 */
public final class GolemBoss {

    public enum Phase { ARMOR, SOUND, RAGE, DEAD }

    public enum Hit { MELEE, RANGED }

    /** phase4.bosses.great-echo-golem: health, phases and loot share. */
    public record Settings(double baseHealth, double perExtraPlayer, int maxPlayersScaled, double armorUntil, double enrageBelow,
                           int plates, int rearmorPlates, double minDamageShare) {

        public Settings {
            baseHealth = Math.max(1, baseHealth);
            perExtraPlayer = Math.max(0, perExtraPlayer);
            maxPlayersScaled = Math.max(1, maxPlayersScaled);
            armorUntil = clamp(armorUntil);
            enrageBelow = Math.min(clamp(enrageBelow), armorUntil);
            plates = Math.max(0, plates);
            rearmorPlates = Math.max(0, rearmorPlates);
            minDamageShare = clamp(minDamageShare);
        }

        public static Settings defaults() {
            // alpha.42f: 700 (was 600), +75 % per further player (was +50 %), counted up to 5 players = at most x4 (was x2.5)
            return new Settings(700, 0.75, 5, 0.6, 0.25, 12, 6, 0.05);
        }

        /** Health for this many players in the arena at the start (at least one, scaled up to maxPlayersScaled). */
        public double maxHealth(int players) {
            int counted = Math.max(1, Math.min(players, maxPlayersScaled));
            return baseHealth * (1 + perExtraPlayer * (counted - 1));
        }

        private static double clamp(double v) {
            return Math.max(0, Math.min(1, v));
        }
    }

    /**
     * How the fight grows with the players in the arena at the start (alpha.42f), besides the health in {@link Settings}:
     * damage +{@code damagePerExtraPlayer} per further player up to +{@code damageMaxBonus}; souls helpers +
     * {@code helpersPerExtraPlayer} per further player (rounded down) up to +{@code maxExtraHelpers}; the time between
     * attacks −{@code speedupPerExtraPlayer} per further player down to −{@code maxSpeedup}.
     */
    public record Scaling(double damagePerExtraPlayer, double damageMaxBonus, double helpersPerExtraPlayer, int maxExtraHelpers,
                          double speedupPerExtraPlayer, double maxSpeedup) {

        public Scaling {
            damagePerExtraPlayer = Math.max(0, damagePerExtraPlayer);
            damageMaxBonus = Math.max(0, damageMaxBonus);
            helpersPerExtraPlayer = Math.max(0, helpersPerExtraPlayer);
            maxExtraHelpers = Math.max(0, maxExtraHelpers);
            speedupPerExtraPlayer = Math.max(0, speedupPerExtraPlayer);
            maxSpeedup = Math.max(0, Math.min(0.9, maxSpeedup));
        }

        public static Scaling defaults() {
            return new Scaling(0.1, 0.4, 0.5, 2, 0.05, 0.2);
        }

        public static Scaling none() {
            return new Scaling(0, 0, 0, 0, 0, 0);
        }

        private static int extra(int players) {
            return Math.max(0, players - 1);
        }

        /** Factor on all damage of the golem. */
        public double damageFactor(int players) {
            return 1 + Math.min(damageMaxBonus, damagePerExtraPlayer * extra(players));
        }

        /** Soul helpers on top of the configured ones. */
        public int extraHelpers(int players) {
            return Math.min(maxExtraHelpers, (int) Math.floor(helpersPerExtraPlayer * extra(players) + 1e-9));
        }

        /** Factor on the time between attacks (below 1 = more often). */
        public double intervalFactor(int players) {
            return 1 - Math.min(maxSpeedup, speedupPerExtraPlayer * extra(players));
        }
    }

    /** Attack timings and numbers (ticks, hearts × 2, blocks). */
    public record Attacks(double meleeDamage, double meleeReach, int meleeCooldown, double slamDamage, double slamRadius, int slamWindup,
                          int slamInterval, int slamIntervalRage, double sonicDamage, int sonicWindup, int sonicInterval,
                          int sonicIntervalRage, double rageSpeed) {

        public static Attacks defaults() {
            // alpha.42f: damage +10 % (was 14 / 8 / 10)
            return new Attacks(15.4, 3.5, 30, 8.8, 6, 20, 240, 120, 11, 40, 200, 140, 1.3);
        }

        /**
         * The attacks of one fight (alpha.42f): damage times {@code damage}, the time between attacks (fist, stomp, sound
         * wave) times {@code interval}; wind-ups stay, so every attack can still be seen coming.
         */
        public Attacks scaled(double damage, double interval) {
            return new Attacks(meleeDamage * damage, meleeReach, ticks(meleeCooldown, interval), slamDamage * damage, slamRadius,
                    slamWindup, ticks(slamInterval, interval), ticks(slamIntervalRage, interval), sonicDamage * damage, sonicWindup,
                    ticks(sonicInterval, interval), ticks(sonicIntervalRage, interval), rageSpeed);
        }

        private static int ticks(int t, double factor) {
            return Math.max(1, (int) Math.round(t * factor));
        }

        /** Ticks between stomps in this phase. */
        public int slamInterval(Phase phase) {
            return phase == Phase.RAGE ? slamIntervalRage : slamInterval;
        }

        /** Ticks between sound waves, or -1 in the armour phase (the golem does not use it yet). */
        public int sonicInterval(Phase phase) {
            return switch (phase) {
                case ARMOR, DEAD -> -1;
                case SOUND -> sonicInterval;
                case RAGE -> sonicIntervalRage;
            };
        }

        /** Walking speed factor in this phase. */
        public double speed(Phase phase) {
            return phase == Phase.RAGE ? rageSpeed : 1.0;
        }

        /** Stomp: damage falls off linearly from the centre to {@link #slamRadius()}; none outside. */
        public double slamDamageAt(double distance) {
            if (distance > slamRadius) {
                return 0;
            }
            return slamDamage * (1 - 0.5 * distance / Math.max(0.01, slamRadius));
        }
    }

    /** What one hit did: health taken, a plate broken, bounced off, and the phase before and after. */
    public record Result(double dealt, boolean plateBroken, boolean bounced, Phase from, Phase to) {

        public boolean changedPhase() {
            return from != to;
        }
    }

    /** One running fight. */
    public static final class Fight {

        private final Settings settings;
        private final int players;
        private final double maxHealth;
        private final double plateShare;
        private final Map<UUID, Double> damage = new LinkedHashMap<>();
        private double health;
        private int plates;
        private int rearmorLeft;
        private boolean soulCall;
        private Phase phase;

        public Fight(Settings settings, int players) {
            this.settings = settings;
            this.players = Math.max(1, players);
            this.maxHealth = settings.maxHealth(players);
            this.health = maxHealth;
            this.plates = settings.plates();
            this.rearmorLeft = settings.rearmorPlates();
            this.plateShare = settings.plates() == 0 ? 0 : maxHealth * (1 - settings.armorUntil()) / settings.plates();
            this.phase = plates > 0 ? Phase.ARMOR : Phase.SOUND;
            if (phase == Phase.SOUND) {
                health = maxHealth * settings.armorUntil();
                soulCall = true;
            }
        }

        public Result hit(UUID who, Hit kind, double amount) {
            Phase before = phase;
            if (phase == Phase.DEAD) {
                return new Result(0, false, false, before, before);
            }
            double dealt = 0;
            boolean broken = false;
            boolean bounced = false;
            if (plates > 0) {
                if (kind == Hit.MELEE) {
                    plates--;
                    broken = true;
                    dealt = phase == Phase.ARMOR ? Math.min(plateShare, health) : 0;
                } else {
                    bounced = true;
                }
            } else {
                dealt = Math.max(0, Math.min(amount, health));
            }
            health -= dealt;
            if (dealt > 0 && who != null) {
                damage.merge(who, dealt, Double::sum);
            }
            advance();
            return new Result(dealt, broken, bounced, before, phase);
        }

        private void advance() {
            if (health <= 1e-9) {
                health = 0;
                plates = 0;
                phase = Phase.DEAD;
                return;
            }
            if (phase == Phase.ARMOR && plates == 0) {
                health = Math.min(health, maxHealth * settings.armorUntil());
                phase = Phase.SOUND;
            }
            if (phase == Phase.SOUND && health <= maxHealth * settings.enrageBelow()) {
                plates = 0;
                phase = Phase.RAGE;
            }
        }

        /** The soul call happens once, when the sound phase starts; true the first time it is asked for. */
        public boolean takeSoulCall() {
            if (phase == Phase.SOUND && !soulCall) {
                soulCall = true;
                return true;
            }
            return false;
        }

        /** A helper brought a soul: a new plate in the sound phase, while plates are left to give. */
        public boolean addPlate() {
            if (phase != Phase.SOUND || rearmorLeft <= 0) {
                return false;
            }
            rearmorLeft--;
            plates++;
            return true;
        }

        /** Admin or test: ends the fight at once. */
        public void kill() {
            health = 0;
            advance();
        }

        public double share(UUID who) {
            return damage.getOrDefault(who, 0.0) / maxHealth;
        }

        /** Who earns loot: at least the minimum share of the damage, or in the arena when the golem died. */
        public Set<UUID> eligible(Collection<UUID> presentAtDeath) {
            Set<UUID> out = new LinkedHashSet<>(presentAtDeath);
            damage.forEach((who, dealt) -> {
                if (dealt / maxHealth + 1e-9 >= settings.minDamageShare()) {
                    out.add(who);
                }
            });
            return out;
        }

        public Phase phase() {
            return phase;
        }

        public double health() {
            return health;
        }

        public double maxHealth() {
            return maxHealth;
        }

        public double fraction() {
            return health / maxHealth;
        }

        public int plates() {
            return plates;
        }

        public int rearmorLeft() {
            return rearmorLeft;
        }

        public int players() {
            return players;
        }

        public Map<UUID, Double> damage() {
            return Map.copyOf(damage);
        }
    }

    /**
     * The arena of a temple: a cylinder around the altar from the arena floor up to {@code height} blocks. Players in it
     * fight; damage from outside does not count, and the soul bars keep fighters in.
     */
    public record Arena(double cx, double floorY, double cz, double radius, double height) {

        public boolean inside(double x, double y, double z) {
            double dx = x - cx;
            double dz = z - cz;
            return dx * dx + dz * dz <= radius * radius && y >= floorY - 1 && y <= floorY + height;
        }

        /** Horizontal distance from the altar. */
        public double distance(double x, double z) {
            return Math.hypot(x - cx, z - cz);
        }
    }

    /**
     * The golem's experience (alpha.42h) split evenly among the players who earn loot (the remainder to the first ones),
     * each share as orbs of at most {@code orbSize} points so Mending spreads over several items. Empty without players.
     */
    public static List<List<Integer>> experienceShares(int total, int players, int orbSize) {
        List<List<Integer>> out = new java.util.ArrayList<>();
        if (players <= 0 || total <= 0) {
            return out;
        }
        int each = total / players;
        int rest = total % players;
        int size = Math.max(1, orbSize);
        for (int i = 0; i < players; i++) {
            int share = each + (i < rest ? 1 : 0);
            List<Integer> orbs = new java.util.ArrayList<>();
            while (share > 0) {
                int orb = Math.min(size, share);
                orbs.add(orb);
                share -= orb;
            }
            out.add(orbs);
        }
        return out;
    }

    private GolemBoss() {
    }
}
