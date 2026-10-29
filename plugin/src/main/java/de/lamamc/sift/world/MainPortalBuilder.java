package de.lamamc.sift.world;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.worldgen.MainPortalPlan;
import de.lamamc.sift.world.gen.PortalReservation;
import de.lamamc.sift.world.gen.SiftTemplates;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Builds the main Sift portal once, near 0/0 of the Sift (SiftPortalStructure#ensurePortal of the
 * original): finds the flattest site, blends the terrain into a natural foundation with cliffs at both
 * ends, places the template unrotated and remembers the arrival point in the world's persistent data.
 * The portal surface and travel come with step 7.
 */
public final class MainPortalBuilder {

    public static final NamespacedKey ANCHOR = new NamespacedKey("the_sift", "portal_anchor");
    public static final NamespacedKey ORIGIN = new NamespacedKey("the_sift", "portal_origin");
    public static final String TEMPLATE = "main_portal";
    /** Square around 0/0 that the site search may use, reserved for the portal until it is built. */
    public static final int RESERVED_RADIUS = MainPortalPlan.SEARCH_RADIUS + MainPortalPlan.REFINE_RADIUS
            + MainPortalPlan.SIZE_Z + MainPortalPlan.FEATURE_CLEAR_MARGIN + 28;

    private static final Set<String> TERRAIN = Set.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk");
    private static final Set<String> PLANTS = Set.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds",
            "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts", "overgrown_willow_foliage",
            "overgrown_willow_log", "overgrown_willow_wood");

    private final Plugin plugin;
    private final CustomBlockRegistry registry;
    private final SiftTemplates templates;
    private final PortalReservation reservation;
    private final Logger logger;
    private volatile boolean building;

    public MainPortalBuilder(Plugin plugin, CustomBlockRegistry registry, SiftTemplates templates, PortalReservation reservation) {
        this.plugin = plugin;
        this.registry = registry;
        this.templates = templates;
        this.reservation = reservation;
        this.logger = plugin.getLogger();
    }

    /** Reserves the portal area for worldgen: the built footprint, or the search area until it exists. */
    public void reserve(World sift) {
        int[] origin = sift.getPersistentDataContainer().get(ORIGIN, PersistentDataType.INTEGER_ARRAY);
        if (origin != null && origin.length == 3) {
            reservation.reserveFootprint(origin[0], origin[2], MainPortalPlan.SIZE_X, MainPortalPlan.SIZE_Z,
                    MainPortalPlan.FEATURE_CLEAR_MARGIN);
        } else {
            reservation.reserveSearchArea(RESERVED_RADIUS);
        }
    }

    /** The arrival point of the main portal, or null if it is not built yet. */
    public static Location anchor(World sift) {
        int[] xyz = sift.getPersistentDataContainer().get(ANCHOR, PersistentDataType.INTEGER_ARRAY);
        return xyz == null || xyz.length != 3 ? null : new Location(sift, xyz[0] + 0.5, xyz[1], xyz[2] + 0.5);
    }

    public boolean building() {
        return building;
    }

    /** Starts building if the portal does not exist yet; the chunks are generated asynchronously first. */
    public void ensure(World sift) {
        if (anchor(sift) != null || building) {
            return;
        }
        if (templates.get(TEMPLATE) == null) {
            logger.severe("Main portal template the_sift:" + TEMPLATE + " is missing; the portal is not built");
            return;
        }
        building = true;
        Set<Long> searchChunks = new HashSet<>();
        int reach = MainPortalPlan.REFINE_RADIUS + MainPortalPlan.OUTER_SAMPLE_MARGIN + MainPortalPlan.SIZE_Z;
        for (int x = -MainPortalPlan.SEARCH_RADIUS - reach; x <= MainPortalPlan.SEARCH_RADIUS + reach; x += 16) {
            for (int z = -MainPortalPlan.SEARCH_RADIUS - reach; z <= MainPortalPlan.SEARCH_RADIUS + reach; z += 16) {
                searchChunks.add(chunkKey(x >> 4, z >> 4));
            }
        }
        logger.info("Selecting a location for the main Sift portal (generating " + searchChunks.size() + " chunks once)");
        load(sift, searchChunks).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            MainPortalPlan.Site site = MainPortalPlan.findSite((x, z) -> terrainSurfaceY(sift, x, z));
            Set<Long> buildChunks = new HashSet<>();
            int margin = MainPortalPlan.BLEND_RADIUS + 1;
            for (int x = site.originX() - margin; x <= site.originX() + MainPortalPlan.SIZE_X + margin; x += 8) {
                for (int z = site.originZ() - margin; z <= site.originZ() + MainPortalPlan.SIZE_Z + margin; z += 8) {
                    buildChunks.add(chunkKey(x >> 4, z >> 4));
                }
            }
            load(sift, buildChunks).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    build(sift, site);
                } finally {
                    building = false;
                }
            }));
        }));
    }

    private CompletableFuture<Void> load(World world, Set<Long> chunks) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (long key : chunks) {
            futures.add(world.getChunkAtAsync((int) (key >> 32), (int) key, true));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private void build(World world, MainPortalPlan.Site site) {
        long seed = world.getSeed();
        int originX = site.originX();
        int originY = site.originY();
        int originZ = site.originZ();
        clearFeatures(world, originX, originZ);
        blendTerrain(world, originX, originY, originZ, seed);
        sealFoundation(world, originX, originY, originZ);

        BlockData reinforced = registry.defaultState("reinforced_siftslate").data();
        for (SiftTemplates.Block block : templates.get(TEMPLATE).blocks()) {
            BlockData data = block.data().getMaterial() == Material.JIGSAW ? reinforced : block.data();
            set(world.getBlockAt(originX + block.x(), originY + block.y(), originZ + block.z()), data);
        }
        int anchorX = originX + MainPortalPlan.MARKER_X;
        int anchorZ = originZ + MainPortalPlan.MARKER_Z;
        set(world.getBlockAt(anchorX, originY + MainPortalPlan.MARKER_Y, anchorZ), reinforced);
        normalizeCoveredGrowth(world, originX, originY, originZ);
        int anchorY = originY + MainPortalPlan.PLAYER_Y;
        decorate(world, originX, originY, originZ, anchorX, anchorZ, new Random(seed ^ 22601841507586892L));
        world.getPersistentDataContainer().set(ANCHOR, PersistentDataType.INTEGER_ARRAY, new int[] {anchorX, anchorY, anchorZ});
        world.getPersistentDataContainer().set(ORIGIN, PersistentDataType.INTEGER_ARRAY, new int[] {originX, originY, originZ});
        reservation.reserveFootprint(originX, originZ, MainPortalPlan.SIZE_X, MainPortalPlan.SIZE_Z, MainPortalPlan.FEATURE_CLEAR_MARGIN);
        logger.info("Main Sift portal ready at " + anchorX + " " + anchorY + " " + anchorZ);
    }

    /** Trees and plants that grew above the natural surface around the footprint. */
    private void clearFeatures(World world, int originX, int originZ) {
        int m = MainPortalPlan.FEATURE_CLEAR_MARGIN;
        Material air = Material.AIR;
        for (int x = originX - m; x <= originX + MainPortalPlan.SIZE_X - 1 + m; x++) {
            for (int z = originZ - m; z <= originZ + MainPortalPlan.SIZE_Z - 1 + m; z++) {
                int natural = terrainSurfaceY(world, x, z);
                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                for (int y = top; y > natural; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    if (!isTerrain(block) && !isPlant(block)) {
                        break;
                    }
                    set(block, air.createBlockData());
                }
            }
        }
    }

    private void blendTerrain(World world, int originX, int originY, int originZ, long seed) {
        buildFoundation(world, originX, originY, originZ, seed);
        int r = MainPortalPlan.BLEND_RADIUS;
        int maxX = originX + MainPortalPlan.SIZE_X - 1;
        int maxZ = originZ + MainPortalPlan.SIZE_Z - 1;
        for (int x = originX - r; x <= maxX + r; x++) {
            for (int z = originZ - r; z <= maxZ + r; z++) {
                int localX = x - originX;
                int localZ = z - originZ;
                boolean inside = localX >= 0 && localX < MainPortalPlan.SIZE_X && localZ >= 0 && localZ < MainPortalPlan.SIZE_Z;
                int naturalY = terrainSurfaceY(world, x, z);
                if (naturalY <= 1 || naturalY >= world.getMaxHeight() - 2) {
                    continue;
                }
                String originalTop = id(world.getBlockAt(x, naturalY, z));
                if (originalTop == null || !TERRAIN.contains(originalTop)) {
                    continue;
                }
                if (inside) {
                    int relative = MainPortalPlan.groundProfile(localX, localZ);
                    if (relative >= 0) {
                        int desired = Math.clamp(originY + relative, 2, world.getMaxHeight() - 3);
                        if (naturalY > desired) {
                            lowerColumn(world, x, z, naturalY, desired);
                        }
                    }
                } else if (MainPortalPlan.distanceOutsideRectangle(x, z, originX, maxX, originZ, maxZ) <= r) {
                    double envelope = MainPortalPlan.envelopeRelativeY(localX, localZ, seed);
                    if (envelope > 0) {
                        int desired = Math.clamp(originY + (int) Math.floor(envelope), 2, world.getMaxHeight() - 3);
                        if (desired > naturalY) {
                            raiseColumn(world, x, z, naturalY, desired, originalTop, true);
                        }
                    }
                }
            }
        }
    }

    private void buildFoundation(World world, int originX, int originY, int originZ, long seed) {
        int top = Math.max(2, originY - 1);
        int m = MainPortalPlan.FOUNDATION_BLEND_RADIUS;
        for (int x = originX - m; x <= originX + MainPortalPlan.SIZE_X - 1 + m; x++) {
            for (int z = originZ - m; z <= originZ + MainPortalPlan.SIZE_Z - 1 + m; z++) {
                int localX = x - originX;
                int localZ = z - originZ;
                int target = MainPortalPlan.foundationTargetY(localX, localZ, seed, top);
                if (target == Integer.MIN_VALUE) {
                    continue;
                }
                int naturalY = terrainSurfaceY(world, x, z);
                if (naturalY > 1 && naturalY < target) {
                    String originalTop = id(world.getBlockAt(x, naturalY, z));
                    if (originalTop != null && TERRAIN.contains(originalTop)) {
                        raiseColumn(world, x, z, naturalY, target, originalTop,
                                MainPortalPlan.foundationKeepsSurface(localX, localZ, seed));
                    }
                }
            }
        }
    }

    private void sealFoundation(World world, int originX, int originY, int originZ) {
        int top = Math.max(2, originY - 1);
        BlockData siftslate = registry.defaultState("siftslate").data();
        for (int localX = 0; localX < MainPortalPlan.SIZE_X; localX++) {
            for (int localZ = 0; localZ < MainPortalPlan.SIZE_Z; localZ++) {
                if (!MainPortalPlan.isBaseColumn(localX, localZ)) {
                    continue;
                }
                int x = originX + localX;
                int z = originZ + localZ;
                int terrainY = Math.max(1, terrainSurfaceY(world, x, z));
                String surface = id(world.getBlockAt(x, terrainY, z));
                if (terrainY < top) {
                    raiseColumn(world, x, z, terrainY, top, surface == null ? "siftslate" : surface, false);
                }
                for (int y = top; y > terrainY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir() || isPlant(block)) {
                        set(block, siftslate);
                    }
                }
            }
        }
    }

    private void lowerColumn(World world, int x, int z, int naturalY, int desiredY) {
        for (int y = naturalY; y > desiredY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!isTerrain(block)) {
                break;
            }
            set(block, Material.AIR.createBlockData());
        }
        Block exposed = world.getBlockAt(x, desiredY, z);
        if (isTerrain(exposed)) {
            set(exposed, registry.defaultState("siftslate").data());
        }
    }

    private void raiseColumn(World world, int x, int z, int naturalY, int desiredY, String originalTop, boolean keepSurface) {
        BlockData siftslate = registry.defaultState("siftslate").data();
        for (int y = naturalY + 1; y <= desiredY; y++) {
            set(world.getBlockAt(x, y, z), siftslate);
        }
        if (keepSurface && ("siftslate_growth".equals(originalTop) || "healthy_sculk".equals(originalTop))) {
            set(world.getBlockAt(x, desiredY, z), registry.defaultState(originalTop).data());
        }
    }

    /** Growth under a full block becomes plain siftslate. */
    private void normalizeCoveredGrowth(World world, int originX, int originY, int originZ) {
        BlockData siftslate = registry.defaultState("siftslate").data();
        for (int x = 0; x < MainPortalPlan.SIZE_X; x++) {
            for (int z = 0; z < MainPortalPlan.SIZE_Z; z++) {
                for (int y = 0; y < MainPortalPlan.SIZE_Y - 1; y++) {
                    Block block = world.getBlockAt(originX + x, originY + y, originZ + z);
                    if ("siftslate_growth".equals(id(block)) && block.getRelative(0, 1, 0).getType().isOccluding()) {
                        set(block, siftslate);
                    }
                }
            }
        }
    }

    /** Plants on the structure floors, except around the arrival point. */
    private void decorate(World world, int originX, int originY, int originZ, int anchorX, int anchorZ, Random random) {
        for (int x = originX; x < originX + MainPortalPlan.SIZE_X; x++) {
            for (int z = originZ; z < originZ + MainPortalPlan.SIZE_Z; z++) {
                for (int y = originY; y < originY + MainPortalPlan.SIZE_Y; y++) {
                    if (MainPortalPlan.isApproach(x, z, anchorX, anchorZ)) {
                        continue;
                    }
                    Block floor = world.getBlockAt(x, y, z);
                    Block plantPos = floor.getRelative(0, 1, 0);
                    if (!plantPos.getType().isAir()) {
                        continue;
                    }
                    String floorId = id(floor);
                    float roll = random.nextFloat();
                    float chance;
                    String plant;
                    if ("siftslate_growth".equals(floorId) || "dry_healthy_sculk_growth".equals(floorId)) {
                        chance = 0.72f;
                        plant = roll < 0.4f ? "overgrown_fronds" : roll < 0.72f ? "overgrown_stalks" : "overgrown_chard";
                    } else if ("siftslate".equals(floorId)) {
                        chance = 0.36f;
                        plant = "siftslate_stalks";
                    } else if ("healthy_sculk".equals(floorId)) {
                        chance = 0.42f;
                        plant = "healthy_sculk_sprouts";
                    } else if ("dry_healthy_sculk".equals(floorId)) {
                        chance = 0.3f;
                        plant = "dry_healthy_sculk_sprouts";
                    } else {
                        continue;
                    }
                    if (random.nextFloat() < chance * 0.92f) {
                        set(plantPos, registry.defaultState(plant).data());
                    }
                }
            }
        }
    }

    /** y of the top Sift terrain block within 48 blocks below the surface, or -1. */
    private int terrainSurfaceY(World world, int x, int z) {
        int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        int minimum = Math.max(world.getMinHeight() + 1, top - 48);
        for (int y = top; y >= minimum; y--) {
            if (isTerrain(world.getBlockAt(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    private String id(Block block) {
        CustomBlock custom = registry.of(block.getBlockData());
        return custom == null ? null : custom.id();
    }

    private boolean isTerrain(Block block) {
        String id = id(block);
        return id != null && TERRAIN.contains(id);
    }

    private boolean isPlant(Block block) {
        if (block.getType() == Material.VINE) {
            return true;
        }
        String id = id(block);
        return id != null && PLANTS.contains(id);
    }

    /** Without neighbour updates, like the original's build flags (no drops, no shape updates). */
    private static void set(Block block, BlockData data) {
        if (!block.getBlockData().equals(data)) {
            block.setBlockData(data, false);
        }
    }
}
