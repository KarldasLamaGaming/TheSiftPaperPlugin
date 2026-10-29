package de.lamamc.sift.world.gen;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.FlowerBed;

/**
 * Plant features of the original: flower patches (SiftFlowerPatchFeature), siftslate plant patches
 * (SiftslatePlantPatchFeature) and the plant cover of structure surfaces (SiftSurfaceDecorator).
 */
final class SurfacePlants {

    private static final String[] FLOWERS = {"overgrown_lotus", "sunburst_plant", "whisperbloom"};
    private static final String[] PATCH_PLANTS = {"overgrown_fronds", "overgrown_stalks", "overgrown_chard",
            "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts"};
    private static final BlockFace[] HORIZONTAL = {BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};

    private SurfacePlants() {
    }

    /** 3–4 flowers of one kind (or wildflowers) around the chunk center. */
    static boolean flowerPatch(GenRegion region, Random random, int chunkX, int chunkZ, boolean overgrown) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int target = 3 + random.nextInt(2);
        int selection = random.nextInt(overgrown ? 6 : 24);
        boolean wildflowers = selection < 3;
        String flower = FLOWERS[selection % 3];
        int placed = 0;
        for (int attempt = 0; attempt < 24 && placed < target; attempt++) {
            int x = centerX + random.nextInt(7) - 3;
            int z = centerZ + random.nextInt(7) - 3;
            int y = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
            String floor = region.id(x, y - 1, z);
            boolean soil = "siftslate".equals(floor) || "siftslate_growth".equals(floor) || "dry_healthy_sculk".equals(floor)
                    || "healthy_sculk".equals(floor);
            if (!soil || !region.writable(x, y, z) || !region.isAir(x, y, z)) {
                continue;
            }
            if (wildflowers) {
                // wildflowers need vanilla vegetation soil, which the Sift soil is not (as in the original)
                FlowerBed bed = (FlowerBed) Bukkit.createBlockData(Material.WILDFLOWERS);
                bed.setFacing(HORIZONTAL[random.nextInt(4)]);
                bed.setFlowerAmount(1 + random.nextInt(4));
                if (region.supportsVegetation(x, y - 1, z)) {
                    region.set(x, y, z, bed);
                    placed++;
                }
            } else if (region.plantFits(flower, x, y, z)) {
                region.setSift(x, y, z, flower);
                placed++;
            }
        }
        return placed > 0;
    }

    /** 4–7 plants of one kind on bare siftslate around the chunk center. */
    static boolean plantPatch(GenRegion region, Random random, int chunkX, int chunkZ) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int target = 4 + random.nextInt(4);
        String plant = PATCH_PLANTS[random.nextInt(6)];
        int placed = 0;
        for (int attempt = 0; attempt < 32 && placed < target; attempt++) {
            int x = centerX + random.nextInt(9) - 4;
            int z = centerZ + random.nextInt(9) - 4;
            int y = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
            if (region.is(x, y - 1, z, "siftslate") && region.plantFits(plant, x, y, z)) {
                region.setSift(x, y, z, plant);
                placed++;
            }
        }
        return placed > 0;
    }

    /** Plants on a structure floor (SiftSurfaceDecorator#decorateStructureSurface). */
    static void decorateStructureSurface(GenRegion region, Random random, int x, int floorY, int z, float density) {
        if (!region.writable(x, floorY + 1, z) || !region.isAir(x, floorY + 1, z)) {
            return;
        }
        String floor = region.id(x, floorY, z);
        float roll = random.nextFloat();
        float chance;
        String plant;
        if ("siftslate_growth".equals(floor) || "dry_healthy_sculk_growth".equals(floor)) {
            chance = 0.72f;
            plant = roll < 0.4f ? "overgrown_fronds" : roll < 0.72f ? "overgrown_stalks" : "overgrown_chard";
        } else if ("siftslate".equals(floor)) {
            chance = 0.36f;
            plant = "siftslate_stalks";
        } else if ("healthy_sculk".equals(floor)) {
            chance = 0.42f;
            plant = "healthy_sculk_sprouts";
        } else if ("dry_healthy_sculk".equals(floor)) {
            chance = 0.3f;
            plant = "dry_healthy_sculk_sprouts";
        } else {
            return;
        }
        if (random.nextFloat() < chance * density && region.plantFits(plant, x, floorY + 1, z)) {
            region.setSift(x, floorY + 1, z, plant);
        }
    }

    /** Plants on natural ground (SiftSurfaceDecorator#decorate). */
    static void decorate(GenRegion region, Random random, int x, int floorY, int z, float density) {
        if (!region.writable(x, floorY + 1, z) || !region.isAir(x, floorY + 1, z)) {
            return;
        }
        String floor = region.id(x, floorY, z);
        float roll = random.nextFloat();
        float chance;
        String plant;
        if ("siftslate_growth".equals(floor)) {
            chance = 0.255f;
            plant = roll < 0.43f ? "overgrown_fronds" : roll < 0.68f ? "overgrown_stalks" : roll < 0.86f ? "overgrown_chard" : "siftslate_stalks";
        } else if ("dry_healthy_sculk_growth".equals(floor)) {
            chance = 0.235f;
            plant = roll < 0.34f ? "dry_healthy_sculk_sprouts" : roll < 0.59f ? "overgrown_fronds"
                    : roll < 0.8f ? "overgrown_stalks" : roll < 0.93f ? "overgrown_chard" : "siftslate_stalks";
        } else if ("healthy_sculk".equals(floor)) {
            chance = 0.225f;
            plant = roll < 0.76f ? "healthy_sculk_sprouts" : roll < 0.92f ? "siftslate_stalks" : "overgrown_fronds";
        } else if ("dry_healthy_sculk".equals(floor)) {
            chance = 0.145f;
            plant = roll < 0.74f ? "dry_healthy_sculk_sprouts" : roll < 0.94f ? "siftslate_stalks" : "overgrown_stalks";
        } else if ("siftslate".equals(floor)) {
            chance = 0.045f;
            plant = roll < 0.72f ? "siftslate_stalks" : "overgrown_fronds";
        } else {
            return;
        }
        if (random.nextFloat() < chance * density && region.plantFits(plant, x, floorY + 1, z)) {
            region.setSift(x, floorY + 1, z, plant);
        }
    }
}
