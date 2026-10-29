package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.PlantSupport;
import java.util.Map;
import java.util.Set;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockSupport;
import org.bukkit.generator.LimitedRegion;

/**
 * The blocks a populator may touch, with the questions the original features ask: Sift id of a block,
 * terrain and plant checks, heights like vanilla's getHeight (first free y). Writes stay inside the
 * chunk and its neighbours, minus one block, like SiftWorldgenBounds#aroundWithNeighbourMargin.
 */
public final class GenRegion {

    /** Natural ground of the Sift (SiftMonolithFeature#isSiftTerrain). */
    public static final Set<String> TERRAIN = Set.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk",
            "dry_healthy_sculk_growth");
    /** Small plants that trees and structures may overwrite. */
    public static final Set<String> SMALL_PLANTS = Set.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds",
            "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts");

    private final LimitedRegion region;
    private final CustomBlockRegistry registry;
    private final PlantSupport support;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int minY;
    private final int maxY;

    public GenRegion(LimitedRegion region, CustomBlockRegistry registry, PlantSupport support, int chunkX, int chunkZ,
                     int minY, int maxY) {
        this.region = region;
        this.registry = registry;
        this.support = support;
        this.minX = ((chunkX - 1) << 4) + 1;
        this.maxX = ((chunkX + 2) << 4) - 2;
        this.minZ = ((chunkZ - 1) << 4) + 1;
        this.maxZ = ((chunkZ + 2) << 4) - 2;
        this.minY = minY;
        this.maxY = maxY;
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean writable(int x, int y, int z) {
        return contains(x, z) && y >= minY && y < maxY && region.isInRegion(x, y, z);
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public BlockData data(int x, int y, int z) {
        return region.getBlockData(x, y, z);
    }

    public Material type(int x, int y, int z) {
        return region.getType(x, y, z);
    }

    /** Sift id of the block, or null for vanilla blocks. */
    public String id(int x, int y, int z) {
        CustomBlock custom = registry.of(region.getBlockData(x, y, z));
        return custom == null ? null : custom.id();
    }

    public boolean is(int x, int y, int z, String siftId) {
        return siftId.equals(id(x, y, z));
    }

    public boolean isAir(int x, int y, int z) {
        return region.getType(x, y, z).isAir();
    }

    public boolean isTerrain(int x, int y, int z) {
        String id = id(x, y, z);
        return id != null && TERRAIN.contains(id);
    }

    /** Full, sturdy face towards {@code face} (vines hold on to it). */
    public boolean sturdy(int x, int y, int z, BlockFace face) {
        return region.getBlockData(x, y, z).isFaceSturdy(face, BlockSupport.FULL);
    }

    public boolean supportsVegetation(int x, int y, int z) {
        return id(x, y, z) == null && Tag.SUPPORTS_VEGETATION.isTagged(region.getType(x, y, z));
    }

    /** Like vanilla getHeight: the first free y above the heightmap (what LimitedRegion returns). */
    public int height(HeightMap map, int x, int z) {
        return region.getHighestBlockYAt(x, z, map);
    }

    public void set(int x, int y, int z, BlockData data) {
        region.setBlockData(x, y, z, data);
    }

    public void setSift(int x, int y, int z, String siftId) {
        region.setBlockData(x, y, z, registry.defaultState(siftId).data());
    }

    public void setSift(int x, int y, int z, String siftId, Map<String, String> properties) {
        CustomBlock state = registry.state(siftId, properties);
        region.setBlockData(x, y, z, (state != null ? state : registry.defaultState(siftId)).data());
    }

    /** Whether a Sift plant could stand here (its support rule, the block itself free). */
    public boolean plantFits(String plantId, int x, int y, int z) {
        int baseY = support.above(plantId) ? y + 1 : y - 1;
        return writable(x, y, z) && isAir(x, y, z) && support.supports(plantId, region.getBlockData(x, baseY, z));
    }

    public LimitedRegion region() {
        return region;
    }

    public CustomBlockRegistry registry() {
        return registry;
    }
}
