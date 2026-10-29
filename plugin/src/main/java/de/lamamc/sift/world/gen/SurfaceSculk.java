package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.SculkRegions;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.SculkShrieker;

/**
 * Large sculk patches on the surface (SiftSurfaceSculkRegionFeature): the top 6–9 blocks of the terrain
 * become vanilla sculk, with sensors, shriekers that can summon a warden, catalysts and veins at the edge.
 * The Dark Sniffer spawns of the original come with the mobs (phase 2).
 */
final class SurfaceSculk {

    private static final Set<String> REPLACEABLE_PLANTS = Set.of("overgrown_chard", "overgrown_stalks", "overgrown_fronds",
            "overgrown_lotus", "sunburst_plant", "whisperbloom", "siftslate_stalks", "healthy_sculk_sprouts",
            "dry_healthy_sculk_sprouts");
    private static final Set<Material> REPLACEABLE_VANILLA = Set.of(Material.WILDFLOWERS, Material.TORCHFLOWER,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP, Material.PITCHER_PLANT);

    private final BlockData sculk = Bukkit.createBlockData(Material.SCULK);
    private final BlockData air = Bukkit.createBlockData(Material.AIR);
    private final BlockData catalyst = Bukkit.createBlockData(Material.SCULK_CATALYST);
    private final BlockData sensor = Bukkit.createBlockData(Material.SCULK_SENSOR);
    private final BlockData shrieker;
    private final BlockData vein;

    SurfaceSculk() {
        SculkShrieker shrieker = (SculkShrieker) Bukkit.createBlockData(Material.SCULK_SHRIEKER);
        shrieker.setCanSummon(true);
        this.shrieker = shrieker;
        MultipleFacing vein = (MultipleFacing) Bukkit.createBlockData(Material.SCULK_VEIN);
        vein.setFace(BlockFace.DOWN, true);
        this.vein = vein;
    }

    boolean place(GenRegion region, long seed, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        boolean changed = false;
        for (SculkRegions.Region sculkRegion : SculkRegions.regionsTouching(seed, minX, minZ)) {
            for (int x = minX; x <= minX + 15; x++) {
                for (int z = minZ; z <= minZ + 15; z++) {
                    boolean core = sculkRegion.contains(x, z);
                    if (!core && !sculkRegion.isVeinFringe(x, z)) {
                        continue;
                    }
                    int surfaceY = region.height(HeightMap.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    if (surfaceY < region.minY() || surfaceY >= region.maxY() - 2 || !isNaturalTerrain(region, x, surfaceY, z)) {
                        continue;
                    }
                    int aboveY = surfaceY + 1;
                    if (isReplaceablePlant(region, x, aboveY, z)) {
                        if (isPitcher(region.type(x, aboveY, z)) && isPitcher(region.type(x, aboveY + 1, z))) {
                            region.set(x, aboveY + 1, z, air);
                        }
                        region.set(x, aboveY, z, air);
                    }
                    boolean aboveFree = region.isAir(x, aboveY, z);
                    if (core) {
                        changed |= replaceSurface(region, sculkRegion, x, surfaceY, z);
                        if (aboveFree) {
                            changed |= decorate(region, sculkRegion, x, aboveY, z);
                        }
                    } else if (aboveFree && region.writable(x, aboveY, z)) {
                        region.set(x, aboveY, z, vein);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private boolean replaceSurface(GenRegion region, SculkRegions.Region sculkRegion, int x, int surfaceY, int z) {
        int depth = sculkRegion.sculkDepth(x, z);
        boolean changed = false;
        for (int offset = 0; offset < depth; offset++) {
            int y = surfaceY - offset;
            if (!region.writable(x, y, z) || !isNaturalTerrain(region, x, y, z)) {
                break;
            }
            region.set(x, y, z, sculk);
            changed = true;
        }
        return changed;
    }

    private boolean decorate(GenRegion region, SculkRegions.Region sculkRegion, int x, int y, int z) {
        BlockData decoration = switch (sculkRegion.decoration(x, z)) {
            case CATALYST -> catalyst;
            case SHRIEKER -> shrieker;
            case SENSOR -> sensor;
            case VEIN -> vein;
            case NONE -> null;
        };
        if (decoration == null || !region.writable(x, y, z)) {
            return false;
        }
        region.set(x, y, z, decoration);
        return true;
    }

    private static boolean isNaturalTerrain(GenRegion region, int x, int y, int z) {
        return region.isTerrain(x, y, z) || region.type(x, y, z) == Material.SCULK;
    }

    private static boolean isReplaceablePlant(GenRegion region, int x, int y, int z) {
        String id = region.id(x, y, z);
        return id != null ? REPLACEABLE_PLANTS.contains(id) : REPLACEABLE_VANILLA.contains(region.type(x, y, z));
    }

    private static boolean isPitcher(Material material) {
        return material == Material.PITCHER_CROP || material == Material.PITCHER_PLANT;
    }
}
