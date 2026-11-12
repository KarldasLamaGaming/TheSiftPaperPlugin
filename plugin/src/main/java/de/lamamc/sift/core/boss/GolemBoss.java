package de.lamamc.sift.core.boss;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
            return new Settings(600, 0.5, 4, 0.6, 0.25, 12, 6, 0.05);
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

    /** Attack timings and numbers (ticks, hearts × 2, blocks). */
    public record Attacks(double meleeDamage, double meleeReach, int meleeCooldown, double slamDamage, double slamRadius, int slamWindup,
                          int slamInterval, int slamIntervalRage, double sonicDamage, int sonicWindup, int sonicInterval,
                          int sonicIntervalRage, double rageSpeed) {

        public static Attacks defaults() {
            return new Attacks(14, 3.5, 30, 8, 6, 20, 240, 120, 10, 40, 200, 140, 1.3);
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

    private GolemBoss() {
    }
}
