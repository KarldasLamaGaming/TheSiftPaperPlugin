package de.lamamc.sift.core.mob;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Player-centred spawning like the Sifter spawner of the original: every {@code interval} ticks per player
 * one attempt in a ring of {@code minDistance} … {@code maxDistance} blocks, on the surface with
 * {@code surfaceChance} (otherwise in a cave), with the chance and cap of the first profile whose biomes
 * match, and a group of {@code minGroup} … {@code maxGroup} mobs.
 */
public record SpawnRules(int interval, int minDistance, int maxDistance, double surfaceChance, int capRadius,
                         int minGroup, int maxGroup, List<Profile> profiles) {

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

    /** Whether an attempt in this biome may spawn, given the mobs of this kind near the player. */
    public boolean rollAttempt(String biome, int nearby, RandomGenerator random) {
        Profile profile = profile(biome);
        return profile != null && nearby < profile.cap() && random.nextDouble() < profile.chance();
    }

    /** Group size, never more than the room left under the cap. */
    public int groupSize(String biome, int nearby, RandomGenerator random) {
        Profile profile = profile(biome);
        if (profile == null) {
            return 0;
        }
        int size = minGroup + random.nextInt(maxGroup - minGroup + 1);
        return Math.max(0, Math.min(size, profile.cap() - nearby));
    }

    /** Spawn point relative to the player: uniform angle, distance in the ring. */
    public Offset offset(RandomGenerator random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
        return new Offset((int) Math.round(Math.cos(angle) * distance), (int) Math.round(Math.sin(angle) * distance),
                random.nextDouble() < surfaceChance);
    }
}
