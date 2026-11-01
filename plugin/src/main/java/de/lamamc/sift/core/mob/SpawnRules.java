package de.lamamc.sift.core.mob;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Player-centred spawning like SiftSifterSpawner of the original: every {@code interval} ticks per player up
 * to {@code attempts} points on a golden-angle spiral in a ring of {@code minDistance} … {@code maxDistance}
 * blocks, each on the surface with {@code surfaceChance} (otherwise in a cave). The first point whose biome
 * profile passes its chance spawns a group of {@code minGroup} … {@code maxGroup} mobs within
 * {@code groupSpread} blocks, but only as many as the profile's cap leaves room for (counted within
 * {@code capRadius} of the point); without room for the smallest group nothing spawns.
 */
public record SpawnRules(int interval, int minDistance, int maxDistance, double surfaceChance, int capRadius,
                         int minGroup, int maxGroup, List<Profile> profiles, int attempts, int groupSpread) {

    /** Golden angle in radians (the original's spiral step). */
    public static final double GOLDEN_ANGLE = 2.399963229728653;

    /** One attempt per check, groups within 3 blocks. */
    public SpawnRules(int interval, int minDistance, int maxDistance, double surfaceChance, int capRadius,
                      int minGroup, int maxGroup, List<Profile> profiles) {
        this(interval, minDistance, maxDistance, surfaceChance, capRadius, minGroup, maxGroup, profiles, 1, 3);
    }

    /** {@code biomes} empty = every biome (a fallback profile at the end). */
    public record Profile(List<String> biomes, double chance, int cap) {

        public boolean matches(String biome) {
            return biomes.isEmpty() || biomes.contains(biome);
        }
    }

    public record Offset(int dx, int dz, boolean surface) {
    }

    public SpawnRules {
        if (minDistance < 0 || maxDistance < minDistance) {
            throw new IllegalArgumentException("distance " + minDistance + " … " + maxDistance);
        }
        if (minGroup < 1 || maxGroup < minGroup) {
            throw new IllegalArgumentException("group " + minGroup + " … " + maxGroup);
        }
        if (attempts < 1 || groupSpread < 0) {
            throw new IllegalArgumentException("attempts " + attempts + ", group spread " + groupSpread);
        }
        profiles = List.copyOf(profiles);
    }

    /** First matching profile, or null when the mob does not spawn in this biome. */
    public Profile profile(String biome) {
        for (Profile profile : profiles) {
            if (profile.matches(biome)) {
                return profile;
            }
        }
        return null;
    }

    public boolean due(long tick, int playerSalt) {
        return interval > 0 && Math.floorMod(tick + playerSalt, interval) == 0;
    }

    /** Whether an attempt in this biome may spawn, given the mobs of this kind near the point. */
    public boolean rollAttempt(String biome, int nearby, RandomGenerator random) {
        Profile profile = profile(biome);
        return profile != null && random.nextDouble() < profile.chance() && groupRoom(profile, nearby) >= minGroup;
    }

    /** Room under the cap of a profile. */
    public int groupRoom(Profile profile, int nearby) {
        return profile.cap() - nearby;
    }

    /** Group size like the original: 0 without room for the smallest group, else min … min(max, room). */
    public int groupSize(String biome, int nearby, RandomGenerator random) {
        Profile profile = profile(biome);
        if (profile == null) {
            return 0;
        }
        int room = groupRoom(profile, nearby);
        if (room < minGroup) {
            return 0;
        }
        int max = Math.min(maxGroup, room);
        return minGroup + random.nextInt(max - minGroup + 1);
    }

    /** Spawn point relative to the player: uniform angle, distance in the ring. */
    public Offset offset(RandomGenerator random) {
        return offset(random.nextDouble() * Math.PI * 2, 0, random);
    }

    /** Point {@code attempt} of the spiral starting at {@code phase}: whole-block radius in the ring. */
    public Offset offset(double phase, int attempt, RandomGenerator random) {
        double angle = phase + attempt * GOLDEN_ANGLE;
        int radius = minDistance + random.nextInt(maxDistance - minDistance + 1);
        return new Offset((int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius),
                random.nextDouble() < surfaceChance);
    }
}
