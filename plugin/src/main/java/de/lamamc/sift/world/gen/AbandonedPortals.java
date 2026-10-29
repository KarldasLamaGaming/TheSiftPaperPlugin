package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.AbandonedPortalPlan;
import de.lamamc.sift.core.worldgen.LandmarkPlacement;
import de.lamamc.sift.core.worldgen.TemplateTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

/**
 * Abandoned portals (AbandonedMainPortalFeature of the original, version 1.1.1): one candidate per 20×20
 * chunks, template by biome (overgrown or wastes, 5 each), centred on the chunk, the rotation with the
 * least relief of all four, a foundation that moves the terrain towards the template floor, plants on the
 * template surfaces and a chest with the abandoned portal loot.
 */
final class AbandonedPortals {

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

    /** A position with the reason a placement step failed. */
    private record Rejection(int x, int y, int z, String reason) {
    }

    private record Column(int x, int z, int naturalTop, int targetTop, BlockData surface) {
    }

    private record Plan(int originX, int originY, int originZ, TemplateTransform transform, List<int[]> natural,
                        List<Column> terrain, int relief) {
    }

    /** Places the portal of this chunk; the entry says where (chest) or why not (reason and position). */
    LandmarkLog.Entry place(GenRegion region, Random random, int chunkX, int chunkZ, boolean overgrown, long seed,
                            PortalReservation reservation) {
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int surfaceY = region.landmarkSurfaceY(centerX, centerZ);
        if (surfaceY <= region.minY() + 4) {
            Rejection blocker = blocker(region, centerX, centerZ, "kein Sift-Boden in der Chunkmitte");
            return rejected(chunkX, chunkZ, blocker.x(), blocker.y(), blocker.z(), blocker.reason());
        }
        String name = (overgrown ? "abandoned_portal_overgrown_" : "abandoned_portal_wastes_")
                + (1 + random.nextInt(AbandonedPortalPlan.TEMPLATE_VARIANTS));
        SiftTemplates.Template template = templates.get(name);
        if (template == null) {
            return rejected(chunkX, chunkZ, centerX, surfaceY, centerZ, "Vorlage " + name + " fehlt");
        }
        TemplateTransform.Rotation[] rotations = TemplateTransform.Rotation.values();
        int firstRotation = random.nextInt(rotations.length);
        TemplateTransform.Mirror mirror = random.nextBoolean() ? TemplateTransform.Mirror.NONE : TemplateTransform.Mirror.LEFT_RIGHT;
        Plan plan = null;
        Rejection firstRejection = null;
        Set<String> reasons = new LinkedHashSet<>();
        for (int attempt = 0; attempt < rotations.length; attempt++) {
            TemplateTransform transform = new TemplateTransform(mirror, rotations[(firstRotation + attempt) % rotations.length], 0, 0);
            Object result = plan(region, template, transform, centerX, centerZ, seed);
            if (result instanceof Plan candidate) {
                if (plan == null || candidate.relief() < plan.relief()) {
                    plan = candidate;
                }
            } else {
                Rejection rejection = (Rejection) result;
                if (firstRejection == null) {
                    firstRejection = rejection;
                }
                reasons.add(rejection.reason());
            }
        }
        if (plan == null) {
            return rejected(chunkX, chunkZ, firstRejection.x(), firstRejection.y(), firstRejection.z(),
                    (reasons.size() == 1 ? "" : "keine der 4 Drehungen passt: ") + String.join("; ", reasons));
        }
        if (!reservation.tryReserveSolid(centerX - AbandonedPortalPlan.RESERVE_BELOW, centerZ - AbandonedPortalPlan.RESERVE_BELOW,
                centerX + AbandonedPortalPlan.RESERVE_ABOVE, centerZ + AbandonedPortalPlan.RESERVE_ABOVE)) {
            return rejected(chunkX, chunkZ, centerX, surfaceY, centerZ, "Platz von einem anderen Bauwerk reserviert");
        }
        List<int[]> surfaces = applyFoundation(region, plan.terrain());
        int[] chest = placeTemplate(region, random, template, plan);
        for (int[] pos : plan.natural()) {
            SurfacePlants.decorateStructureSurface(region, random, pos[0], pos[1], pos[2], 0.96f);
        }
        for (int[] pos : surfaces) {
            SurfacePlants.decorate(region, random, pos[0], pos[1], pos[2], 2.85f);
        }
        int[] where = chest != null ? chest : plan.natural().getFirst();
        return new LandmarkLog.Entry(chunkX, chunkZ, LandmarkLog.Status.BUILT, where[0], where[1], where[2], name);
    }

    static LandmarkLog.Entry rejected(int chunkX, int chunkZ, int x, int y, int z, String reason) {
        return new LandmarkLog.Entry(chunkX, chunkZ, LandmarkLog.Status.REJECTED, x, y, z, reason);
    }

    /** AbandonedMainPortalFeature#planPlacement: a {@link Plan} or the {@link Rejection} that stopped it. */
    private Object plan(GenRegion region, SiftTemplates.Template template, TemplateTransform transform, int centerX, int centerZ,
                        long seed) {
        List<int[]> relative = naturalBlocks(template, transform, 0, 0, 0);
        if (relative.isEmpty()) {
            return new Rejection(centerX, 0, centerZ, "Vorlage " + template.name() + " ohne Boden");
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] pos : relative) {
            minX = Math.min(minX, pos[0]);
            maxX = Math.max(maxX, pos[0]);
            minZ = Math.min(minZ, pos[2]);
            maxZ = Math.max(maxZ, pos[2]);
        }
        int baseY = relative.getFirst()[1];
        int originX = AbandonedPortalPlan.centredOrigin(centerX, minX, maxX);
        int originZ = AbandonedPortalPlan.centredOrigin(centerZ, minZ, maxZ);
        List<Integer> heights = new ArrayList<>();
        for (int x = originX + minX; x <= originX + maxX; x++) {
            for (int z = originZ + minZ; z <= originZ + maxZ; z++) {
                if (!region.contains(x, z)) {
                    return new Rejection(x, 0, z, "Vorlage ragt aus dem Generierungsbereich");
                }
                int height = region.landmarkSurfaceY(x, z);
                if (height <= region.minY() + 4) {
                    return blocker(region, x, z, "kein Sift-Boden unter der Vorlage");
                }
                heights.add(height);
            }
        }
        heights.sort(Integer::compareTo);
        int relief = AbandonedPortalPlan.relief(heights);
        if (relief > AbandonedPortalPlan.MAX_RELIEF) {
            return new Rejection(centerX, heights.getLast(), centerZ,
                    "Gelände zu steil (Höhenunterschied " + relief + " > " + AbandonedPortalPlan.MAX_RELIEF + ")");
        }
        int groundY = AbandonedPortalPlan.groundY(heights);
        int originY = groundY - baseY;
        List<int[]> natural = naturalBlocks(template, transform, originX, originY, originZ);
        List<int[]> structure = new ArrayList<>(natural);
        for (SiftTemplates.Block block : template.blocks()) {
            if (block.data().getMaterial() == Material.CHEST) {
                structure.add(world(transform, block, originX, originY, originZ));
            }
        }
        for (int[] pos : structure) {
            if (!region.writable(pos[0], pos[1], pos[2])) {
                return new Rejection(pos[0], pos[1], pos[2], "Vorlage ragt aus dem Generierungsbereich");
            }
            if (!region.canLandmarkReplace(pos[0], pos[1], pos[2])) {
                return new Rejection(pos[0], pos[1], pos[2], "Platz belegt durch " + region.describe(pos[0], pos[1], pos[2]));
            }
        }
        for (SiftTemplates.Block block : template.blocks()) {
            int[] pos = world(transform, block, originX, originY, originZ);
            if (placed(block) && !region.writable(pos[0], pos[1], pos[2])) {
                return new Rejection(pos[0], pos[1], pos[2], "Vorlage ragt aus dem Generierungsbereich");
            }
        }
        long noiseSalt = seed ^ AbandonedPortalPlan.packPos(originX, originY, originZ);
        Object terrain = planFoundation(region, originX + minX, originX + maxX, originZ + minZ, originZ + maxZ, groundY - 1, noiseSalt);
        if (terrain instanceof Rejection rejection) {
            return rejection;
        }
        @SuppressWarnings("unchecked")
        List<Column> columns = (List<Column>) terrain;
        return new Plan(originX, originY, originZ, transform, natural, columns, relief);
    }

    /** World positions of the natural blocks (Sift ground) of the template, lowest first. */
    private static List<int[]> naturalBlocks(SiftTemplates.Template template, TemplateTransform transform, int originX, int originY,
                                             int originZ) {
        List<int[]> blocks = new ArrayList<>();
        for (SiftTemplates.Block block : template.blocks()) {
            if (block.custom() != null && GenRegion.TERRAIN.contains(block.custom().id())) {
                blocks.add(world(transform, block, originX, originY, originZ));
            }
        }
        blocks.sort(Comparator.comparingInt(pos -> pos[1]));
        return blocks;
    }

    private static int[] world(TemplateTransform transform, SiftTemplates.Block block, int originX, int originY, int originZ) {
        int[] p = transform.apply(block.x(), block.y(), block.z());
        return new int[] {originX + p[0], originY + p[1], originZ + p[2]};
    }

    /** BlockIgnoreProcessor.STRUCTURE_AND_AIR: air and structure voids of the template are not placed. */
    private static boolean placed(SiftTemplates.Block block) {
        Material type = block.data().getMaterial();
        return !type.isAir() && type != Material.STRUCTURE_VOID;
    }

    /** AbandonedMainPortalFeature#planFoundation: the columns to reshape, or the {@link Rejection}. */
    private static Object planFoundation(GenRegion region, int minX, int maxX, int minZ, int maxZ, int floorY, long noiseSalt) {
        List<Column> terrain = new ArrayList<>();
        int radius = AbandonedPortalPlan.FOUNDATION_BLEND_RADIUS;
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                if (!region.contains(x, z)) {
                    continue;
                }
                int dx = Math.max(0, Math.max(minX - x, x - maxX));
                int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edge = AbandonedPortalPlan.foundationEdge(x, z, noiseSalt);
                if (distance > edge) {
                    continue;
                }
                boolean core = dx == 0 && dz == 0;
                int naturalTop = region.landmarkSurfaceY(x, z);
                if (naturalTop == Integer.MIN_VALUE) {
                    if (core) {
                        return blocker(region, x, z, "Fundament: kein Sift-Boden unter der Vorlage");
                    }
                    continue;
                }
                if (core && !AbandonedPortalPlan.coreFits(floorY, naturalTop)) {
                    return new Rejection(x, naturalTop, z, "Fundament: Boden " + Math.abs(floorY - naturalTop)
                            + " Blöcke vom Vorlagenboden entfernt (> " + AbandonedPortalPlan.MAX_EARTHWORK + ")");
                }
                int targetTop = AbandonedPortalPlan.foundationTop(core, distance, edge, floorY, naturalTop, region.distanceToEdge(x, z));
                if (!core && naturalTop == targetTop) {
                    continue;
                }
                BlockData originalTop = region.data(x, naturalTop, z);
                int startY = Math.min(naturalTop, targetTop) - 2;
                int endY = Math.max(naturalTop, targetTop) + 3;
                for (int y = startY; y <= endY; y++) {
                    if (!region.writable(x, y, z)) {
                        return new Rejection(x, y, z, "Fundament ragt aus dem Generierungsbereich");
                    }
                    if (!region.canLandmarkReplace(x, y, z)) {
                        return new Rejection(x, y, z, "Fundament: Platz belegt durch " + region.describe(x, y, z));
                    }
                }
                BlockData surface = region.is(x, naturalTop, z, "sift_rubble")
                        ? region.registry().defaultState("siftslate").data() : originalTop;
                terrain.add(new Column(x, z, naturalTop, targetTop, surface));
            }
        }
        return terrain;
    }

    /** AbandonedMainPortalFeature#applyFoundation; returns the new surface blocks. */
    private static List<int[]> applyFoundation(GenRegion region, List<Column> terrain) {
        List<int[]> surfaces = new ArrayList<>();
        for (Column column : terrain) {
            int floorY = column.targetTop();
            for (int y = Math.min(column.naturalTop() + 1, floorY - 2); y <= floorY; y++) {
                if (y > column.naturalTop() || !region.isLandmarkGround(column.x(), y, column.z())
                        || region.is(column.x(), y, column.z(), "sift_rubble")) {
                    region.setSift(column.x(), y, column.z(), "siftslate");
                }
            }
            for (int y = floorY + 1; y <= Math.max(column.naturalTop(), floorY) + 3; y++) {
                region.set(column.x(), y, column.z(), Material.AIR.createBlockData());
            }
            region.set(column.x(), floorY, column.z(), column.surface());
            surfaces.add(new int[] {column.x(), floorY, column.z()});
        }
        return surfaces;
    }

    /** Sets the template blocks; returns the chest position or null. */
    private int[] placeTemplate(GenRegion region, Random random, SiftTemplates.Template template, Plan plan) {
        TemplateTransform transform = plan.transform();
        int[] chest = null;
        for (SiftTemplates.Block block : template.blocks()) {
            if (!placed(block)) {
                continue;
            }
            int[] pos = world(transform, block, plan.originX(), plan.originY(), plan.originZ());
            if (block.custom() != null) {
                region.setSift(pos[0], pos[1], pos[2], block.custom().id(), transform.properties(block.custom().properties()));
                continue;
            }
            BlockData data = block.data().clone();
            if (data instanceof Directional directional && directional.getFacing().getModY() == 0) {
                directional.setFacing(org.bukkit.block.BlockFace.valueOf(
                        transform.direction(directional.getFacing().name().toLowerCase()).toUpperCase()));
            }
            region.set(pos[0], pos[1], pos[2], data);
            if (data.getMaterial() == Material.CHEST) {
                fillChest(region, pos, block.loot(), random);
                chest = pos;
            }
        }
        return chest;
    }

    /** The block that hides the Sift ground of a column (what surfaceBlockY stopped at). */
    private static Rejection blocker(GenRegion region, int x, int z, String what) {
        int top = region.height(org.bukkit.HeightMap.WORLD_SURFACE_WG, x, z) - 1;
        int minimum = Math.max(region.minY() + 1, top - 32);
        for (int y = top; y >= minimum; y--) {
            if (!region.isLandmarkGround(x, y, z) && !region.canLandmarkReplace(x, y, z)) {
                return new Rejection(x, y, z, what + " (verdeckt durch " + region.describe(x, y, z) + ")");
            }
        }
        return new Rejection(x, top, z, what + " (nichts innerhalb von 32 Blöcken unter der Oberfläche)");
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
}
