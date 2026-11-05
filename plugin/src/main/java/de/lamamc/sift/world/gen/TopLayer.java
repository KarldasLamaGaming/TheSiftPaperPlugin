package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.core.worldgen.GrowthCleanup;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.BlockSupport;

/**
 * Feature step top_layer_modification of the original 1.1.1 (alpha.30): covered growth cleanup
 * (CoveredGrowthCleanupFeature, config worldgen.covered-growth-cleanup) and the lava floor
 * (SiftLavaFloorFeature, config worldgen.lava-floor: every air block up to y 11 becomes lava). Both run in
 * every Sift chunk, before the surface sculk and the ichor snow.
 */
public final class TopLayer {

    private static final Set<Material> WATER = Set.of(Material.WATER, Material.BUBBLE_COLUMN, Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.KELP, Material.KELP_PLANT);

    public final AtomicLong growth = new AtomicLong();
    public final AtomicLong plants = new AtomicLong();
    public final AtomicLong lava = new AtomicLong();
    public final AtomicLong nanos = new AtomicLong();
    private final boolean cleanup;
    private final boolean lavaFloor;
    private final int lavaMaxY;

    public TopLayer(boolean cleanup, boolean lavaFloor, int lavaMaxY) {
        this.cleanup = cleanup;
        this.lavaFloor = lavaFloor;
        this.lavaMaxY = lavaMaxY;
    }

    public void place(GenRegion region, int chunkX, int chunkZ) {
        long start = System.nanoTime();
        if (cleanup) {
            cleanup(region, chunkX, chunkZ);
        }
        if (lavaFloor) {
            lavaFloor(region, chunkX, chunkZ);
        }
        nanos.addAndGet(System.nanoTime() - start);
    }

    private void cleanup(GenRegion region, int chunkX, int chunkZ) {
        int maxY = region.maxY() - 1;
        BlockData air = Material.AIR.createBlockData();
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                int surface = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
                int minY = Math.max(region.minY(), surface - 56);
                for (int y = minY; y <= Math.min(maxY, surface); y++) {
                    BlockData data = region.data(x, y, z);
                    CustomBlock custom = region.registry().of(data);
                    String id = custom != null ? custom.id() : data.getMaterial().getKey().toString();
                    if (GrowthCleanup.LAKE_PLANTS.contains(id) && floatsOverIchor(region, x, y, z)) {
                        region.set(x, y, z, air);
                        plants.incrementAndGet();
                        continue;
                    }
                    if (custom == null || !GrowthCleanup.GROWTH.equals(id) && !GrowthCleanup.DRY_GROWTH.equals(id) || y + 1 > maxY) {
                        continue;
                    }
                    BlockData cover = region.data(x, y + 1, z);
                    boolean coverSnow = cover.getMaterial() == Material.SNOW;
                    // isCollisionShapeFullBlock: a full cube on top (custom Sift blocks are full cubes)
                    boolean coverFull = cover.isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL) && cover.isFaceSturdy(BlockFace.UP, BlockSupport.FULL)
                            && cover.getMaterial().isSolid();
                    boolean snowy = "true".equals(custom.properties().get("snowy"));
                    GrowthCleanup.Change change = GrowthCleanup.growth(id, snowy, y > region.minY() ? region.id(x, y - 1, z) : null,
                            coverFull, coverSnow);
                    if (change != null) {
                        if (GrowthCleanup.GROWTH.equals(change.block())) {
                            region.setSift(x, y, z, GrowthCleanup.GROWTH, Map.of("snowy", Boolean.toString(change.snowy())));
                        } else {
                            region.setSift(x, y, z, change.block());
                        }
                        growth.incrementAndGet();
                    }
                }
            }
        }
    }

    private static boolean floatsOverIchor(GenRegion region, int x, int y, int z) {
        GrowthCleanup.Below[] below = new GrowthCleanup.Below[6];
        for (int i = 0; i < 6; i++) {
            int by = y - 1 - i;
            if (by < region.minY()) {
                below[i] = GrowthCleanup.Below.SOLID;
                continue;
            }
            BlockData data = region.data(x, by, z);
            Material type = data.getMaterial();
            below[i] = WATER.contains(type) || data instanceof Waterlogged w && w.isWaterlogged() ? GrowthCleanup.Below.ICHOR
                    : type == Material.LAVA ? GrowthCleanup.Below.OTHER_FLUID
                    : type.isAir() ? GrowthCleanup.Below.AIR : GrowthCleanup.Below.SOLID;
        }
        return GrowthCleanup.floatsOverIchor(below);
    }

    private void lavaFloor(GenRegion region, int chunkX, int chunkZ) {
        BlockData lavaBlock = Material.LAVA.createBlockData();
        int maxY = Math.min(lavaMaxY, region.maxY() - 1);
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                for (int y = region.minY(); y <= maxY; y++) {
                    if (region.type(x, y, z).isAir() && region.writable(x, y, z)) {
                        region.set(x, y, z, lavaBlock);
                        lava.incrementAndGet();
                    }
                }
            }
        }
    }
}
