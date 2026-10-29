package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.AbandonedPortalPlan;
import de.lamamc.sift.core.worldgen.LandmarkPlacement;
import de.lamamc.sift.core.worldgen.TemplateTransform;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

/**
 * Abandoned portals (AbandonedMainPortalFeature): one per 32×32 chunks on terrain with little relief,
 * template by biome (overgrown or wastes, 5 each), a foundation that slopes into the terrain, plants on
 * the template surfaces and a chest with the abandoned portal loot.
 */
final class AbandonedPortals {

    private static final Set<String> CAN_REPLACE_EXTRA = Set.of("siftslate_hanging_roots", "overgrown_hanging_roots");

    private final SiftTemplates templates;
    private final int cellChunks;
    private final LootTable chestLoot;
    private final Logger logger;
    final java.util.concurrent.atomic.AtomicLong lootChests = new java.util.concurrent.atomic.AtomicLong();

    AbandonedPortals(SiftTemplates templates, int cellChunks, String chestLoot, Logger logger) {
        this.templates = templates;
        this.cellChunks = cellChunks;
        NamespacedKey key = NamespacedKey.fromString(chestLoot);
        this.chestLoot = key == null ? null : Bukkit.getLootTable(key);
        this.logger = logger;
    }

    static List<String> templateNames() {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= AbandonedPortalPlan.TEMPLATE_VARIANTS; i++) {
            names.add("abandoned_portal_overgrown_" + i);
            names.add("abandoned_portal_wastes_" + i);
        }
        return names;
    }

    boolean isSelected(long seed, int chunkX, int chunkZ) {
        return LandmarkPlacement.isSelectedChunk(seed, chunkX, chunkZ, cellChunks, LandmarkPlacement.ABANDONED_PORTAL_SALT);
    }

    /** Places the portal of this chunk; returns null on success, otherwise why not (for /sift debug worldgen). */
    String place(GenRegion region, Random random, int chunkX, int chunkZ, boolean overgrown, long seed) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int surfaceY = region.height(HeightMap.WORLD_SURFACE_WG, centerX, centerZ) - 1;
        if (surfaceY <= region.minY() + 4 || !region.isTerrain(centerX, surfaceY, centerZ)) {
            return "kein Sift-Boden in der Chunkmitte";
        }
        String name = (overgrown ? "abandoned_portal_overgrown_" : "abandoned_portal_wastes_")
                + (1 + random.nextInt(AbandonedPortalPlan.TEMPLATE_VARIANTS));
        SiftTemplates.Template template = templates.get(name);
        if (template == null) {
            return "Vorlage fehlt";
        }
        TemplateTransform transform = TemplateTransform.random(random, AbandonedPortalPlan.PIVOT_X, AbandonedPortalPlan.PIVOT_Z);
        if (!AbandonedPortalPlan.terrainFits((x, z) -> region.height(HeightMap.WORLD_SURFACE_WG, x, z) - 1, centerX, centerZ,
                transform.rotation())) {
            return "Gelände zu steil";
        }
        int originX = centerX - AbandonedPortalPlan.PIVOT_X;
        int originZ = centerZ - AbandonedPortalPlan.PIVOT_Z;
        List<int[]> natural = new ArrayList<>();
        List<int[]> structure = new ArrayList<>();
        List<Object[]> placements = new ArrayList<>();
        for (SiftTemplates.Block block : template.blocks()) {
            int[] p = transform.apply(block.x(), block.y(), block.z());
            int[] pos = {originX + p[0], surfaceY + p[1], originZ + p[2]};
            if (block.data().getMaterial().isAir() || block.data().getMaterial() == Material.STRUCTURE_VOID) {
                continue; // BlockIgnoreProcessor.STRUCTURE_AND_AIR
            }
            placements.add(new Object[] {pos, block});
            if (block.custom() != null && GenRegion.TERRAIN.contains(block.custom().id())) {
                natural.add(pos);
                structure.add(pos);
            } else if (block.data().getMaterial() == Material.CHEST) {
                structure.add(pos);
            }
        }
        if (natural.isEmpty()) {
            return "Vorlage ohne Boden";
        }
        for (int[] pos : structure) {
            if (!region.writable(pos[0], pos[1], pos[2]) || !canReplace(region, pos[0], pos[1], pos[2])) {
                return "Platz belegt";
            }
        }
        for (Object[] placement : placements) {
            int[] pos = (int[]) placement[0];
            if (!region.writable(pos[0], pos[1], pos[2])) {
                return "außerhalb des Bereichs";
            }
        }
        natural.sort((a, b) -> Integer.compare(a[1], b[1]));
        int baseY = natural.getFirst()[1];
        List<int[]> blended = blendFoundation(region, random, natural, baseY);

        for (Object[] placement : placements) {
            int[] pos = (int[]) placement[0];
            SiftTemplates.Block block = (SiftTemplates.Block) placement[1];
            if (block.custom() != null) {
                region.setSift(pos[0], pos[1], pos[2], block.custom().id(), transform.properties(block.custom().properties()));
            } else {
                BlockData data = block.data().clone();
                if (data instanceof Directional directional && directional.getFacing().getModY() == 0) {
                    directional.setFacing(org.bukkit.block.BlockFace.valueOf(
                            transform.direction(directional.getFacing().name().toLowerCase()).toUpperCase()));
                }
                region.set(pos[0], pos[1], pos[2], data);
                if (data.getMaterial() == Material.CHEST) {
                    fillChest(region, pos, block.loot(), random);
                }
            }
        }
        for (int[] pos : natural) {
            SurfacePlants.decorateStructureSurface(region, random, pos[0], pos[1], pos[2], 0.96f);
        }
        for (int[] pos : blended) {
            SurfacePlants.decorate(region, random, pos[0], pos[1], pos[2], 0.72f);
        }
        return null;
    }

    private void fillChest(GenRegion region, int[] pos, LootTable templateLoot, Random random) {
        LootTable loot = templateLoot != null ? templateLoot : chestLoot;
        if (loot == null) {
            return;
        }
        BlockState state = region.region().getBlockState(pos[0], pos[1], pos[2]);
        if (state instanceof Lootable lootable) {
            lootable.setLootTable(loot, random.nextLong());
            state.update(true, false);
            lootChests.incrementAndGet();
        } else {
            logger.fine("No chest block entity at " + pos[0] + " " + pos[1] + " " + pos[2]);
        }
    }

    /** Raises the ground under and around the template base (AbandonedMainPortalFeature#blendNaturalFoundation). */
    private List<int[]> blendFoundation(GenRegion region, Random random, List<int[]> natural, int baseY) {
        List<int[]> baseColumns = new ArrayList<>();
        Set<Long> baseKeys = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] pos : natural) {
            if (pos[1] == baseY && baseKeys.add(key(pos[0], pos[2]))) {
                baseColumns.add(pos);
                minX = Math.min(minX, pos[0]);
                maxX = Math.max(maxX, pos[0]);
                minZ = Math.min(minZ, pos[2]);
                maxZ = Math.max(maxZ, pos[2]);
            }
        }
        List<int[]> blended = new ArrayList<>();
        if (baseColumns.isEmpty()) {
            return blended;
        }
        long noiseSalt = random.nextLong();
        int radius = AbandonedPortalPlan.FOUNDATION_BLEND_RADIUS;
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                if (!region.contains(x, z)) {
                    continue;
                }
                boolean core = baseKeys.contains(key(x, z));
                double distance = core ? 0 : distanceToBase(x, z, baseColumns);
                int targetTop = AbandonedPortalPlan.foundationTargetY(x, z, distance, core, baseY, noiseSalt);
                if (targetTop == Integer.MIN_VALUE) {
                    continue;
                }
                int surfaceY = findTerrainSurface(region, x, z);
                if (surfaceY < 0) {
                    continue;
                }
                int naturalTop = surfaceY - 1;
                if (naturalTop >= targetTop || !region.isTerrain(x, naturalTop, z)) {
                    continue;
                }
                String originalTop = region.id(x, naturalTop, z);
                boolean complete = true;
                for (int y = naturalTop + 1; y <= targetTop; y++) {
                    if (!region.writable(x, y, z) || !canReplace(region, x, y, z)) {
                        complete = false;
                        break;
                    }
                    region.setSift(x, y, z, "siftslate");
                }
                if (complete) {
                    region.setSift(x, targetTop, z, blendedSurface(originalTop, core, distance));
                    if (!core && distance > 2.5) {
                        blended.add(new int[] {x, targetTop, z});
                    }
                }
            }
        }
        return blended;
    }

    private static String blendedSurface(String originalTop, boolean core, double distance) {
        if (core || distance < 2.5) {
            return "siftslate";
        }
        return switch (originalTop) {
            case "siftslate_growth", "dry_healthy_sculk_growth", "healthy_sculk", "dry_healthy_sculk" -> originalTop;
            default -> "siftslate";
        };
    }

    /** First free y above the Sift terrain within 8 blocks below the WG surface, or -1. */
    private static int findTerrainSurface(GenRegion region, int x, int z) {
        int y = region.height(HeightMap.WORLD_SURFACE_WG, x, z);
        for (int step = 0; step < 8 && y > region.minY(); step++, y--) {
            if (region.isTerrain(x, y - 1, z)) {
                return y;
            }
        }
        return -1;
    }

    private static double distanceToBase(int x, int z, List<int[]> baseColumns) {
        int best = Integer.MAX_VALUE;
        for (int[] base : baseColumns) {
            int dx = x - base[0];
            int dz = z - base[2];
            best = Math.min(best, dx * dx + dz * dz);
        }
        return Math.sqrt(best);
    }

    private static boolean canReplace(GenRegion region, int x, int y, int z) {
        if (region.isAir(x, y, z)) {
            return true;
        }
        String id = region.id(x, y, z);
        return id != null && (GenRegion.TERRAIN.contains(id) || GenRegion.SMALL_PLANTS.contains(id) || CAN_REPLACE_EXTRA.contains(id));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
