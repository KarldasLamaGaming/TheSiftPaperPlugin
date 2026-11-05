package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.PlantSupport;
import de.lamamc.sift.core.worldgen.AbandonedPortalPlan;
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
        /** Soul canyons: candidates, built, and the time spent on them (planning and carving). */
        public final AtomicLong canyonCandidates = new AtomicLong();
        public final AtomicLong canyons = new AtomicLong();
        public final AtomicLong canyonNanos = new AtomicLong();
        public final AtomicLong canyonMaxNanos = new AtomicLong();
        public final java.util.Map<String, Integer> canyonRejects = new java.util.concurrent.ConcurrentHashMap<>();
        public final AtomicLong errors = new AtomicLong();
        public final java.util.Map<String, Integer> portalRejects = new java.util.concurrent.ConcurrentHashMap<>();
    }

    private final PortalReservation reservation;
    private final LandmarkLog log;
    private final SoulCanyons canyons;
    private final IchorWaters waters;
    private IchorSnow snow;
    private Landforms landforms;

    public SiftPopulator(CustomBlockRegistry registry, PlantSupport support, WorldgenSettings settings, SiftTemplates templates,
                         PortalReservation reservation, LandmarkLog log, SoulCanyons canyons, IchorWaters waters, Logger logger) {
        this.waters = waters;
        this.reservation = reservation;
        this.canyons = canyons;
        this.log = log;
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
        names.add(de.lamamc.sift.world.MainPortalBuilder.TEMPLATE);
        return names;
    }

    /** Chests of abandoned portals that got their loot table. */
    public long lootChests() {
        return portals.lootChests.get();
    }

    public LandmarkLog log() {
        return log;
    }

    /** Ichor snow on the ichor snowy peaks (alpha.26); null = off. */
    public void snow(IchorSnow snow) {
        this.snow = snow;
    }

    public IchorSnow snow() {
        return snow;
    }

    /** Monoliths and cliff arches (alpha.27); null = off. */
    public void landforms(Landforms landforms) {
        this.landforms = landforms;
    }

    public Landforms landforms() {
        return landforms;
    }

    public IchorWaters waters() {
        return waters;
    }

    public SoulCanyons canyons() {
        return canyons;
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
            int surfaceY = region.height(HeightMap.WORLD_SURFACE_WG, centerX, centerZ) - 1;
            LandmarkLog.Entry entry = !settings.portalBiomes().contains(biome)
                    ? AbandonedPortals.rejected(chunkX, chunkZ, centerX, surfaceY, centerZ, "Biom " + biome + " ohne verlassene Portale")
                    : reservation.intersects(centerX, centerZ, 28)
                    ? AbandonedPortals.rejected(chunkX, chunkZ, centerX, surfaceY, centerZ, "zu nah am Haupt-Portal")
                    : !AbandonedPortalPlan.passesChance(seed, chunkX, chunkZ, settings.portalChance())
                    ? AbandonedPortals.rejected(chunkX, chunkZ, centerX, surfaceY, centerZ,
                    "nicht ausgewählt (chance " + settings.portalChance() + ")")
                    : portals.place(region, random, chunkX, chunkZ, overgrown || biome.equals("ichor_snowy_peaks"), seed,
                    reservation);
            log.record(entry);
            if (entry.status() == LandmarkLog.Status.BUILT) {
                stats.abandonedPortals.incrementAndGet();
            } else {
                stats.portalRejects.merge(entry.reason().replaceAll(" \\(.*", "").replaceAll(" durch .*", ""), 1, Integer::sum);
            }
        }
        if (settings.soulCanyons() && canyons.isSelected(seed, chunkX, chunkZ)) {
            canyon(region, random, chunkX, chunkZ);
        }
        if (waters != null) {
            // original order: fluid springs (step 8), then surface lakes (step 9, before the trees)
            waters.springs(region, random, chunkX, chunkZ);
            waters.lake(region, random, chunkX, chunkZ, biome, reservation,
                    (r, rand, x, y, z) -> !reservation.intersectsBuilt(x, z, 12) && willows.placeAt(r, rand, x, y, z, false));
        }
        if (landforms != null) {
            // original order: lush monolith, bare monolith, (dry sculk spikes,) cliff arch, then the willows
            landforms.place(region, random, chunkX, chunkZ, reservation);
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
        if (snow != null) {
            snow.place(region, seed, chunkX, chunkZ); // top_layer_modification: last
        }
    }

    /** Soul canyon of this cell (feature step underground_decoration, after the abandoned portals). */
    private void canyon(GenRegion region, Random random, int chunkX, int chunkZ) {
        stats.canyonCandidates.incrementAndGet();
        long start = System.nanoTime();
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        SoulCanyons.Outcome outcome;
        if (!anyBiome(region, chunkX, chunkZ, settings.canyonBiomes())) {
            outcome = SoulCanyons.Outcome.BIOME;
        } else if (reservation.intersects(centerX, centerZ, settings.canyonPortalClearance())) {
            outcome = SoulCanyons.Outcome.PORTAL;
        } else {
            outcome = canyons.build(SoulCanyons.of(region, chunkX, chunkZ), centerX, centerZ, random, reservation).outcome();
        }
        long nanos = System.nanoTime() - start;
        stats.canyonNanos.addAndGet(nanos);
        stats.canyonMaxNanos.accumulateAndGet(nanos, Math::max);
        if (outcome == SoulCanyons.Outcome.BUILT) {
            stats.canyons.incrementAndGet();
        } else {
            stats.canyonRejects.merge(outcome.name(), 1, Integer::sum);
        }
    }

    private void tree(GenRegion region, Random random, int chunkX, int chunkZ) {
        if (willows.place(region, random, chunkX, chunkZ, reservation)) {
            stats.trees.incrementAndGet();
        }
    }

    static String biome(GenRegion region, int x, int z) {
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
