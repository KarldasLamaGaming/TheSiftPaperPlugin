package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.PlantSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.HeightMap;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * The worldgen features of the original that are Java code there (step 6): willow trees, flower and
 * plant patches, surface sculk and abandoned portals. Runs for every new Sift chunk after the datapack
 * features, in the order of the original (abandoned portal, trees, flowers, plants, then sculk).
 */
public final class SiftPopulator extends BlockPopulator {

    private final CustomBlockRegistry registry;
    private final PlantSupport support;
    private final WorldgenSettings settings;
    private final WillowTrees willows;
    private final AbandonedPortals portals;
    private final SurfaceSculk sculk = new SurfaceSculk();
    private final Logger logger;
    private final Stats stats = new Stats();

    /** Counters for /sift debug worldgen. */
    public static final class Stats {
        public final AtomicLong chunks = new AtomicLong();
        public final AtomicLong trees = new AtomicLong();
        public final AtomicLong flowerPatches = new AtomicLong();
        public final AtomicLong plantPatches = new AtomicLong();
        public final AtomicLong sculkChunks = new AtomicLong();
        public final AtomicLong abandonedPortals = new AtomicLong();
        public final AtomicLong errors = new AtomicLong();
        public final java.util.Map<String, Integer> portalRejects = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public SiftPopulator(CustomBlockRegistry registry, PlantSupport support, WorldgenSettings settings, SiftTemplates templates,
                         Logger logger) {
        this.registry = registry;
        this.support = support;
        this.settings = settings;
        this.willows = new WillowTrees(templates, settings.bigWillowChance());
        this.portals = new AbandonedPortals(templates, settings.portalCellChunks(), settings.portalChestLoot(), logger);
        this.logger = logger;
    }

    /** Templates the populator needs (loaded on the main thread before generation starts). */
    public static List<String> templateNames() {
        List<String> names = new ArrayList<>(AbandonedPortals.templateNames());
        names.add(WillowTrees.SMALL);
        names.add(WillowTrees.BIG);
        return names;
    }

    /** Chests of abandoned portals that got their loot table. */
    public long lootChests() {
        return portals.lootChests.get();
    }

    public Stats stats() {
        return stats;
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limited) {
        stats.chunks.incrementAndGet();
        try {
            GenRegion region = new GenRegion(limited, registry, support, chunkX, chunkZ, worldInfo.getMinHeight(),
                    worldInfo.getMaxHeight());
            populate(region, random, chunkX, chunkZ, worldInfo.getSeed());
        } catch (RuntimeException e) {
            // one broken chunk must not stop world generation
            if (stats.errors.incrementAndGet() <= 5) {
                logger.log(Level.WARNING, "Sift worldgen failed in chunk " + chunkX + " " + chunkZ, e);
            }
        }
    }

    private void populate(GenRegion region, Random random, int chunkX, int chunkZ, long seed) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        String biome = biome(region, centerX, centerZ);
        boolean overgrown = biome.startsWith("overgrown_");

        if (settings.abandonedPortals() && portals.isSelected(seed, chunkX, chunkZ)) {
            String rejected = !settings.portalBiomes().contains(biome) ? "Biom " + biome
                    : portals.place(region, random, chunkX, chunkZ, overgrown || biome.equals("ichor_snowy_peaks"), seed);
            if (rejected == null) {
                stats.abandonedPortals.incrementAndGet();
            } else {
                stats.portalRejects.merge(rejected, 1, Integer::sum);
            }
        }
        if (settings.willowTrees()) {
            if (settings.denseWillowBiomes().contains(biome)) {
                for (int i = 0; i < settings.denseWillowCount(); i++) {
                    tree(region, random, chunkX, chunkZ);
                }
            } else if (settings.sparseWillowBiomes().contains(biome) && random.nextInt(settings.sparseWillowRarity()) == 0) {
                tree(region, random, chunkX, chunkZ);
            }
        }
        if (settings.flowerPatches()) {
            int rarity = settings.wastesFlowerBiomes().contains(biome) ? settings.wastesFlowerRarity()
                    : settings.flowerBiomes().contains(biome) ? settings.flowerRarity() : 0;
            if (rarity > 0 && random.nextInt(rarity) == 0 && SurfacePlants.flowerPatch(region, random, chunkX, chunkZ, overgrown)) {
                stats.flowerPatches.incrementAndGet();
            }
        }
        if (settings.plantPatches() && settings.plantBiomes().contains(biome) && random.nextInt(settings.plantRarity()) == 0
                && SurfacePlants.plantPatch(region, random, chunkX, chunkZ)) {
            stats.plantPatches.incrementAndGet();
        }
        if (settings.surfaceSculk() && anyBiome(region, chunkX, chunkZ, settings.sculkBiomes())
                && sculk.place(region, seed, chunkX, chunkZ)) {
            stats.sculkChunks.incrementAndGet();
        }
    }

    private void tree(GenRegion region, Random random, int chunkX, int chunkZ) {
        if (willows.place(region, random, chunkX, chunkZ)) {
            stats.trees.incrementAndGet();
        }
    }

    private static String biome(GenRegion region, int x, int z) {
        int y = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
        var key = region.region().getBiome(x, Math.min(y, region.maxY() - 1), z).getKey();
        return key.getNamespace().equals("the_sift") ? key.getKey() : key.toString();
    }

    /** Vanilla runs a biome's features when that biome occurs in the chunk; sample center and corners. */
    private static boolean anyBiome(GenRegion region, int chunkX, int chunkZ, Set<String> biomes) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int[][] points = {{8, 8}, {0, 0}, {15, 0}, {0, 15}, {15, 15}};
        for (int[] p : points) {
            if (biomes.contains(biome(region, minX + p[0], minZ + p[1]))) {
                return true;
            }
        }
        return false;
    }
}
