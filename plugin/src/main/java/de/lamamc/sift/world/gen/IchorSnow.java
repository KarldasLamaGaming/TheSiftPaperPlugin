package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.IchorSnowRules;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Snow;

/**
 * Ichor snow on the ichor snowy peaks (alpha.26, config worldgen.ichor-snow.*), as SiftIchorSnowFeature of the
 * original 1.1.1: every column of the biome gets one or two layers of ichor snow (vanilla snow here) on natural
 * ground, replacing small peak plants, and on top of willow crowns; siftslate growth under it turns snowy.
 * Runs last in the chunk (feature step top_layer_modification).
 */
public final class IchorSnow {

    public static final String BIOME = "ichor_snowy_peaks";
    private static final Set<String> GROUND = Set.of("siftslate", "siftslate_growth", "healthy_sculk", "dry_healthy_sculk",
            "dry_healthy_sculk_growth");
    private static final Set<String> CANOPY = Set.of("overgrown_willow_foliage", "overgrown_willow_log", "overgrown_willow_wood");
    private static final Set<String> PEAK_PLANTS = Set.of("healthy_sculk_sprouts", "dry_healthy_sculk_sprouts", "siftslate_stalks",
            "overgrown_fronds", "overgrown_chard", "overgrown_stalks", "overgrown_lotus", "sunburst_plant", "whisperbloom");
    private static final Set<Material> PEAK_PLANTS_VANILLA = Set.of(Material.SCULK_VEIN, Material.WILDFLOWERS, Material.TORCHFLOWER,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP, Material.PITCHER_PLANT);

    public final AtomicLong chunks = new AtomicLong();
    public final AtomicLong layers = new AtomicLong();
    public final AtomicLong nanos = new AtomicLong();
    private final boolean enabled;
    private final boolean canopy;

    public IchorSnow(boolean enabled, boolean canopy) {
        this.enabled = enabled;
        this.canopy = canopy;
    }

    public void place(GenRegion region, long seed, int chunkX, int chunkZ) {
        if (!enabled) {
            return;
        }
        long start = System.nanoTime();
        boolean any = false;
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                int groundY = region.height(HeightMap.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (groundY < region.minY() || groundY >= region.maxY() - 2 || !BIOME.equals(biome(region, x, groundY, z))) {
                    continue;
                }
                String ground = region.id(x, groundY, z);
                if ((ground != null && GROUND.contains(ground) || region.type(x, groundY, z) == Material.SCULK)
                        && snow(region, seed, x, groundY, z, IchorSnowRules.GROUND_SALT, true)) {
                    any = true;
                }
                if (!canopy) {
                    continue;
                }
                int canopyY = region.height(HeightMap.MOTION_BLOCKING, x, z) - 1;
                if (canopyY > groundY && canopyY < region.maxY() - 1 && BIOME.equals(biome(region, x, canopyY, z))) {
                    String top = region.id(x, canopyY, z);
                    if (top != null && CANOPY.contains(top) && snow(region, seed, x, canopyY, z, IchorSnowRules.CANOPY_SALT, false)) {
                        any = true;
                    }
                }
            }
        }
        if (any) {
            chunks.incrementAndGet();
        }
        nanos.addAndGet(System.nanoTime() - start);
    }

    private boolean snow(GenRegion region, long seed, int x, int supportY, int z, long salt, boolean replacePlants) {
        int y = supportY + 1;
        if (!region.writable(x, y, z) || !region.sturdy(x, supportY, z, BlockFace.UP)) {
            return false;
        }
        Material above = region.type(x, y, z);
        String aboveId = region.id(x, y, z); // null for vanilla blocks
        boolean plant = replacePlants && (aboveId != null && PEAK_PLANTS.contains(aboveId) || PEAK_PLANTS_VANILLA.contains(above));
        if (!above.isAir() && !plant) {
            return false;
        }
        if ((above == Material.PITCHER_CROP || above == Material.PITCHER_PLANT) && region.writable(x, y + 1, z)
                && (region.type(x, y + 1, z) == Material.PITCHER_CROP || region.type(x, y + 1, z) == Material.PITCHER_PLANT)) {
            region.set(x, y + 1, z, Material.AIR.createBlockData()); // the upper half of a pitcher
        }
        Snow snow = (Snow) Material.SNOW.createBlockData();
        snow.setLayers(IchorSnowRules.layers(seed, salt, x, z));
        region.set(x, y, z, snow);
        layers.incrementAndGet();
        if ("siftslate_growth".equals(region.id(x, supportY, z))) {
            region.setSift(x, supportY, z, "siftslate_growth", Map.of("snowy", "true"));
        }
        return true;
    }

    private static String biome(GenRegion region, int x, int y, int z) {
        var key = region.region().getBiome(x, y, z).getKey();
        return key.getNamespace().equals("the_sift") ? key.getKey() : key.toString();
    }
}
