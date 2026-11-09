package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.IchorLakePlan;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Surface ichor lakes and cave springs of the original 1.1.1 (alpha.25, config worldgen.ichor-lakes.* and
 * worldgen.ichor-springs.*), built by the Sift populator with {@link IchorLakePlan}. Ichor is water in the
 * Sift; generated lakes and springs get light blocks (level 12) for the glow of the original. Since alpha.36 the
 * lights sit in the air above the ichor: a waterlogged light is a water source that cannot be seen or taken
 * apart, so ichor kept flowing after the visible source was gone. Lights of older chunks (waterlogged) still go
 * when their ichor is taken away (bucket, sponge).
 */
public final class IchorWaters implements Listener {

    public static final int LIGHT_LEVEL = 12;
    private static final NamespacedKey LAKES = new NamespacedKey("the_sift", "ichor_lakes");
    private static final NamespacedKey SPRINGS = new NamespacedKey("the_sift", "ichor_springs");

    /** Settings (worldgen.ichor-lakes / worldgen.ichor-springs). */
    public record Settings(boolean lakes, Set<String> lakeBiomes, int lakeRarity, Set<String> clearingBiomes, int clearingRarity,
                           int lightSpacing, boolean shorePlants, boolean springs, int springCount, int springMinY,
                           int springMaxY, int springBelowSurface, boolean springLight) {

        public static Settings load(ConfigurationSection worldgen) {
            ConfigurationSection w = worldgen == null ? new org.bukkit.configuration.MemoryConfiguration() : worldgen;
            return new Settings(w.getBoolean("ichor-lakes.enabled", true),
                    Set.copyOf(w.getStringList("ichor-lakes.biomes")), Math.max(1, w.getInt("ichor-lakes.rarity", 3)),
                    Set.copyOf(w.getStringList("ichor-lakes.clearing-biomes")), Math.max(1, w.getInt("ichor-lakes.clearing-rarity", 2)),
                    Math.max(0, w.getInt("ichor-lakes.light-spacing", 3)), w.getBoolean("ichor-lakes.shore-plants", true),
                    w.getBoolean("ichor-springs.enabled", true), Math.max(0, w.getInt("ichor-springs.count", 16)),
                    w.getInt("ichor-springs.min-y", 15), w.getInt("ichor-springs.max-y", 110),
                    Math.max(0, w.getInt("ichor-springs.below-surface", 12)), w.getBoolean("ichor-springs.light", true));
        }
    }

    /** Counters for /sift debug worldgen and /sift debug ichor. */
    public static final class Stats {
        public final AtomicLong lakeCandidates = new AtomicLong();
        public final AtomicLong lakes = new AtomicLong();
        public final AtomicLong lakeNanos = new AtomicLong();
        public final AtomicLong lakeMaxNanos = new AtomicLong();
        public final Map<String, Integer> lakeRejects = new ConcurrentHashMap<>();
        public final Map<String, Integer> lakeVariants = new ConcurrentHashMap<>();
        public final AtomicLong springs = new AtomicLong();
        public final AtomicLong springNanos = new AtomicLong();
        public final AtomicLong springChunks = new AtomicLong();
        public final AtomicLong lights = new AtomicLong();
        public final AtomicLong lightsRemoved = new AtomicLong();
        public final AtomicLong islands = new AtomicLong();
    }

    private final Settings settings;
    private final Stats stats = new Stats();
    private final Set<Long> lakes = ConcurrentHashMap.newKeySet();
    private final Set<Long> springs = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;
    private Plugin plugin;

    public IchorWaters(Settings settings) {
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public Stats stats() {
        return stats;
    }

    // ---- lakes ----------------------------------------------------------------------------------------------

    /** Surface lake of this chunk (feature step vegetal_decoration, before the trees). */
    /** Places a willow with its root at a block (the willow island of a large overgrown lake). */
    public interface Trees {
        boolean placeAt(GenRegion region, Random random, int x, int y, int z);
    }

    public void lake(GenRegion region, Random random, int chunkX, int chunkZ, String biome, PortalReservation reservation, Trees trees) {
        int rarity = settings.lakeBiomes().contains(biome) ? settings.lakeRarity()
                : settings.clearingBiomes().contains(biome) ? settings.clearingRarity() : 0;
        if (!settings.lakes() || rarity == 0 || random.nextInt(rarity) != 0) {
            return;
        }
        stats.lakeCandidates.incrementAndGet();
        long start = System.nanoTime();
        String reason;
        IchorLakePlan.Result plan = IchorLakePlan.plan(world(region, reservation, biome.startsWith("overgrown_")), random, chunkX, chunkZ);
        if (!plan.built()) {
            reason = plan.reason();
        } else if (!reservation.tryReserveSolid(plan.box()[0], plan.box()[1], plan.box()[2], plan.box()[3])) {
            reason = "Platz reserviert";
        } else {
            apply(region, random, plan);
            if (plan.islandRoot() != null && trees != null) {
                int[] r = plan.islandRoot();
                if (trees.placeAt(region, random, r[0], r[1], r[2])) {
                    stats.islands.incrementAndGet();
                }
            }
            reason = null;
        }
        long nanos = System.nanoTime() - start;
        stats.lakeNanos.addAndGet(nanos);
        stats.lakeMaxNanos.accumulateAndGet(nanos, Math::max);
        if (reason == null) {
            stats.lakes.incrementAndGet();
            stats.lakeVariants.merge(plan.kind(), 1, Integer::sum);
            int[] c = plan.waterCells().get(plan.waterCells().size() / 2);
            lakes.add(SoulCanyonLandmarks.pack(c[0], c[1], c[2]));
            dirty = true;
        } else {
            // per variant, so it shows which terrain checks stop the big lakes (alpha.25.1)
            stats.lakeRejects.merge(plan.kind() + " " + reason, 1, Integer::sum);
        }
    }

    private void apply(GenRegion region, Random random, IchorLakePlan.Result plan) {
        BlockData water = Material.WATER.createBlockData();
        org.bukkit.block.data.Levelled falling = (org.bukkit.block.data.Levelled) Material.WATER.createBlockData();
        falling.setLevel(8); // falling ichor of the cascade curtain
        for (IchorLakePlan.Edit e : plan.edits()) {
            if (!region.writable(e.x(), e.y(), e.z())) {
                continue;
            }
            switch (e.block()) {
                case IchorLakePlan.CLEAR -> {
                    if (clearable(region, e.x(), e.y(), e.z())) {
                        region.set(e.x(), e.y(), e.z(), Material.AIR.createBlockData());
                    }
                }
                case IchorLakePlan.ICHOR -> region.set(e.x(), e.y(), e.z(), water);
                case IchorLakePlan.AIR -> region.set(e.x(), e.y(), e.z(), Material.AIR.createBlockData());
                case IchorLakePlan.FALLING -> region.set(e.x(), e.y(), e.z(), falling);
                default -> region.setSift(e.x(), e.y(), e.z(), e.block());
            }
        }
        int spacing = settings.lightSpacing();
        if (spacing > 0) {
            for (int[] cell : plan.waterCells()) {
                // the light goes into the air above a surface source, never into the ichor itself
                if (Math.floorMod(cell[0], spacing) == 0 && Math.floorMod(cell[2], spacing) == 0
                        && region.writable(cell[0], cell[1] + 1, cell[2])
                        && region.data(cell[0], cell[1], cell[2]) instanceof org.bukkit.block.data.Levelled level && level.getLevel() == 0
                        && region.type(cell[0], cell[1], cell[2]) == Material.WATER && region.type(cell[0], cell[1] + 1, cell[2]).isAir()) {
                    region.set(cell[0], cell[1] + 1, cell[2], light());
                    stats.lights.incrementAndGet();
                }
            }
        }
        if (settings.shorePlants()) {
            for (int[] floor : plan.shoreFloors()) {
                plant(region, random, floor[0], floor[1], floor[2]);
            }
        }
    }

    /** Air, Sift ground, small plants, ichor and ichor snow (SiftMonolithFeature#canReplace + ichor + snow). */
    private static boolean clearable(GenRegion region, int x, int y, int z) {
        Material type = region.type(x, y, z);
        if (type.isAir() || type == Material.WATER || type == Material.SNOW || type == Material.SNOW_BLOCK) {
            return true;
        }
        String id = region.id(x, y, z);
        return id != null && (GenRegion.TERRAIN.contains(id) || GenRegion.SMALL_PLANTS.contains(id) || id.startsWith("ichor_snow"));
    }

    /** SiftSurfaceDecorator#decorateLakeShore without the flower batches: small plants by floor. */
    private static void plant(GenRegion region, Random random, int x, int floorY, int z) {
        if (!region.writable(x, floorY + 1, z) || !region.isAir(x, floorY + 1, z)) {
            return;
        }
        String floor = region.id(x, floorY, z);
        if (floor == null) {
            return;
        }
        boolean growth = floor.equals("siftslate_growth") || floor.equals("dry_healthy_sculk_growth");
        double chance = growth ? 0.68 : floor.equals("healthy_sculk") ? 0.6 : floor.equals("dry_healthy_sculk") ? 0.5
                : floor.equals("siftslate") ? 0.45 : 0;
        if (random.nextFloat() >= chance) {
            return;
        }
        float roll = random.nextFloat();
        String plant = growth ? (roll < 0.38f ? "overgrown_fronds" : roll < 0.72f ? "overgrown_stalks" : "overgrown_chard")
                : floor.equals("siftslate") ? (roll < 0.82f ? "siftslate_stalks" : "healthy_sculk_sprouts")
                : floor.equals("healthy_sculk") ? (roll < 0.82f ? "healthy_sculk_sprouts" : "siftslate_stalks")
                : "dry_healthy_sculk_sprouts";
        if (region.plantFits(plant, x, floorY + 1, z)) {
            region.setSift(x, floorY + 1, z, plant);
        }
    }

    private static IchorLakePlan.World world(GenRegion region, PortalReservation reservation, boolean overgrown) {
        return new IchorLakePlan.World() {
            @Override
            public int surface(int x, int z) {
                if (!region.contains(x, z)) {
                    return -1;
                }
                int y = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
                for (int step = 0; step < 8 && y > region.minY(); step++, y--) {
                    if (region.isTerrain(x, y - 1, z)) {
                        return y;
                    }
                }
                return -1;
            }

            @Override
            public boolean terrain(int x, int y, int z) {
                return region.contains(x, z) && region.isTerrain(x, y, z);
            }

            @Override
            public boolean ichor(int x, int y, int z) {
                return region.contains(x, z) && region.type(x, y, z) == Material.WATER;
            }

            @Override
            public boolean contains(int x, int z) {
                return region.contains(x, z);
            }

            @Override
            public boolean nearPortal(int minX, int minZ, int maxX, int maxZ) {
                int half = Math.max(maxX - minX, maxZ - minZ) / 2 + 1;
                return reservation.intersects((minX + maxX) / 2, (minZ + maxZ) / 2, half);
            }

            @Override
            public boolean overgrown() {
                return overgrown;
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

    // ---- springs --------------------------------------------------------------------------------------------

    /** Cave springs of this chunk: 16 tries, y 15–110, at least 12 below the surface (feature step fluid_springs). */
    public void springs(GenRegion region, Random random, int chunkX, int chunkZ) {
        if (!settings.springs() || settings.springCount() == 0) {
            return;
        }
        stats.springChunks.incrementAndGet();
        long start = System.nanoTime();
        IchorLakePlan.SpringWorld w = new IchorLakePlan.SpringWorld() {
            @Override
            public boolean rock(int x, int y, int z) {
                return region.contains(x, z) && region.isTerrain(x, y, z);
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return region.contains(x, z) && region.isAir(x, y, z);
            }

            @Override
            public int surface(int x, int z) {
                return region.height(HeightMap.WORLD_SURFACE_WG, x, z);
            }
        };
        int range = Math.max(1, settings.springMaxY() - settings.springMinY() + 1);
        for (int i = 0; i < settings.springCount(); i++) {
            int x = (chunkX << 4) + random.nextInt(16);
            int z = (chunkZ << 4) + random.nextInt(16);
            int y = settings.springMinY() + random.nextInt(range);
            if (y > region.height(HeightMap.WORLD_SURFACE_WG, x, z) - settings.springBelowSurface() || !region.writable(x, y, z)
                    || !IchorLakePlan.spring(w, x, y, z)) {
                continue;
            }
            region.set(x, y, z, Material.WATER.createBlockData());
            int[] glow = settings.springLight() ? IchorLakePlan.springGlow(w, x, y, z) : null;
            if (glow != null && region.writable(glow[0], glow[1], glow[2])) {
                region.set(glow[0], glow[1], glow[2], light());
                stats.lights.incrementAndGet();
            }
            stats.springs.incrementAndGet();
            springs.add(SoulCanyonLandmarks.pack(x, y, z));
            dirty = true;
        }
        stats.springNanos.addAndGet(System.nanoTime() - start);
    }

    /** The glow of generated ichor: a dry light block of level 12 (never waterlogged, see the class comment). */
    static BlockData light() {
        Light light = (Light) Material.LIGHT.createBlockData();
        light.setLevel(LIGHT_LEVEL);
        light.setWaterlogged(false);
        return light;
    }

    // ---- records (for /sift debug ichor) -------------------------------------------------------------------

    public void load(World world) {
        for (var entry : List.of(Map.entry(LAKES, lakes), Map.entry(SPRINGS, springs))) {
            long[] stored = world.getPersistentDataContainer().get(entry.getKey(), PersistentDataType.LONG_ARRAY);
            if (stored != null) {
                for (long packed : stored) {
                    entry.getValue().add(packed);
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
        world.getPersistentDataContainer().set(LAKES, PersistentDataType.LONG_ARRAY, lakes.stream().mapToLong(Long::longValue).toArray());
        world.getPersistentDataContainer().set(SPRINGS, PersistentDataType.LONG_ARRAY, springs.stream().mapToLong(Long::longValue).toArray());
    }

    public int lakeCount() {
        return lakes.size();
    }

    public int springCount() {
        return springs.size();
    }

    /** Nearest recorded lakes or springs to x/z. */
    public List<SoulCanyonLandmarks.Pos> nearest(boolean ofLakes, double x, double z, int limit) {
        List<SoulCanyonLandmarks.Pos> all = new ArrayList<>();
        for (long packed : ofLakes ? lakes : springs) {
            all.add(SoulCanyonLandmarks.unpack(packed));
        }
        all.sort(Comparator.comparingDouble(p -> p.horizontalDistanceSquared(x, z)));
        return all.subList(0, Math.min(limit, all.size()));
    }

    // ---- the light goes with its ichor ----------------------------------------------------------------------

    public void listen(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (SiftWorlds.isSift(block.getWorld()) && block.getType() == Material.LIGHT) {
            Bukkit.getScheduler().runTask(plugin, () -> dryLight(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        if (!SiftWorlds.isSift(event.getBlock().getWorld())) {
            return;
        }
        List<Block> blocks = event.getBlocks().stream().map(BlockState::getBlock).toList();
        Bukkit.getScheduler().runTask(plugin, () -> blocks.forEach(this::dryLight));
    }

    /** A light block of ours that lost its ichor (not waterlogged any more) becomes air. */
    private void dryLight(Block block) {
        if (block.getBlockData() instanceof Light light && light.getLevel() == LIGHT_LEVEL && !light.isWaterlogged()) {
            block.setType(Material.AIR, false);
            stats.lightsRemoved.incrementAndGet();
        }
    }
}
