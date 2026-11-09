package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.SnifferCavePlan;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Sniffer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Sniffer caves and sniffer plant patches of the original 1.1.1 (alpha.28, config worldgen.sniffer-caves.*
 * and worldgen.sniffer-plant-patches.*), built by the Sift populator with {@link SnifferCavePlan}. The
 * sniffer family of a cave is queued by the worldgen thread and spawned on the main thread once the nest's
 * chunks are loaded (SiftDeferredSnifferSpawner: up to 4 per tick, 80 tries).
 */
public final class SnifferCaves {

    private static final NamespacedKey CAVES = new NamespacedKey("the_sift", "sniffer_caves");
    private static final Set<String> SOIL = Set.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk",
            "dry_healthy_sculk_growth", "overgrown_willow_foliage");
    private static final int PER_TICK = 4;
    private static final int MAX_RETRIES = 80;

    /** One cave feature: cells of {@code cellChunks} with {@code candidates} candidate chunks, where the chunk's biome is listed. */
    public record Cells(int cellChunks, int candidates, Set<String> biomes) {
    }

    public record Settings(boolean caves, Cells overgrown, Cells wastes, int portalClearance, boolean family, boolean ichorLight,
                           boolean plantPatches, int plantRarity, Set<String> plantBiomes) {

        private static final List<String> OVERGROWN = List.of("overgrown_clearing", "overgrown_forest", "overgrown_forest_slopes",
                "overgrown_peaks", "overgrown_slopes");

        public static Settings load(ConfigurationSection worldgen) {
            ConfigurationSection w = worldgen == null ? new MemoryConfiguration() : worldgen;
            List<String> plants = new ArrayList<>(OVERGROWN);
            plants.addAll(List.of("sift_wastes", "siftslate_peaks", "siftslate_slopes"));
            return new Settings(w.getBoolean("sniffer-caves.enabled", true),
                    cells(w, "sniffer-caves.overgrown", 16, 4, OVERGROWN),
                    cells(w, "sniffer-caves.wastes", 28, 3, List.of("ichor_snowy_peaks", "sift_wastes", "siftslate_peaks", "siftslate_slopes")),
                    Math.max(0, w.getInt("sniffer-caves.portal-clearance", 12)), w.getBoolean("sniffer-caves.family", true),
                    w.getBoolean("sniffer-caves.ichor-light", true), w.getBoolean("sniffer-plant-patches.enabled", true),
                    Math.max(1, w.getInt("sniffer-plant-patches.rarity", 14)), biomes(w, "sniffer-plant-patches.biomes", plants));
        }

        private static Cells cells(ConfigurationSection w, String path, int cell, int candidates, List<String> biomes) {
            return new Cells(Math.max(1, w.getInt(path + ".cell-chunks", cell)), Math.max(1, w.getInt(path + ".candidates", candidates)),
                    biomes(w, path + ".biomes", biomes));
        }

        private static Set<String> biomes(ConfigurationSection w, String path, List<String> fallback) {
            return Set.copyOf(w.isList(path) ? w.getStringList(path) : fallback);
        }
    }

    /** Counters for /sift debug worldgen. */
    public static final class Stats {
        public final AtomicLong candidates = new AtomicLong();
        public final AtomicLong caves = new AtomicLong();
        public final AtomicLong nanos = new AtomicLong();
        public final AtomicLong maxNanos = new AtomicLong();
        public final Map<String, Integer> rejects = new ConcurrentHashMap<>();
        public final AtomicLong eggs = new AtomicLong();
        public final AtomicLong plants = new AtomicLong();
        public final AtomicLong sniffersQueued = new AtomicLong();
        public final AtomicLong sniffersSpawned = new AtomicLong();
        public final AtomicLong sniffersGivenUp = new AtomicLong();
        public final AtomicLong plantPatches = new AtomicLong();
    }

    /** One sniffer to spawn on one of the nest blocks. */
    record Request(List<int[]> nest, boolean baby, long seed, int attempts) {
    }

    private final Settings settings;
    private final int lightSpacing;
    private final Stats stats = new Stats();
    /** nest centre → entrance, both packed. */
    private final Map<Long, Long> caves = new ConcurrentHashMap<>();
    private final Queue<Request> pending = new ConcurrentLinkedQueue<>();
    private volatile boolean dirty;

    public SnifferCaves(Settings settings, int lightSpacing) {
        this.settings = settings;
        this.lightSpacing = lightSpacing;
    }

    public Settings settings() {
        return settings;
    }

    public Stats stats() {
        return stats;
    }

    /** Both cave features of this chunk (feature step underground_decoration, after the soul canyon). */
    public void caves(GenRegion region, Random random, int chunkX, int chunkZ, long seed, PortalReservation reservation) {
        if (!settings.caves()) {
            return;
        }
        cave(region, random, chunkX, chunkZ, seed, reservation, settings.overgrown(), SnifferCavePlan.OVERGROWN_SALT);
        cave(region, random, chunkX, chunkZ, seed, reservation, settings.wastes(), SnifferCavePlan.WASTES_SALT);
    }

    private void cave(GenRegion region, Random random, int chunkX, int chunkZ, long seed, PortalReservation reservation, Cells cells,
                      long salt) {
        // placement: heightmap and biome at the chunk origin, then the cell check of the feature
        if (!cells.biomes().contains(SiftPopulator.biome(region, chunkX << 4, chunkZ << 4))
                || !SnifferCavePlan.selected(seed, chunkX, chunkZ, cells.cellChunks(), salt, cells.candidates())) {
            return;
        }
        stats.candidates.incrementAndGet();
        long start = System.nanoTime();
        SnifferCavePlan.Result r = SnifferCavePlan.place(world(region, reservation), random, chunkX, chunkZ, settings.portalClearance());
        if (r.built()) {
            lights(region, r);
        }
        long nanos = System.nanoTime() - start;
        stats.nanos.addAndGet(nanos);
        stats.maxNanos.accumulateAndGet(nanos, Math::max);
        if (!r.built()) {
            stats.rejects.merge(r.reason(), 1, Integer::sum);
            return;
        }
        stats.caves.incrementAndGet();
        stats.eggs.addAndGet(r.eggs());
        stats.plants.addAndGet(r.plants());
        caves.put(SoulCanyonLandmarks.pack(r.nest()[0], r.nest()[1], r.nest()[2]),
                SoulCanyonLandmarks.pack(r.entrance()[0], r.entrance()[1], r.entrance()[2]));
        dirty = true;
        if (settings.family() && !r.nestFloors().isEmpty()) {
            int[] first = r.nestFloors().get(0);
            long family = seed ^ SoulCanyonLandmarks.pack(first[0], first[1], first[2]) ^ 6002815919506936396L;
            pending.add(new Request(r.nestFloors(), false, mix(family), 0));
            pending.add(new Request(r.nestFloors(), false, mix(family + 1), 0));
            if (r.baby()) {
                pending.add(new Request(r.nestFloors(), true, mix(family + 2), 0));
            }
            stats.sniffersQueued.addAndGet(r.baby() ? 3 : 2);
        }
    }

    /** Sniffer plant patch of this chunk (end of vegetal_decoration). */
    public void plantPatch(GenRegion region, Random random, int chunkX, int chunkZ, PortalReservation reservation) {
        if (!settings.plantPatches() || random.nextInt(settings.plantRarity()) != 0
                || !settings.plantBiomes().contains(SiftPopulator.biome(region, chunkX << 4, chunkZ << 4))) {
            return;
        }
        if (SnifferCavePlan.plantPatch(world(region, reservation), random, chunkX, chunkZ) > 0) {
            stats.plantPatches.incrementAndGet();
        }
    }

    /** The ichor of the basin glows like the lakes: a light block in the air above every few surface blocks (alpha.36). */
    private void lights(GenRegion region, SnifferCavePlan.Result r) {
        if (!settings.ichorLight() || lightSpacing <= 0) {
            return;
        }
        for (int[] c : r.ichor()) {
            if (Math.floorMod(c[0], lightSpacing) == 0 && Math.floorMod(c[2], lightSpacing) == 0 && region.writable(c[0], c[1] + 1, c[2])
                    && region.type(c[0], c[1], c[2]) == Material.WATER && region.type(c[0], c[1] + 1, c[2]).isAir()) {
                region.set(c[0], c[1] + 1, c[2], IchorWaters.light());
            }
        }
    }

    static SnifferCavePlan.World world(GenRegion region, PortalReservation reservation) {
        BlockData air = Material.AIR.createBlockData();
        BlockData water = Material.WATER.createBlockData();
        return new SnifferCavePlan.World() {
            @Override
            public int height(int x, int z) {
                return region.contains(x, z) ? region.height(HeightMap.WORLD_SURFACE_WG, x, z) : region.minY();
            }

            @Override
            public String block(int x, int y, int z) {
                if (y < region.minY() || y >= region.maxY() || !region.region().isInRegion(x, y, z)) {
                    return "minecraft:void";
                }
                Material type = region.type(x, y, z);
                if (type.isAir()) {
                    return SnifferCavePlan.AIR;
                }
                String id = region.id(x, y, z);
                if (id != null) {
                    return id;
                }
                // the portal frame is reinforced deepslate, the portal surface light blocks
                return type == Material.REINFORCED_DEEPSLATE ? "reinforced_siftslate" : type == Material.LIGHT ? "sift_portal"
                        : type.getKey().toString();
            }

            @Override
            public boolean contains(int x, int z) {
                return region.contains(x, z);
            }

            @Override
            public boolean writable(int x, int y, int z) {
                return region.writable(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, String block) {
                if (!region.writable(x, y, z)) {
                    return;
                }
                switch (block) {
                    case SnifferCavePlan.AIR -> region.set(x, y, z, air);
                    case SnifferCavePlan.ICHOR -> region.set(x, y, z, water);
                    default -> region.setSift(x, y, z, block);
                }
            }

            @Override
            public boolean plant(int x, int y, int z, String plant, int age) {
                if (!region.writable(x, y, z)) {
                    return false;
                }
                String floor = region.id(x, y - 1, z);
                switch (plant) {
                    case SnifferCavePlan.EGG -> region.set(x, y, z, Material.SNIFFER_EGG.createBlockData());
                    case SnifferCavePlan.TORCHFLOWER -> {
                        if (floor == null || !SOIL.contains(floor)) {
                            return false;
                        }
                        region.set(x, y, z, Material.TORCHFLOWER.createBlockData());
                    }
                    case SnifferCavePlan.PITCHER -> {
                        if (floor == null || !SOIL.contains(floor) || !region.writable(x, y + 1, z)) {
                            return false;
                        }
                        region.set(x, y, z, pitcher(age, Bisected.Half.BOTTOM));
                        region.set(x, y + 1, z, pitcher(age, Bisected.Half.TOP));
                    }
                    default -> {
                        if (!region.plantFits(plant, x, y, z)) {
                            return false;
                        }
                        region.setSift(x, y, z, plant);
                    }
                }
                return true;
            }

            @Override
            public boolean nearPortal(int x, int z, int radius) {
                return reservation.intersects(x, z, radius);
            }

            @Override
            public int minY() {
                return region.minY();
            }
        };
    }

    private static BlockData pitcher(int age, Bisected.Half half) {
        BlockData data = Material.PITCHER_CROP.createBlockData();
        ((Ageable) data).setAge(Math.min(age, ((Ageable) data).getMaximumAge()));
        ((Bisected) data).setHalf(half);
        return data;
    }

    // ---- sniffer families (main thread) --------------------------------------------------------------------

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    private void tick() {
        if (pending.isEmpty()) {
            return;
        }
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            return;
        }
        int available = pending.size();
        int spawned = 0;
        for (int i = 0; i < available && spawned < PER_TICK; i++) {
            Request request = pending.poll();
            if (request == null) {
                break;
            }
            if (loaded(sift, request) && spawn(sift, request)) {
                spawned++;
                stats.sniffersSpawned.incrementAndGet();
            } else if (request.attempts() + 1 <= MAX_RETRIES) {
                pending.add(new Request(request.nest(), request.baby(), request.seed(), request.attempts() + 1));
            } else {
                stats.sniffersGivenUp.incrementAndGet();
            }
        }
    }

    private static boolean loaded(World world, Request request) {
        for (int[] f : request.nest()) {
            if (!world.isChunkLoaded(f[0] >> 4, f[2] >> 4)) {
                return false;
            }
        }
        return true;
    }

    private static boolean spawn(World world, Request request) {
        Random random = new Random(request.seed());
        List<int[]> nest = request.nest();
        int start = random.nextInt(nest.size());
        for (int offset = 0; offset < nest.size(); offset++) {
            int[] f = nest.get((start + offset) % nest.size());
            Block feet = world.getBlockAt(f[0], f[1] + 1, f[2]);
            if (feet.getType().isAir() && feet.getRelative(0, 1, 0).getType().isAir() && feet.getType() != Material.WATER
                    && feet.getRelative(0, -1, 0).getType() != Material.WATER) {
                Location at = feet.getLocation().add(0.5, 0, 0.5);
                at.setYaw(random.nextFloat() * 360f);
                world.spawn(at, Sniffer.class, s -> {
                    if (request.baby()) {
                        s.setBaby();
                    } else {
                        s.setAdult();
                    }
                });
                return true;
            }
        }
        return false;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        return value ^ value >>> 31;
    }

    // ---- records (for /sift debug landmarks) ----------------------------------------------------------------

    public void load(World world) {
        long[] stored = world.getPersistentDataContainer().get(CAVES, PersistentDataType.LONG_ARRAY);
        if (stored != null) {
            for (int i = 0; i + 1 < stored.length; i += 2) {
                caves.put(stored[i], stored[i + 1]);
            }
        }
    }

    /** Main thread only. */
    public void save(World world) {
        if (!dirty) {
            return;
        }
        dirty = false;
        long[] out = new long[caves.size() * 2];
        int i = 0;
        for (var e : caves.entrySet()) {
            if (i + 1 >= out.length) {
                break;
            }
            out[i++] = e.getKey();
            out[i++] = e.getValue();
        }
        world.getPersistentDataContainer().set(CAVES, PersistentDataType.LONG_ARRAY, java.util.Arrays.copyOf(out, i));
    }

    public int count() {
        return caves.size();
    }

    /** A recorded cave: the centre of its nest chamber and its entrance on the surface. */
    public record Found(SoulCanyonLandmarks.Pos nest, SoulCanyonLandmarks.Pos entrance) {
    }

    public List<Found> nearest(double x, double z, int limit) {
        List<Found> all = new ArrayList<>();
        caves.forEach((nest, entrance) -> all.add(new Found(SoulCanyonLandmarks.unpack(nest), SoulCanyonLandmarks.unpack(entrance))));
        all.sort(Comparator.comparingDouble(f -> f.nest().horizontalDistanceSquared(x, z)));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public int pendingSniffers() {
        return pending.size();
    }
}
