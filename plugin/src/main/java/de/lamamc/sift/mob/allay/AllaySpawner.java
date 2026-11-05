package de.lamamc.sift.mob.allay;

import de.lamamc.sift.core.mob.AllayRules;
import de.lamamc.sift.world.SiftWorlds;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Vanilla allays in the Sift (alpha.31, config mobs.allay.*), as SiftAllaySpawner of the original 1.1.1:
 * groups in the overgrown forest, the sift wastes and the overgrown clearing, near players, up to a local cap.
 */
public final class AllaySpawner {

    private final boolean enabled;
    private final int interval;
    private final int attempts;
    private final int minDistance;
    private final int maxDistance;
    private final double localRadius;
    private final double localHeight;
    private final Map<String, AllayRules.Profile> profiles;
    private long groups;
    private long allays;

    public AllaySpawner(ConfigurationSection config) {
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        this.enabled = c.getBoolean("enabled", true);
        this.interval = Math.max(1, c.getInt("interval-ticks", 80));
        this.attempts = Math.max(1, c.getInt("attempts", 18));
        this.minDistance = Math.max(0, c.getInt("min-distance", 34));
        this.maxDistance = Math.max(minDistance, c.getInt("max-distance", 80));
        this.localRadius = c.getDouble("local-radius", 96);
        this.localHeight = c.getDouble("local-height", 48);
        ConfigurationSection p = c.getConfigurationSection("profiles");
        if (p == null) {
            this.profiles = AllayRules.DEFAULTS;
        } else {
            Map<String, AllayRules.Profile> loaded = new HashMap<>();
            for (String biome : p.getKeys(false)) {
                ConfigurationSection b = p.getConfigurationSection(biome);
                if (b != null) {
                    int min = Math.max(1, b.getInt("min-group", 1));
                    loaded.put(biome, new AllayRules.Profile(b.getDouble("chance", 0.5), min, Math.max(min, b.getInt("max-group", min)),
                            Math.max(0, b.getInt("local-cap", 8))));
                }
            }
            this.profiles = Map.copyOf(loaded);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public long groups() {
        return groups;
    }

    public long allays() {
        return allays;
    }

    public Map<String, AllayRules.Profile> profiles() {
        return profiles;
    }

    public void start(Plugin plugin) {
        if (enabled) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        }
    }

    private void tick() {
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            return;
        }
        for (Player player : sift.getPlayers()) {
            if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR && trySpawnNear(sift, player.getLocation())) {
                return; // one group per check, like the original
            }
        }
    }

    private boolean trySpawnNear(World world, Location around) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double phase = random.nextDouble() * Math.PI * 2.0;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int[] o = AllayRules.offset(phase, attempt, minDistance, maxDistance, random);
            int x = around.getBlockX() + o[0];
            int z = around.getBlockZ() + o[1];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            // vanilla getHeight(WORLD_SURFACE) is the first free y; Bukkit returns the highest block
            Location center = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1 + 6, z + 0.5);
            String biome = biome(world, x, center.getBlockY(), z);
            AllayRules.Profile profile = profiles.get(biome);
            if (profile == null) {
                continue;
            }
            int local = world.getNearbyEntitiesByType(Allay.class, center, localRadius, localHeight, localRadius).size();
            int size = AllayRules.groupSize(profile, local, random);
            return size > 0 && spawnGroup(world, center, biome, size, random);
        }
        return false;
    }

    private boolean spawnGroup(World world, Location center, String biome, int size, ThreadLocalRandom random) {
        int spawned = 0;
        for (int member = 0; member < size; member++) {
            Block feet = null;
            for (int attempt = 0; attempt < 10 && feet == null; attempt++) {
                int x = center.getBlockX() + random.nextInt(9) - 4;
                int z = center.getBlockZ() + random.nextInt(9) - 4;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE) + 1 + 5 + random.nextInt(8);
                Block candidate = world.getBlockAt(x, y, z);
                if (biome.equals(biome(world, x, y, z)) && candidate.getCollisionShape().getBoundingBoxes().isEmpty()
                        && candidate.getRelative(0, 1, 0).getCollisionShape().getBoundingBoxes().isEmpty()) {
                    feet = candidate;
                }
            }
            if (feet != null) {
                Location at = feet.getLocation().add(0.5, 0, 0.5);
                at.setYaw(random.nextFloat() * 360f);
                world.spawn(at, Allay.class);
                spawned++;
                allays++;
            }
        }
        if (spawned > 0) {
            groups++;
        }
        return spawned > 0;
    }

    private static String biome(World world, int x, int y, int z) {
        var key = world.getBiome(x, Math.min(y, world.getMaxHeight() - 1), z).getKey();
        return key.getNamespace().equals("the_sift") ? key.getKey() : key.toString();
    }
}
