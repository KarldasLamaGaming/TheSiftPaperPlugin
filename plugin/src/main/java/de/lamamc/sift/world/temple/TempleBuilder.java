package de.lamamc.sift.world.temple;

import de.lamamc.sift.core.temple.TempleLayout;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.world.gen.GenRegion;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

/**
 * Builds the temples (phase 4, alpha.41a) in the Sift populator: the chunk that holds a temple site builds the whole
 * temple (sites are chunk centres, the temple reaches 20 blocks, the populator may write 24). The floor sits at the
 * median ground height of the area; below it a siftslate foundation, above it the space is cleared. Worldgen threads
 * put each built temple into a queue; the main thread registers it ({@link TempleRegistry}).
 */
public final class TempleBuilder {

    /** A temple that was built, for the main thread. */
    public record Built(TempleSites.Site site, int floorY) {
    }

    public static final class Stats {
        public final AtomicLong built = new AtomicLong();
        public final AtomicLong nanos = new AtomicLong();
        public final AtomicLong maxNanos = new AtomicLong();
    }

    private static final int MAX_FOUNDATION = 24;

    private final long seed;
    private final Map<Long, TempleSites.Site> byChunk = new HashMap<>();
    private final List<TempleSites.Site> sites;
    private final LootTable chestLoot;
    private final Queue<Built> built = new ConcurrentLinkedQueue<>();
    private final Stats stats = new Stats();

    public TempleBuilder(long seed, List<TempleSites.Site> sites, LootTable chestLoot) {
        this.seed = seed;
        this.sites = List.copyOf(sites);
        this.chestLoot = chestLoot;
        for (TempleSites.Site site : sites) {
            byChunk.put(key(site.x() >> 4, site.z() >> 4), site);
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    public List<TempleSites.Site> sites() {
        return sites;
    }

    public Queue<Built> built() {
        return built;
    }

    public Stats stats() {
        return stats;
    }

    /** The areas the other features keep out of: the footprint plus a margin. */
    public List<int[]> reservedAreas(int margin) {
        int r = TempleLayout.RADIUS + margin;
        return sites.stream().map(s -> new int[] {s.x() - r, s.z() - r, s.x() + r, s.z() + r}).toList();
    }

    /** Builds the temple of this chunk, if it holds one (first step of the populator). */
    public void populate(GenRegion region, Random random, int chunkX, int chunkZ) {
        TempleSites.Site site = byChunk.get(key(chunkX, chunkZ));
        if (site == null) {
            return;
        }
        long start = System.nanoTime();
        int floorY = floorY(region, site);
        build(region, site, floorY, random);
        long nanos = System.nanoTime() - start;
        stats.built.incrementAndGet();
        stats.nanos.addAndGet(nanos);
        stats.maxNanos.accumulateAndGet(nanos, Math::max);
        built.add(new Built(site, floorY));
    }

    /** Median of the ground heights at the centre, the corners and the edge middles of the footprint. */
    private static int floorY(GenRegion region, TempleSites.Site site) {
        int r = TempleLayout.RADIUS;
        int[][] points = {{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}, {0, -r}, {0, r}, {-r, 0}, {r, 0}};
        int[] heights = new int[points.length];
        for (int i = 0; i < points.length; i++) {
            heights[i] = region.height(HeightMap.OCEAN_FLOOR_WG, site.x() + points[i][0], site.z() + points[i][1]);
        }
        Arrays.sort(heights);
        int median = heights[heights.length / 2];
        return Math.max(region.minY() + 10, Math.min(region.maxY() - TempleLayout.CLEAR_HEIGHT - 4, median));
    }

    private void build(GenRegion region, TempleSites.Site site, int floorY, Random random) {
        int r = TempleLayout.RADIUS;
        BlockData air = Material.AIR.createBlockData();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = site.x() + dx;
                int z = site.z() + dz;
                // foundation down to the ground, cleared space above the floor
                int ground = region.height(HeightMap.OCEAN_FLOOR_WG, x, z) - 1;
                for (int y = floorY - 2; y > ground && y >= floorY - 1 - MAX_FOUNDATION; y--) {
                    region.setSift(x, y, z, "siftslate");
                }
                int top = Math.min(region.maxY() - 1, Math.max(floorY + TempleLayout.CLEAR_HEIGHT, region.height(HeightMap.WORLD_SURFACE_WG, x, z)));
                for (int y = floorY; y <= top && y < floorY + TempleLayout.CLEAR_HEIGHT + 16; y++) {
                    if (region.writable(x, y, z) && !region.isAir(x, y, z)) {
                        region.set(x, y, z, air);
                    }
                }
            }
        }
        TempleLayout.Layout layout = TempleLayout.build(seed ^ (long) site.index() * 0x9E3779B97F4A7C15L);
        for (var e : layout.blocks().entrySet()) {
            int x = site.x() + e.getKey().x();
            int y = floorY + e.getKey().y();
            int z = site.z() + e.getKey().z();
            if (!region.writable(x, y, z)) {
                continue;
            }
            String block = e.getValue();
            if (block.equals("air")) {
                region.set(x, y, z, air);
            } else if (block.startsWith("minecraft:")) {
                region.set(x, y, z, Bukkit.createBlockData(block));
            } else {
                region.setSift(x, y, z, block);
            }
        }
        for (TempleLayout.Pos chest : layout.chests()) {
            var state = region.region().getBlockState(site.x() + chest.x(), floorY + chest.y(), site.z() + chest.z());
            if (chestLoot != null && state instanceof Lootable lootable) {
                lootable.setLootTable(chestLoot, random.nextLong());
                state.update(true, false);
            }
        }
    }
}
