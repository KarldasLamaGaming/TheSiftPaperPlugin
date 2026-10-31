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
            if (rules == null) {
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
        SpawnRules.Offset offset = rules.offset(random);
        int x = center.getBlockX() + offset.dx();
        int z = center.getBlockZ() + offset.dz();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        Location spot = offset.surface() ? surface(world, x, z) : cave(world, x, z, random);
        if (spot == null || tooCloseToPlayers(spot, rules.minDistance())) {
            return;
        }
        String biome = biome(spot);
        int nearby = mobs.countNear(spot, rules.capRadius(), kind.id());
        if (!rules.rollAttempt(biome, nearby, random) || !kind.canSpawnAt(spot)) {
            return;
        }
        int group = rules.groupSize(biome, nearby, random);
        List<Location> places = new ArrayList<>();
        places.add(spot);
        for (int i = 1; i < group * 4 && places.size() < group; i++) {
            Location near = standable(world, spot.getBlockX() + random.nextInt(-3, 4), spot.getBlockY() + 1,
                    spot.getBlockZ() + random.nextInt(-3, 4), 3);
            if (near != null && kind.canSpawnAt(near)) {
                places.add(near);
            }
        }
        for (Location place : places) {
            if (mobs.countNear(center, mobs.playerRadius(), null) >= mobs.perPlayer()) {
                break;
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

    private static Location surface(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return standable(world, x, y + 1, z, 2);
    }

    private static Location cave(World world, int x, int z, RandomGenerator random) {
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) - 8;
        int bottom = world.getMinHeight() + 8;
        if (top <= bottom) {
            return null;
        }
        return standable(world, x, random.nextInt(bottom, top), z, 16);
    }

    /** First spot at or below {@code y} (scanning {@code depth} blocks) with solid ground and two free blocks. */
    static Location standable(World world, int x, int y, int z, int depth) {
        for (int dy = 0; dy <= depth; dy++) {
            Block feet = world.getBlockAt(x, y - dy, z);
            Block below = feet.getRelative(0, -1, 0);
            if (below.getType().isSolid() && feet.isPassable() && !feet.isLiquid()
                    && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, 1, 0).isLiquid()) {
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
                profiles);
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}
