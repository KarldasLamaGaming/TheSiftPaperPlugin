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

    /** Ground a landmark may stand on and replace (SiftLandmarkTerrain#isGround). */
    public static final Set<String> LANDMARK_GROUND = Set.of("siftslate", "cobbled_siftslate", "sift_rubble", "siftslate_growth",
            "healthy_sculk", "dry_healthy_sculk", "dry_healthy_sculk_growth", "siftslate_coal_ore", "siftslate_diamond_ore",
            "siftslate_emerald_ore", "siftslate_charoite_ore", "siftslate_siftite_ore", "ichor_snow_block");
    /** Further Sift blocks a landmark may overwrite (SiftLandmarkTerrain#canReplace). */
    private static final Set<String> LANDMARK_REPLACEABLE = Set.of("ichor_snow", "siftslate_hanging_roots", "overgrown_hanging_roots");
    private static final Set<Material> LANDMARK_GROUND_VANILLA = Set.of(Material.SCULK, Material.BONE_BLOCK);
    private static final Set<Material> LANDMARK_REPLACEABLE_VANILLA = Set.of(Material.SCULK_VEIN, Material.SCULK_SENSOR,
            Material.SCULK_SHRIEKER, Material.SCULK_CATALYST);
    /** Replaceable blocks that hold a fluid (vanilla canReplace also requires an empty fluid state). */
    private static final Set<Material> FLUIDS = Set.of(Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN, Material.SEAGRASS,
            Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT);

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

    /** "the_sift:x" or the vanilla block id, for messages. */
    public String describe(int x, int y, int z) {
        String id = id(x, y, z);
        return id != null ? "the_sift:" + id : region.getType(x, y, z).getKey().toString();
    }

    public boolean isAir(int x, int y, int z) {
        return region.getType(x, y, z).isAir();
    }

    public boolean isTerrain(int x, int y, int z) {
        String id = id(x, y, z);
        return id != null && TERRAIN.contains(id);
    }

    /** Distance of x/z to the edge of the writable area (SiftWorldgenBounds#distanceToEdge). */
    public int distanceToEdge(int x, int z) {
        return Math.max(0, Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z)));
    }

    /** SiftLandmarkTerrain#isGround. */
    public boolean isLandmarkGround(int x, int y, int z) {
        String id = id(x, y, z);
        return id != null ? LANDMARK_GROUND.contains(id) : LANDMARK_GROUND_VANILLA.contains(region.getType(x, y, z));
    }

    /** SiftLandmarkTerrain#canReplace: air, ground, small plants and replaceable blocks without fluid. */
    public boolean canLandmarkReplace(int x, int y, int z) {
        BlockData data = region.getBlockData(x, y, z);
        CustomBlock custom = registry.of(data);
        if (custom != null) {
            String id = custom.id();
            return LANDMARK_GROUND.contains(id) || SMALL_PLANTS.contains(id) || LANDMARK_REPLACEABLE.contains(id);
        }
        Material type = data.getMaterial();
        if (type.isAir() || LANDMARK_GROUND_VANILLA.contains(type) || LANDMARK_REPLACEABLE_VANILLA.contains(type)) {
            return true;
        }
        boolean fluid = FLUIDS.contains(type) || data instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged();
        return !fluid && data.isReplaceable();
    }

    /**
     * SiftLandmarkTerrain#surfaceBlockY: the topmost landmark ground within 32 blocks below the WG
     * surface, or Integer.MIN_VALUE when something else (a tree, ichor, a structure) is in the way.
     */
    public int landmarkSurfaceY(int x, int z) {
        int top = height(HeightMap.WORLD_SURFACE_WG, x, z) - 1;
        int minimum = Math.max(minY + 1, top - 32);
        for (int y = top; y >= minimum; y--) {
            if (isLandmarkGround(x, y, z)) {
                return y;
            }
            if (!canLandmarkReplace(x, y, z)) {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
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
