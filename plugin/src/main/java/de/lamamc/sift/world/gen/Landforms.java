package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.ArchPlan;
import de.lamamc.sift.core.worldgen.DrySpikePlan;
import de.lamamc.sift.core.worldgen.LandformWorld;
import de.lamamc.sift.core.worldgen.MonolithPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.persistence.PersistentDataType;

/**
 * Monoliths and cliff arches of the original 1.1.1 (alpha.27, config worldgen.monoliths.* and
 * worldgen.arches.*), built by the Sift populator with {@link MonolithPlan} and {@link ArchPlan} in the
 * order of the original (after the ichor lake, before the willows). Placement like the placed features:
 * rarity filter, a random column of the chunk, biome there. Built ones are recorded in the world's
 * persistent data for /sift debug landmarks.
 */
public final class Landforms {

    public enum Kind {
        LUSH("üppiger Monolith"), BARE("kahler Monolith"), ARCH("Bogen"), SPIKE("trockene Sculk-Spitze");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        NamespacedKey key() {
            return new NamespacedKey("the_sift", switch (this) {
                case LUSH -> "lush_monoliths";
                case BARE -> "bare_monoliths";
                case ARCH -> "arches";
                case SPIKE -> "dry_sculk_spikes";
            });
        }
    }

    /** One placed feature: 1 in {@code rarity} chunks, where the chosen column is one of {@code biomes}. */
    public record Placement(int rarity, Set<String> biomes) {

        static Placement load(ConfigurationSection s, String path, int rarity, List<String> biomes) {
            List<String> configured = s.getStringList(path + ".biomes");
            return new Placement(Math.max(1, s.getInt(path + ".rarity", rarity)), Set.copyOf(configured.isEmpty() && !s.isList(path
                    + ".biomes") ? biomes : configured));
        }
    }

    public record Settings(boolean lush, Placement lushOvergrown, Placement lushWastes, boolean bare, Placement bareOvergrown,
                           Placement bareWastes, int monolithPortalClearance, int monolithReserve, boolean arches, Placement arch,
                           int archPortalClearance, boolean spikes, Placement spikesOvergrown, Placement spikesWastes, int spikePortalClearance,
                           int spikeReserve) {

        private static final List<String> OVERGROWN = List.of("overgrown_clearing", "overgrown_forest", "overgrown_forest_slopes",
                "overgrown_peaks", "overgrown_slopes");

        public static Settings load(ConfigurationSection worldgen) {
            ConfigurationSection w = worldgen == null ? new MemoryConfiguration() : worldgen;
            boolean monoliths = w.getBoolean("monoliths.enabled", true);
            boolean arches = w.getBoolean("arches.enabled", true);
            return new Settings(monoliths && w.getBoolean("monoliths.lush.enabled", true),
                    Placement.load(w, "monoliths.lush.overgrown", 11, OVERGROWN),
                    Placement.load(w, "monoliths.lush.wastes", 8, List.of("ichor_snowy_peaks", "sift_wastes", "siftslate_slopes")),
                    monoliths && w.getBoolean("monoliths.bare.enabled", true),
                    Placement.load(w, "monoliths.bare.overgrown", 20, OVERGROWN),
                    Placement.load(w, "monoliths.bare.wastes", 15, List.of("ichor_snowy_peaks", "sift_wastes", "siftslate_peaks",
                            "siftslate_slopes")),
                    Math.max(0, w.getInt("monoliths.portal-clearance", 22)), Math.max(0, w.getInt("monoliths.reserve-radius", 22)),
                    arches, Placement.load(w, "arches", 5, List.of("ichor_snowy_peaks", "overgrown_clearing", "overgrown_forest",
                            "overgrown_forest_slopes", "overgrown_peaks", "overgrown_slopes", "sift_wastes", "siftslate_peaks",
                            "siftslate_slopes")),
                    Math.max(0, w.getInt("arches.portal-clearance", 23)),
                    w.getBoolean("dry-sculk-spikes.enabled", true), Placement.load(w, "dry-sculk-spikes.overgrown", 21, OVERGROWN),
                    Placement.load(w, "dry-sculk-spikes.wastes", 16, List.of("ichor_snowy_peaks", "sift_wastes", "siftslate_peaks",
                            "siftslate_slopes")),
                    Math.max(0, w.getInt("dry-sculk-spikes.portal-clearance", 18)), Math.max(0, w.getInt("dry-sculk-spikes.reserve-radius", 18)));
        }
    }

    /** Counters for /sift debug worldgen. */
    public static final class Stats {
        public final AtomicLong monolithCandidates = new AtomicLong();
        public final AtomicLong lush = new AtomicLong();
        public final AtomicLong bare = new AtomicLong();
        public final AtomicLong satellites = new AtomicLong();
        public final AtomicLong monolithNanos = new AtomicLong();
        public final AtomicLong monolithMaxNanos = new AtomicLong();
        public final Map<String, Integer> monolithRejects = new ConcurrentHashMap<>();
        public final AtomicLong archCandidates = new AtomicLong();
        public final AtomicLong arches = new AtomicLong();
        public final AtomicLong healthyArches = new AtomicLong();
        public final AtomicLong archNanos = new AtomicLong();
        public final AtomicLong archMaxNanos = new AtomicLong();
        public final Map<String, Integer> archRejects = new ConcurrentHashMap<>();
        public final AtomicLong spikeCandidates = new AtomicLong();
        public final AtomicLong spikes = new AtomicLong();
        public final AtomicLong spikeNanos = new AtomicLong();
        public final AtomicLong spikeMaxNanos = new AtomicLong();
        public final Map<String, Integer> spikeRejects = new ConcurrentHashMap<>();
    }

    private final Settings settings;
    private final Stats stats = new Stats();
    private final Map<Kind, Set<Long>> records = Map.of(Kind.LUSH, ConcurrentHashMap.newKeySet(), Kind.BARE,
            ConcurrentHashMap.newKeySet(), Kind.ARCH, ConcurrentHashMap.newKeySet(), Kind.SPIKE, ConcurrentHashMap.newKeySet());
    private volatile boolean dirty;

    public Landforms(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public Stats stats() {
        return stats;
    }

    /** Lush and bare monoliths, then the cliff arch (feature step vegetal_decoration, before the willows). */
    public void place(GenRegion region, Random random, int chunkX, int chunkZ, PortalReservation reservation) {
        if (settings.lush()) {
            monolith(region, random, chunkX, chunkZ, reservation, settings.lushOvergrown(), true);
            monolith(region, random, chunkX, chunkZ, reservation, settings.lushWastes(), true);
        }
        if (settings.bare()) {
            monolith(region, random, chunkX, chunkZ, reservation, settings.bareOvergrown(), false);
            monolith(region, random, chunkX, chunkZ, reservation, settings.bareWastes(), false);
        }
        if (settings.spikes()) {
            spikes(region, random, chunkX, chunkZ, reservation, settings.spikesOvergrown());
            spikes(region, random, chunkX, chunkZ, reservation, settings.spikesWastes());
        }
        if (settings.arches() && selected(region, random, chunkX, chunkZ, settings.arch())) {
            stats.archCandidates.incrementAndGet();
            long start = System.nanoTime();
            ArchPlan.Result r = ArchPlan.place(world(region, random, reservation), random, chunkX, chunkZ,
                    settings.archPortalClearance());
            time(stats.archNanos, stats.archMaxNanos, start);
            if (r.built()) {
                stats.arches.incrementAndGet();
                if (r.healthy()) {
                    stats.healthyArches.incrementAndGet();
                }
                record(Kind.ARCH, r.crownX(), r.crownY(), r.crownZ());
            } else {
                stats.archRejects.merge(r.reason(), 1, Integer::sum);
            }
        }
    }

    private void monolith(GenRegion region, Random random, int chunkX, int chunkZ, PortalReservation reservation, Placement placement,
                          boolean lush) {
        if (!selected(region, random, chunkX, chunkZ, placement)) {
            return;
        }
        stats.monolithCandidates.incrementAndGet();
        long start = System.nanoTime();
        MonolithPlan.Result r = MonolithPlan.place(world(region, random, reservation), random, chunkX, chunkZ, lush,
                settings.monolithPortalClearance(), settings.monolithReserve());
        time(stats.monolithNanos, stats.monolithMaxNanos, start);
        if (r.built()) {
            (lush ? stats.lush : stats.bare).incrementAndGet();
            stats.satellites.addAndGet(r.satellites());
            record(lush ? Kind.LUSH : Kind.BARE, r.topX(), r.topY(), r.topZ());
        } else {
            stats.monolithRejects.merge(r.reason(), 1, Integer::sum);
        }
    }

    private void spikes(GenRegion region, Random random, int chunkX, int chunkZ, PortalReservation reservation, Placement placement) {
        if (!selected(region, random, chunkX, chunkZ, placement)) {
            return;
        }
        stats.spikeCandidates.incrementAndGet();
        long start = System.nanoTime();
        DrySpikePlan.Result r = DrySpikePlan.place(world(region, random, reservation), random, chunkX, chunkZ,
                settings.spikePortalClearance(), settings.spikeReserve());
        time(stats.spikeNanos, stats.spikeMaxNanos, start);
        if (r.built()) {
            stats.spikes.incrementAndGet();
            record(Kind.SPIKE, r.x(), r.topY(), r.z());
        } else {
            stats.spikeRejects.merge(r.reason(), 1, Integer::sum);
        }
    }

    /** rarity_filter, in_square, heightmap WORLD_SURFACE_WG, biome. */
    private static boolean selected(GenRegion region, Random random, int chunkX, int chunkZ, Placement placement) {
        if (random.nextInt(placement.rarity()) != 0) {
            return false;
        }
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        return placement.biomes().contains(SiftPopulator.biome(region, x, z));
    }

    private static void time(AtomicLong total, AtomicLong max, long start) {
        long nanos = System.nanoTime() - start;
        total.addAndGet(nanos);
        max.accumulateAndGet(nanos, Math::max);
    }

    /** The plans' view of the populator region. */
    static LandformWorld world(GenRegion region, Random random, PortalReservation reservation) {
        BlockData air = Material.AIR.createBlockData();
        return new LandformWorld() {
            @Override
            public int height(int x, int z) {
                return region.contains(x, z) ? region.height(HeightMap.WORLD_SURFACE_WG, x, z) : Integer.MIN_VALUE;
            }

            @Override
            public String block(int x, int y, int z) {
                if (y < region.minY() || y >= region.maxY() || !region.region().isInRegion(x, y, z)) {
                    return OTHER;
                }
                if (region.isAir(x, y, z)) {
                    return AIR;
                }
                String id = region.id(x, y, z);
                return id != null ? id : OTHER;
            }

            @Override
            public boolean fluid(int x, int y, int z) {
                if (y < region.minY() || y >= region.maxY() || !region.region().isInRegion(x, y, z)) {
                    return false;
                }
                BlockData data = region.data(x, y, z);
                Material type = data.getMaterial();
                return type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN || type == Material.SEAGRASS
                        || type == Material.TALL_SEAGRASS || type == Material.KELP || type == Material.KELP_PLANT
                        || data instanceof Waterlogged w && w.isWaterlogged();
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
                if (AIR.equals(block)) {
                    region.set(x, y, z, air);
                } else {
                    region.setSift(x, y, z, block);
                }
            }

            @Override
            public void decorate(int x, int floorY, int z, float density) {
                SurfacePlants.decorate(region, random, x, floorY, z, density);
            }

            @Override
            public boolean nearPortal(int x, int z, int radius) {
                return reservation.intersects(x, z, radius);
            }

            @Override
            public boolean reserve(int minX, int minZ, int maxX, int maxZ) {
                return reservation.tryReserveSolid(minX, minZ, maxX, maxZ);
            }

            @Override
            public int minY() {
                return region.minY();
            }

            @Override
            public int maxY() {
                return region.maxY();
            }
        };
    }

    // ---- records (for /sift debug landmarks) ----------------------------------------------------------------

    private void record(Kind kind, int x, int y, int z) {
        records.get(kind).add(SoulCanyonLandmarks.pack(x, y, z));
        dirty = true;
    }

    public void load(World world) {
        for (Kind kind : Kind.values()) {
            long[] stored = world.getPersistentDataContainer().get(kind.key(), PersistentDataType.LONG_ARRAY);
            if (stored != null) {
                for (long packed : stored) {
                    records.get(kind).add(packed);
                }
            }
        }
    }

    /** Main thread only. */
    public void save(World world) {
        if (!dirty) {
            return;
        }
        dirty = false;
        for (Kind kind : Kind.values()) {
            world.getPersistentDataContainer().set(kind.key(), PersistentDataType.LONG_ARRAY,
                    records.get(kind).stream().mapToLong(Long::longValue).toArray());
        }
    }

    public int count(Kind kind) {
        return records.get(kind).size();
    }

    /** A recorded landform: the top of a monolith's cap or of an arch's middle. */
    public record Found(Kind kind, SoulCanyonLandmarks.Pos top) {
    }

    /** Nearest recorded landforms of these kinds to x/z. */
    public List<Found> nearest(List<Kind> kinds, double x, double z, int limit) {
        List<Found> all = new ArrayList<>();
        for (Kind kind : kinds) {
            for (long packed : records.get(kind)) {
                all.add(new Found(kind, SoulCanyonLandmarks.unpack(packed)));
            }
        }
        all.sort(Comparator.comparingDouble(f -> f.top().horizontalDistanceSquared(x, z)));
        return all.subList(0, Math.min(limit, all.size()));
    }
}
