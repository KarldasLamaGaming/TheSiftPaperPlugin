package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.TemplateTransform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

/**
 * Overgrown willows (OvergrownWillowTreeFeature): a small or big template, randomly rotated and mirrored
 * around its trunk, only on vegetation ground, not within 5 blocks of another trunk, with surface roots
 * and hanging vines of varying length. The vines are vanilla vines (the willow vines are a substitute).
 */
final class WillowTrees {

    static final String SMALL = "overgrown_willow/small_01";
    static final String BIG = "overgrown_willow/big_01";
    private static final int[] SMALL_ROOT = {3, 6};
    private static final int[] BIG_ROOT = {7, 6};
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private static final Set<String> TREE_GROUND = Set.of("siftslate_growth", "healthy_sculk", "dry_healthy_sculk",
            "dry_healthy_sculk_growth");
    private static final Set<String> TRUNK = Set.of("overgrown_willow_log", "overgrown_willow_wood");

    private final SiftTemplates templates;
    private final double bigChance;
    private final BlockData air = Bukkit.createBlockData(Material.AIR);

    WillowTrees(SiftTemplates templates, double bigChance) {
        this.templates = templates;
        this.bigChance = bigChance;
    }

    /** One attempt at a random spot of the chunk. */
    boolean place(GenRegion region, Random random, int chunkX, int chunkZ) {
        int rootX = (chunkX << 4) + 4 + random.nextInt(8);
        int rootZ = (chunkZ << 4) + 4 + random.nextInt(8);
        int rootY = region.height(HeightMap.WORLD_SURFACE_WG, rootX, rootZ);
        if (!isTreeGround(region, rootX, rootY - 1, rootZ)) {
            return false;
        }
        boolean big = random.nextFloat() < bigChance;
        SiftTemplates.Template template = templates.get(big ? BIG : SMALL);
        if (template == null) {
            return false;
        }
        int[] pivot = big ? BIG_ROOT : SMALL_ROOT;
        TemplateTransform transform = TemplateTransform.random(random, pivot[0], pivot[1]);
        int originX = rootX - pivot[0];
        int originZ = rootZ - pivot[1];

        List<Placed> logs = new ArrayList<>();
        List<Placed> foliage = new ArrayList<>();
        List<Placed> vines = new ArrayList<>();
        for (SiftTemplates.Block block : template.blocks()) {
            int[] p = transform.apply(block.x(), block.y(), block.z());
            Placed placed = new Placed(originX + p[0], rootY + p[1], originZ + p[2], block, transform);
            if (block.custom() != null && TRUNK.contains(block.custom().id())) {
                logs.add(placed);
            } else if (block.is("overgrown_willow_foliage")) {
                foliage.add(placed);
            } else if (block.data().getMaterial() == Material.VINE) {
                vines.add(placed);
            }
        }
        for (List<Placed> part : List.of(logs, foliage, vines)) {
            for (Placed placed : part) {
                if (!region.contains(placed.x(), placed.z())) {
                    return false;
                }
            }
        }
        Placed base = logs.stream().min(Comparator.comparingInt(Placed::y)
                .thenComparingInt(p -> (p.x() - rootX) * (p.x() - rootX) + (p.z() - rootZ) * (p.z() - rootZ))).orElse(null);
        if (base == null || !isTreeGround(region, base.x(), base.y() - 1, base.z()) || hasNearbyTrunk(region, base, 5)) {
            return false;
        }
        for (Placed log : logs) {
            if (!region.writable(log.x(), log.y(), log.z()) || !canReplace(region, log.x(), log.y(), log.z())) {
                return false;
            }
        }
        int count = placeAll(region, logs) + placeAll(region, foliage);
        vines.sort(Comparator.comparingInt(Placed::y).reversed());
        count += placeAll(region, vines);
        alignVines(region, vines);
        addSurfaceRoots(region, random, base);
        varyHangingVines(region, random, vines);
        return count > 0;
    }

    private record Placed(int x, int y, int z, SiftTemplates.Block block, TemplateTransform transform) {
    }

    private int placeAll(GenRegion region, List<Placed> blocks) {
        int placed = 0;
        for (Placed p : blocks) {
            if (region.writable(p.x(), p.y(), p.z()) && canReplace(region, p.x(), p.y(), p.z())) {
                if (p.block().custom() != null) {
                    region.setSift(p.x(), p.y(), p.z(), p.block().custom().id(), p.transform().properties(p.block().custom().properties()));
                } else {
                    region.set(p.x(), p.y(), p.z(), rotateVine(p.block().data(), p.transform()));
                }
                placed++;
            }
        }
        return placed;
    }

    private static BlockData rotateVine(BlockData data, TemplateTransform transform) {
        if (!(data instanceof MultipleFacing vine)) {
            return data;
        }
        MultipleFacing rotated = (MultipleFacing) data.clone();
        for (BlockFace side : SIDES) {
            rotated.setFace(side, false);
        }
        for (BlockFace side : SIDES) {
            if (vine.hasFace(side)) {
                rotated.setFace(BlockFace.valueOf(transform.direction(side.name().toLowerCase()).toUpperCase()), true);
            }
        }
        return rotated;
    }

    /** Vines hold on to solid neighbours or the vine above; unsupported template vines disappear. */
    private void alignVines(GenRegion region, List<Placed> topDown) {
        for (Placed p : topDown) {
            if (!region.contains(p.x(), p.z()) || !(region.data(p.x(), p.y(), p.z()) instanceof MultipleFacing state)
                    || region.type(p.x(), p.y(), p.z()) != Material.VINE) {
                continue;
            }
            BlockData aboveData = region.data(p.x(), p.y() + 1, p.z());
            boolean aboveIsVine = aboveData.getMaterial() == Material.VINE;
            boolean upSupport = region.sturdy(p.x(), p.y() + 1, p.z(), BlockFace.DOWN);
            boolean[] direct = new boolean[4];
            boolean anyDirect = false;
            for (int i = 0; i < 4; i++) {
                BlockFace side = SIDES[i];
                direct[i] = region.sturdy(p.x() + side.getModX(), p.y(), p.z() + side.getModZ(), side.getOppositeFace());
                anyDirect |= direct[i];
            }
            boolean anyFace = false;
            MultipleFacing updated = (MultipleFacing) state.clone();
            for (int i = 0; i < 4; i++) {
                BlockFace side = SIDES[i];
                boolean inherited = aboveIsVine && ((MultipleFacing) aboveData).hasFace(side);
                boolean keepAuthored = !anyDirect && !aboveIsVine && upSupport && state.hasFace(side);
                boolean active = direct[i] || inherited || keepAuthored;
                updated.setFace(side, active);
                anyFace |= active;
            }
            updated.setFace(BlockFace.UP, upSupport);
            region.set(p.x(), p.y(), p.z(), anyFace || upSupport ? updated : air);
        }
    }

    /** 2–4 roots of 1–3 logs lying along the ground from the trunk base. */
    private void addSurfaceRoots(GenRegion region, Random random, Placed base) {
        String baseId = region.id(base.x(), base.y(), base.z());
        if (baseId == null || !TRUNK.contains(baseId) || !isTreeGround(region, base.x(), base.y() - 1, base.z())) {
            return;
        }
        int first = random.nextInt(4);
        int roots = 2 + random.nextInt(3);
        for (int index = 0; index < roots; index++) {
            BlockFace side = SIDES[(first + index) % 4];
            int length = 1 + random.nextInt(3);
            String axis = side.getModX() != 0 ? "x" : "z";
            for (int step = 1; step <= length; step++) {
                int x = base.x() + side.getModX() * step;
                int z = base.z() + side.getModZ() * step;
                if (!region.writable(x, base.y(), z) || !canReplace(region, x, base.y(), z)
                        || !isTreeGround(region, x, base.y() - 1, z)) {
                    break;
                }
                region.setSift(x, base.y(), z, "overgrown_willow_log", Map.of("axis", axis));
            }
        }
    }

    /** About a third of the vine tips grow 1–3 blocks longer. */
    private void varyHangingVines(GenRegion region, Random random, List<Placed> vines) {
        for (Placed p : vines) {
            for (BlockFace side : SIDES) {
                if (!(region.data(p.x(), p.y(), p.z()) instanceof MultipleFacing tip) || region.type(p.x(), p.y(), p.z()) != Material.VINE) {
                    break;
                }
                boolean endsHere = !(region.data(p.x(), p.y() - 1, p.z()) instanceof MultipleFacing below
                        && below.getMaterial() == Material.VINE && below.hasFace(side));
                if (!tip.hasFace(side) || !endsHere || random.nextFloat() >= 0.34f) {
                    continue;
                }
                int extension = 1 + random.nextInt(3);
                int y = p.y();
                for (int step = 0; step < extension; step++) {
                    int belowY = y - 1;
                    if (!region.writable(p.x(), belowY, p.z())) {
                        break;
                    }
                    BlockData below = region.data(p.x(), belowY, p.z());
                    MultipleFacing next;
                    if (below.getMaterial().isAir()) {
                        next = (MultipleFacing) Bukkit.createBlockData(Material.VINE);
                    } else if (below.getMaterial() == Material.VINE) {
                        next = (MultipleFacing) below.clone();
                    } else {
                        break;
                    }
                    next.setFace(side, true);
                    region.set(p.x(), belowY, p.z(), next);
                    y = belowY;
                }
            }
        }
    }

    private static boolean hasNearbyTrunk(GenRegion region, Placed base, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 10; dy++) {
                    int x = base.x() + dx;
                    int y = base.y() + dy;
                    int z = base.z() + dz;
                    if (region.writable(x, y, z)) {
                        String id = region.id(x, y, z);
                        if (id != null && TRUNK.contains(id)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static boolean isTreeGround(GenRegion region, int x, int y, int z) {
        String id = region.id(x, y, z);
        return id != null ? TREE_GROUND.contains(id) : region.supportsVegetation(x, y, z);
    }

    private static boolean canReplace(GenRegion region, int x, int y, int z) {
        if (region.isAir(x, y, z) || region.type(x, y, z) == Material.VINE) {
            return true;
        }
        String id = region.id(x, y, z);
        return id != null && (id.equals("overgrown_willow_foliage") || GenRegion.SMALL_PLANTS.contains(id));
    }
}
