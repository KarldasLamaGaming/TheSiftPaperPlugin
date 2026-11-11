package de.lamamc.sift.world.temple;

import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.temple.TempleLayout;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.world.gen.GenRegion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.loot.LootTable;

/**
 * Builds the temples (phase 4, alpha.41.1): a temple covers several chunks, and every chunk builds only its own part
 * (no neighbour chunk is touched or loaded). The floor height is decided once, by the chunk that holds the site (median
 * of the ground heights around the centre, read inside its own region). Chunks of a temple that are generated before
 * that chunk cannot know the floor yet; they are reported ({@link #waiting()}) and get their part afterwards on the main
 * thread ({@link TempleRegistry}). Inside the footprint everything above the floor is cleared, a siftslate foundation
 * goes down to the ground under everything that stands on the court (nothing floats), and around the footprint the
 * terrain is cut back in a slope (nothing is stuck in a mountain).
 */
public final class TempleBuilder {

    /** The floor of a temple was decided (main thread registers it). */
    public record Built(TempleSites.Site site, int floorY) {
    }

    /** A chunk of a temple: built in worldgen, or waiting for the floor. */
    public record Slice(String siteId, int chunkX, int chunkZ) {
    }

    public static final class Stats {
        public final AtomicLong slices = new AtomicLong();
        public final AtomicLong nanos = new AtomicLong();
        public final AtomicLong maxNanos = new AtomicLong();
        public final AtomicLong deferred = new AtomicLong();
        public final AtomicLong deferredNanos = new AtomicLong();
        public final AtomicLong deferredMaxNanos = new AtomicLong();

        void add(long time, boolean late) {
            (late ? deferred : slices).incrementAndGet();
            (late ? deferredNanos : nanos).addAndGet(time);
            (late ? deferredMaxNanos : maxNanos).accumulateAndGet(time, Math::max);
        }
    }

    /** Where a part is built: the worldgen region or the loaded world (deferred parts, rebuild). */
    public interface Canvas {
        /** y of the topmost solid block (ocean floor: water does not count). */
        int groundY(int x, int z);

        /** y of the topmost block of any kind. */
        int surfaceY(int x, int z);

        boolean isAir(int x, int y, int z);

        void set(int x, int y, int z, BlockData data);

        void loot(int x, int y, int z, LootTable table, long seed);

        int minY();

        int maxY();
    }

    /** A temple plan, packed per chunk: entries are (x + 128) | (y + 128) << 8 | (z + 128) << 16 | palette << 24. */
    private record Packed(List<String> palette, Map<Long, int[]> chunks, Set<Long> standing, TempleLayout.Layout layout) {
    }

    private static final int MAX_FOUNDATION = 64;
    private static final int MAX_CLEAR = 96;
    private static final int SLOPE = 8;

    private final long seed;
    private final List<TempleSites.Site> sites;
    private final TempleLayout.Params params;
    private final LootTable chestLoot;
    private final CustomBlockRegistry blocks;
    private final Map<String, Integer> floors = new ConcurrentHashMap<>();
    private final Map<Integer, Packed> plans = new ConcurrentHashMap<>();
    private final Map<String, BlockData> data = new ConcurrentHashMap<>();
    private final Queue<Built> built = new ConcurrentLinkedQueue<>();
    private final Queue<Slice> sliced = new ConcurrentLinkedQueue<>();
    private final Queue<Slice> waiting = new ConcurrentLinkedQueue<>();
    private final Stats stats = new Stats();

    public TempleBuilder(long seed, List<TempleSites.Site> sites, TempleLayout.Params params, LootTable chestLoot,
                         CustomBlockRegistry blocks) {
        this.seed = seed;
        this.sites = List.copyOf(sites);
        this.params = params;
        this.chestLoot = chestLoot;
        this.blocks = blocks;
    }

    public List<TempleSites.Site> sites() {
        return sites;
    }

    public TempleLayout.Params params() {
        return params;
    }

    public Queue<Built> built() {
        return built;
    }

    public Queue<Slice> sliced() {
        return sliced;
    }

    public Queue<Slice> waiting() {
        return waiting;
    }

    public Stats stats() {
        return stats;
    }

    /** The floor of a temple known from the registry (at start, so chunks generated later build right away). */
    public void knownFloor(String siteId, int floorY) {
        floors.put(siteId, floorY);
    }

    /** The areas the other features keep out of: the footprint, the slope around it and a margin. */
    public List<int[]> reservedAreas(int margin) {
        int r = params.extent() + SLOPE + margin;
        return sites.stream().map(s -> new int[] {s.x() - r, s.z() - r, s.x() + r, s.z() + r}).toList();
    }

    /** Half side of the area a temple touches (footprint plus the cut-back slope). */
    public int reach() {
        return params.extent() + SLOPE;
    }

    /** The plan of a site (the same for every call). */
    public TempleLayout.Layout layout(TempleSites.Site site) {
        return packed(site).layout();
    }

    /** Chunks a temple touches. */
    public List<long[]> chunks(TempleSites.Site site) {
        List<long[]> out = new ArrayList<>();
        int reach = reach();
        for (int cx = (site.x() - reach) >> 4; cx <= (site.x() + reach) >> 4; cx++) {
            for (int cz = (site.z() - reach) >> 4; cz <= (site.z() + reach) >> 4; cz++) {
                out.add(new long[] {cx, cz});
            }
        }
        return out;
    }

    private Packed packed(TempleSites.Site site) {
        return plans.computeIfAbsent(site.index(), i -> {
            TempleLayout.Layout layout = TempleLayout.build(seed ^ (long) site.index() * 0x9E3779B97F4A7C15L, params);
            List<String> palette = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            Map<Long, List<Integer>> perChunk = new HashMap<>();
            for (Map.Entry<TempleLayout.Pos, String> e : layout.blocks().entrySet()) {
                TempleLayout.Pos p = e.getKey();
                int id = index.computeIfAbsent(e.getValue(), v -> {
                    palette.add(v);
                    return palette.size() - 1;
                });
                long key = key((site.x() + p.x()) >> 4, (site.z() + p.z()) >> 4);
                perChunk.computeIfAbsent(key, k -> new ArrayList<>()).add((p.x() + 128) | (p.y() + 128) << 8 | (p.z() + 128) << 16 | id << 24);
            }
            Map<Long, int[]> chunks = new HashMap<>();
            perChunk.forEach((k, list) -> chunks.put(k, list.stream().mapToInt(Integer::intValue).toArray()));
            return new Packed(List.copyOf(palette), chunks, TempleLayout.standingColumns(layout), layout);
        });
    }

    static long key(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    /** The temple whose area touches this chunk, or null. Sites are far apart, so at most one does. */
    public TempleSites.Site siteAt(int chunkX, int chunkZ) {
        int reach = reach();
        for (TempleSites.Site site : sites) {
            if (chunkX >= (site.x() - reach) >> 4 && chunkX <= (site.x() + reach) >> 4
                    && chunkZ >= (site.z() - reach) >> 4 && chunkZ <= (site.z() + reach) >> 4) {
                return site;
            }
        }
        return null;
    }

    /** Worldgen: builds this chunk's part of a temple (first step of the populator). */
    public void populate(GenRegion region, Random random, int chunkX, int chunkZ) {
        TempleSites.Site site = siteAt(chunkX, chunkZ);
        if (site == null) {
            return;
        }
        long start = System.nanoTime();
        if (site.x() >> 4 == chunkX && site.z() >> 4 == chunkZ && !floors.containsKey(site.id())) {
            int floorY = floorY(region, site);
            if (floors.putIfAbsent(site.id(), floorY) == null) {
                built.add(new Built(site, floorY));
            }
        }
        Integer floorY = floors.get(site.id());
        if (floorY == null) {
            waiting.add(new Slice(site.id(), chunkX, chunkZ));
            return;
        }
        build(new RegionCanvas(region), site, floorY, chunkX, chunkZ, random.nextLong());
        sliced.add(new Slice(site.id(), chunkX, chunkZ));
        stats.add(System.nanoTime() - start, false);
    }

    /** Main thread: a part of a temple whose chunk was generated before the floor was known (or a rebuild). */
    public void buildLate(Canvas canvas, TempleSites.Site site, int floorY, int chunkX, int chunkZ, long lootSeed) {
        long start = System.nanoTime();
        build(canvas, site, floorY, chunkX, chunkZ, lootSeed);
        stats.add(System.nanoTime() - start, true);
    }

    /** Median of the ground heights at the centre and 16 blocks around it (inside the site chunk's region). */
    private int floorY(GenRegion region, TempleSites.Site site) {
        int[] heights = new int[9];
        int i = 0;
        for (int dx = -16; dx <= 16; dx += 16) {
            for (int dz = -16; dz <= 16; dz += 16) {
                heights[i++] = region.height(HeightMap.OCEAN_FLOOR_WG, site.x() + dx, site.z() + dz);
            }
        }
        Arrays.sort(heights);
        int median = heights[4];
        return Math.max(region.minY() + params.depth() + 8, Math.min(region.maxY() - params.top() - 4, median));
    }

    private BlockData data(String block) {
        return data.computeIfAbsent(block, b -> {
            if (b.equals("air")) {
                return Material.AIR.createBlockData();
            }
            if (b.startsWith("minecraft:")) {
                return Bukkit.createBlockData(b);
            }
            return blocks.defaultState(b).data();
        });
    }

    private void build(Canvas canvas, TempleSites.Site site, int floorY, int chunkX, int chunkZ, long lootSeed) {
        Packed plan = packed(site);
        int ext = params.extent();
        BlockData air = data("air");
        BlockData foundation = data("siftslate");
        int minY = canvas.minY();
        int maxY = canvas.maxY();
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                int dx = x - site.x();
                int dz = z - site.z();
                int out = Math.max(Math.abs(dx), Math.abs(dz)) - ext;
                if (out > SLOPE) {
                    continue;
                }
                // clear above the court; outside the footprint as a slope that rises away from the temple
                int from = floorY + Math.max(0, out) * 2;
                int to = Math.min(maxY - 1, Math.min(floorY + MAX_CLEAR, canvas.surfaceY(x, z)));
                for (int y = Math.max(from, minY); y <= to; y++) {
                    if (!canvas.isAir(x, y, z)) {
                        canvas.set(x, y, z, air);
                    }
                }
                // foundation under everything that stands on the court
                if (out <= 0 && plan.standing().contains(TempleLayout.column(dx, dz))) {
                    int ground = canvas.groundY(x, z);
                    for (int y = floorY - 2; y > ground && y >= Math.max(minY, floorY - 2 - MAX_FOUNDATION); y--) {
                        canvas.set(x, y, z, foundation);
                    }
                }
            }
        }
        int[] entries = plan.chunks().get(key(chunkX, chunkZ));
        if (entries == null) {
            return;
        }
        Random loot = new Random(lootSeed);
        for (int e : entries) {
            int x = site.x() + (e & 0xFF) - 128;
            int y = floorY + ((e >> 8) & 0xFF) - 128;
            int z = site.z() + ((e >> 16) & 0xFF) - 128;
            if (y < minY || y >= maxY) {
                continue;
            }
            String block = plan.palette().get(e >>> 24);
            canvas.set(x, y, z, data(block));
            if (chestLoot != null && block.startsWith("minecraft:chest")) {
                canvas.loot(x, y, z, chestLoot, loot.nextLong());
            }
        }
    }

    /** The worldgen region, restricted to the chunk being populated by the caller. */
    private static final class RegionCanvas implements Canvas {

        private final GenRegion region;

        RegionCanvas(GenRegion region) {
            this.region = region;
        }

        @Override
        public int groundY(int x, int z) {
            return region.height(HeightMap.OCEAN_FLOOR_WG, x, z) - 1;
        }

        @Override
        public int surfaceY(int x, int z) {
            return region.height(HeightMap.WORLD_SURFACE_WG, x, z) - 1;
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return region.isAir(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, BlockData data) {
            region.set(x, y, z, data);
        }

        @Override
        public void loot(int x, int y, int z, LootTable table, long seed) {
            var state = region.region().getBlockState(x, y, z);
            if (state instanceof org.bukkit.loot.Lootable lootable) {
                lootable.setLootTable(table, seed);
                state.update(true, false);
            }
        }

        @Override
        public int minY() {
            return region.minY();
        }

        @Override
        public int maxY() {
            return region.maxY();
        }
    }
}
