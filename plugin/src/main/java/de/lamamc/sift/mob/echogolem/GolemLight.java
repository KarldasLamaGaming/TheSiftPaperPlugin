package de.lamamc.sift.mob.echogolem;

import de.lamamc.sift.core.mob.GolemLightRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The light a carrying Echo Golem takes along (the original's dynamic light of level 14 at its soul block): an
 * invisible light block in its head block, moved only when that block changes. Safety rules:
 * <ul>
 *   <li>set only into air; never overwrites a block;</li>
 *   <li>removed only where our light block (same level) still stands at a position we registered;</li>
 *   <li>every position is registered in its chunk's persistent data before the block is set, so a light left
 *       behind by a crash, a chunk unload or a stop is removed the next time the chunk loads (and at start);</li>
 *   <li>entity events never change blocks: a golem that leaves its world loses its light one tick later;</li>
 *   <li>plugin disable and server stop remove all lights at once.</li>
 * </ul>
 * Switch: mobs.echo_golem.glow.light-block, level glow.level.
 */
public final class GolemLight implements Listener {

    static final NamespacedKey KEY = new NamespacedKey("the_sift", "golem_lights");

    private final Plugin plugin;
    private final boolean enabled;
    private final int level;
    private final Logger logger;
    /** Golem → the block its light stands in. */
    private final Map<UUID, Block> lights = new HashMap<>();
    private long removed;

    public GolemLight(Plugin plugin, ConfigurationSection config, Logger logger) {
        this.plugin = plugin;
        ConfigurationSection glow = config == null ? null : config.getConfigurationSection("glow");
        this.enabled = glow == null || glow.getBoolean("light-block", true);
        this.level = Math.clamp(glow == null ? 14 : glow.getInt("level", 14), 1, 15);
        this.logger = logger;
    }

    public boolean enabled() {
        return enabled;
    }

    public int active() {
        return lights.size();
    }

    public long cleanedUp() {
        return removed;
    }

    /** Every tick from the golem: the light follows its head block while it carries a soul block. */
    void update(EchoGolem golem, boolean carrying) {
        UUID id = golem.entity().getUniqueId();
        Block current = lights.get(id);
        Block wanted = null;
        if (enabled && carrying && !golem.dying() && golem.entity().isValid()) {
            Location at = golem.entity().getLocation();
            wanted = at.getWorld().getBlockAt(at.getBlockX(), GolemLightRules.headBlockY(at.getY(), 1.0), at.getBlockZ());
        }
        GolemLightRules.Plan plan = GolemLightRules.plan(coords(current), coords(wanted));
        if (plan.removeOld()) {
            remove(current);
            lights.remove(id);
        }
        if (plan.placeNew() && place(wanted)) {
            lights.put(id, wanted);
        }
    }

    /** The golem left its world (death, removal, unload): its light goes one tick later, if its chunk is still there. */
    public void detached(UUID golem) {
        Block block = lights.remove(golem);
        if (block != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    remove(block);
                }
                // otherwise it stays registered in its chunk and is removed when the chunk loads again
            });
        }
    }

    private boolean place(Block block) {
        if (!GolemLightRules.canPlaceInto(block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR)) {
            return false;
        }
        register(block, true);
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(level);
        block.setBlockData(light, false);
        return true;
    }

    private void remove(Block block) {
        boolean registered = registered(block);
        boolean isLight = block.getType() == Material.LIGHT;
        int blockLevel = isLight ? ((Light) block.getBlockData()).getLevel() : -1;
        if (GolemLightRules.mayRemove(isLight, blockLevel, level, registered)) {
            block.setType(Material.AIR, false);
            removed++;
        }
        if (registered) {
            register(block, false);
        }
    }

    /** Removes every light we still have registered in a chunk (it was just loaded, no golem owns it now). */
    private void cleanChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] stored = pdc.get(KEY, PersistentDataType.LONG_ARRAY);
        if (stored == null || stored.length == 0) {
            return;
        }
        int count = 0;
        for (long packed : stored) {
            Block block = unpack(chunk, packed);
            if (lights.containsValue(block)) {
                continue; // a golem in this chunk already took it over
            }
            if (block.getType() == Material.LIGHT && ((Light) block.getBlockData()).getLevel() == level) {
                block.setType(Material.AIR, false);
                removed++;
                count++;
            }
        }
        long[] kept = java.util.Arrays.stream(stored).filter(p -> lights.containsValue(unpack(chunk, p))).toArray();
        if (kept.length == 0) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.LONG_ARRAY, kept);
        }
        if (count > 0) {
            logger.fine("Removed " + count + " echo golem light(s) left in chunk " + chunk.getX() + " " + chunk.getZ());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // a golem in this chunk attaches one tick later and sets its light again
        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(KEY)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (chunk.isLoaded()) {
                    cleanChunk(chunk);
                }
            });
        }
    }

    /** Server start / plugin enable: clean the chunks that are already loaded. */
    public void cleanLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (chunk.getPersistentDataContainer().has(KEY)) {
                    cleanChunk(chunk);
                }
            }
        }
    }

    /** Plugin disable / server stop: remove every active light now (main thread, no event in progress). */
    public void removeAll() {
        for (Block block : lights.values()) {
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                remove(block);
            }
        }
        lights.clear();
    }

    private static long[] coords(Block block) {
        return block == null ? null : new long[] {block.getX(), block.getY(), block.getZ()};
    }

    private boolean registered(Block block) {
        long[] stored = block.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.LONG_ARRAY);
        if (stored == null) {
            return false;
        }
        long packed = pack(block);
        for (long p : stored) {
            if (p == packed) {
                return true;
            }
        }
        return false;
    }

    private static void register(Block block, boolean add) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] stored = pdc.getOrDefault(KEY, PersistentDataType.LONG_ARRAY, new long[0]);
        long packed = pack(block);
        long[] next = java.util.Arrays.stream(stored).filter(p -> p != packed).toArray();
        if (add) {
            next = java.util.Arrays.copyOf(next, next.length + 1);
            next[next.length - 1] = packed;
        }
        if (next.length == 0) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.LONG_ARRAY, next);
        }
    }

    /** Position inside the chunk: x and z 0..15, y with sign. */
    private static long pack(Block block) {
        return ((long) block.getY() << 8) | ((block.getX() & 15L) << 4) | (block.getZ() & 15L);
    }

    private static Block unpack(Chunk chunk, long packed) {
        int y = (int) (packed >> 8);
        int x = (int) ((packed >> 4) & 15);
        int z = (int) (packed & 15);
        return chunk.getBlock(x, y, z);
    }
}
