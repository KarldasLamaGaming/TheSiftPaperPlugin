package de.lamamc.sift.world.ichor;

import de.lamamc.sift.core.ichor.FluidIdentity;
import de.lamamc.sift.world.SiftWorlds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The exceptions of {@link FluidIdentity} (alpha.38): positions whose water is not what the world's default says
 * (plain water in the Sift, ichor elsewhere). Kept per chunk in its persistent data ({@code the_sift:fluid_marks},
 * packed positions) and in memory while the chunk is loaded; written back when the chunk unloads, when the world
 * saves and on disable. A mark only counts while its block holds water; marks of blocks that hold none any more are
 * dropped on unload (ice and cauldrons keep theirs, so frozen or poured ichor stays ichor).
 */
public final class FluidMarks implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "fluid_marks");

    private final Map<String, Map<Long, Set<Integer>>> worlds = new HashMap<>();
    private final Map<String, Set<Long>> dirty = new HashMap<>();

    /** Loads the marks of the chunks that are loaded already (plugin start). */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    private Map<Long, Set<Integer>> chunks(World world) {
        return worlds.computeIfAbsent(world.getName(), w -> new HashMap<>());
    }

    /** Whether the world has any mark in its loaded chunks (outside the Sift the ichor ticks only run then). */
    public boolean any(World world) {
        Map<Long, Set<Integer>> chunks = worlds.get(world.getName());
        return chunks != null && !chunks.isEmpty();
    }

    /** Marks in the loaded chunks of a world. */
    public int count(World world) {
        Map<Long, Set<Integer>> chunks = worlds.get(world.getName());
        return chunks == null ? 0 : chunks.values().stream().mapToInt(Set::size).sum();
    }

    /** Loaded chunks with marks. */
    public int chunkCount(World world) {
        Map<Long, Set<Integer>> chunks = worlds.get(world.getName());
        return chunks == null ? 0 : chunks.size();
    }

    public boolean marked(Block block) {
        Map<Long, Set<Integer>> chunks = worlds.get(block.getWorld().getName());
        if (chunks == null) {
            return false;
        }
        Set<Integer> marks = chunks.get(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
        return marks != null && marks.contains(FluidIdentity.pack(block.getX(), block.getY(), block.getZ()));
    }

    public void set(Block block, boolean mark) {
        long key = Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4);
        int packed = FluidIdentity.pack(block.getX(), block.getY(), block.getZ());
        Map<Long, Set<Integer>> chunks = chunks(block.getWorld());
        boolean changed;
        if (mark) {
            changed = chunks.computeIfAbsent(key, k -> new HashSet<>()).add(packed);
        } else {
            Set<Integer> marks = chunks.get(key);
            changed = marks != null && marks.remove(packed);
            if (marks != null && marks.isEmpty()) {
                chunks.remove(key);
            }
        }
        if (changed) {
            dirty.computeIfAbsent(block.getWorld().getName(), w -> new HashSet<>()).add(key);
        }
    }

    /** Whether the water at this block (water, waterlogged, water cauldron) is ichor. */
    public boolean ichor(Block block) {
        return FluidIdentity.isIchor(SiftWorlds.isSift(block.getWorld()), marked(block));
    }

    /** Water, a waterlogged block or a filled water cauldron. */
    public static boolean holdsWater(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.WATER_CAULDRON
                || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    /** A water source (water level 0 or a waterlogged block): a bucket emptied into it changes nothing. */
    public static boolean holdsSource(Block block) {
        if (block.getType() == Material.WATER) {
            return block.getBlockData() instanceof org.bukkit.block.data.Levelled level && level.getLevel() == 0;
        }
        return block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    /** Where a mark may stay without water right now: frozen water melts back into the same water. */
    private static boolean keeps(Block block) {
        Material type = block.getType();
        return holdsWater(block) || type == Material.ICE || type == Material.FROSTED_ICE;
    }

    // ---- loading and saving -------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        Map<Long, Set<Integer>> chunks = worlds.get(chunk.getWorld().getName());
        Set<Integer> marks = chunks == null ? null : chunks.get(chunk.getChunkKey());
        if (marks != null) {
            int before = marks.size();
            marks.removeIf(p -> !keeps(chunk.getBlock(FluidIdentity.localX(p), FluidIdentity.y(p), FluidIdentity.localZ(p))));
            if (marks.size() != before) {
                dirty.computeIfAbsent(chunk.getWorld().getName(), w -> new HashSet<>()).add(chunk.getChunkKey());
            }
        }
        write(chunk);
        if (chunks != null) {
            chunks.remove(chunk.getChunkKey());
        }
        Set<Long> keys = dirty.get(chunk.getWorld().getName());
        if (keys != null) {
            keys.remove(chunk.getChunkKey());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSave(WorldSaveEvent event) {
        flush(event.getWorld());
    }

    /** Writes the changed chunks of every world (plugin disable). */
    public void flushAll() {
        Bukkit.getWorlds().forEach(this::flush);
    }

    private void flush(World world) {
        Set<Long> keys = dirty.get(world.getName());
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (long key : Set.copyOf(keys)) {
            int x = (int) key;
            int z = (int) (key >> 32);
            if (world.isChunkLoaded(x, z)) {
                write(world.getChunkAt(x, z));
            }
        }
        keys.clear();
    }

    private void load(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (stored == null || stored.length == 0) {
            return;
        }
        Set<Integer> marks = new HashSet<>();
        for (int packed : stored) {
            marks.add(packed);
        }
        chunks(chunk.getWorld()).put(chunk.getChunkKey(), marks);
    }

    private void write(Chunk chunk) {
        Set<Long> keys = dirty.get(chunk.getWorld().getName());
        if (keys == null || !keys.contains(chunk.getChunkKey())) {
            return;
        }
        Map<Long, Set<Integer>> chunks = worlds.get(chunk.getWorld().getName());
        Set<Integer> marks = chunks == null ? null : chunks.get(chunk.getChunkKey());
        if (marks == null || marks.isEmpty()) {
            chunk.getPersistentDataContainer().remove(KEY);
        } else {
            chunk.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER_ARRAY,
                    marks.stream().mapToInt(Integer::intValue).toArray());
        }
    }
}
