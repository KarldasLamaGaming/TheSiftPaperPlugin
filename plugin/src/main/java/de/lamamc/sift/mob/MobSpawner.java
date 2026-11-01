package de.lamamc.sift.mob;

import de.lamamc.sift.core.mob.SpawnRules;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The player-centred spawner of the original, for every mob with spawn rules: in the Sift, every
 * {@code interval} ticks per player one attempt at a random point of the ring around the player, only in
 * loaded chunks, never closer than the minimum distance to any player, and within the limits of
 * {@link MobManager} (per player area, per world).
 */
public final class MobSpawner {

    private final MobManager mobs;
    private long tick;
    /** Spawned per kind since the start (/sift debug mobs). */
    final java.util.Map<String, Integer> spawned = new java.util.TreeMap<>();

    public MobSpawner(MobManager mobs) {
        this.mobs = mobs;
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    public java.util.Map<String, Integer> spawned() {
        return spawned;
    }

    private void tick() {
        tick++;
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null || sift.getPlayers().isEmpty()) {
            return;
        }
        for (MobKind kind : mobs.kinds().values()) {
            SpawnRules rules = kind.enabled() ? kind.spawnRules() : null;
            if (rules == null || kind.hostile() && sift.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                continue;
            }
            for (Player player : sift.getPlayers()) {
                if (player.getGameMode() != GameMode.SPECTATOR && rules.due(tick, player.getUniqueId().hashCode())) {
                    attempt(kind, rules, player, ThreadLocalRandom.current());
                }
            }
        }
    }

    private void attempt(MobKind kind, SpawnRules rules, Player player, RandomGenerator random) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (mobs.countNear(center, mobs.playerRadius(), null) >= mobs.perPlayer() || mobs.count(world) >= mobs.worldLimit()) {
            return;
        }
        double phase = random.nextDouble() * Math.PI * 2;
        for (int attempt = 0; attempt < rules.attempts(); attempt++) {
            SpawnRules.Offset offset = rules.offset(phase, attempt, random);
            int x = center.getBlockX() + offset.dx();
            int z = center.getBlockZ() + offset.dz();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Location spot = feet(kind, world, x, z, offset.surface(), random);
            if (spot == null || tooCloseToPlayers(spot, rules.minDistance())) {
                continue;
            }
            String biome = biome(spot);
            SpawnRules.Profile profile = rules.profile(biome);
            if (profile == null || random.nextDouble() >= profile.chance()) {
                continue; // like the original: the next point of the spiral gets its chance
            }
            int group = rules.groupSize(biome, mobs.countNear(spot, rules.capRadius(), kind.id()), random);
            if (group == 0) {
                return; // cap reached here
            }
            spawnGroup(kind, rules, profile, spot, group, center, random);
            return;
        }
    }

    private void spawnGroup(MobKind kind, SpawnRules rules, SpawnRules.Profile profile, Location spot, int group,
                            Location player, RandomGenerator random) {
        World world = spot.getWorld();
        int spread = rules.groupSpread();
        for (int member = 0; member < group; member++) {
            Location place = null;
            for (int attempt = 0; attempt < 12 && place == null; attempt++) {
                int x = spot.getBlockX() + (spread == 0 ? 0 : random.nextInt(-spread, spread + 1));
                int z = spot.getBlockZ() + (spread == 0 ? 0 : random.nextInt(-spread, spread + 1));
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                Location candidate = feet(kind, world, x, z, random.nextDouble() < rules.surfaceChance(), random);
                if (candidate != null && rules.profile(biome(candidate)) == profile) {
                    place = candidate;
                }
            }
            if (place == null || mobs.countNear(player, mobs.playerRadius(), null) >= mobs.perPlayer()) {
                continue;
            }
            place.setYaw(random.nextFloat() * 360f);
            if (mobs.spawn(kind, place, true, null) != null) {
                spawned.merge(kind.id(), 1, Integer::sum);
            }
        }
    }

    private static boolean tooCloseToPlayers(Location spot, int minDistance) {
        return MobManager.nearestPlayer(spot, minDistance) != null;
    }

    /**
     * Feet position like the original's findSpawnFeet: on the surface the floor is searched from the top block
     * up to 10 blocks upwards, in caves from a random height between the bottom and 6 below the surface up to
     * 18 blocks downwards. The floor must suit the kind, feet and head must be free.
     */
    static Location feet(MobKind kind, World world, int x, int z, boolean surface, RandomGenerator random) {
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        if (surface) {
            return floor(kind, world, x, z, top - 1, 10, 1);
        }
        int minY = world.getMinHeight() + 2;
        int highest = top - 6;
        if (highest <= minY) {
            return null;
        }
        return floor(kind, world, x, z, minY + random.nextInt(highest - minY + 1), 18, -1);
    }

    private static Location floor(MobKind kind, World world, int x, int z, int startY, int steps, int direction) {
        for (int offset = 0; offset <= steps; offset++) {
            int y = startY + offset * direction;
            if (y < world.getMinHeight() || y + 2 >= world.getMaxHeight()) {
                continue;
            }
            Block floor = world.getBlockAt(x, y, z);
            Block feet = floor.getRelative(0, 1, 0);
            Block head = feet.getRelative(0, 1, 0);
            if (floor.getType().isSolid() && floor.getType().isOccluding() && kind.canSpawnOn(floor)
                    && feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    /** Sift biomes by their path ("overgrown_forest"), others by their full key. */
    public static String biome(Location location) {
        var key = location.getWorld().getBiome(location).getKey();
        return key.getNamespace().equals("the_sift") ? key.getKey() : key.toString();
    }

    /** Spawn rules from a config section (mobs.&lt;id&gt;.spawn), or null when natural spawning is off. */
    public static SpawnRules rules(ConfigurationSection spawn) {
        if (spawn == null || !spawn.getBoolean("enabled", false)) {
            return null;
        }
        List<SpawnRules.Profile> profiles = new ArrayList<>();
        for (var entry : spawn.getMapList("profiles")) {
            Object biomes = entry.get("biomes");
            List<String> list = new ArrayList<>();
            if (biomes instanceof List<?> l) {
                l.forEach(b -> list.add(String.valueOf(b)));
            }
            profiles.add(new SpawnRules.Profile(list, number(entry.get("chance"), 0), (int) number(entry.get("cap"), 0)));
        }
        List<Integer> group = spawn.getIntegerList("group");
        return new SpawnRules(spawn.getInt("interval", 80), spawn.getInt("min-distance", 32), spawn.getInt("max-distance", 76),
                spawn.getDouble("surface-chance", 0.8), spawn.getInt("cap-radius", 64),
                group.isEmpty() ? 1 : group.getFirst(), group.size() < 2 ? (group.isEmpty() ? 1 : group.getFirst()) : group.get(1),
                profiles, Math.max(1, spawn.getInt("attempts", 1)), Math.max(0, spawn.getInt("group-spread", 3)));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
