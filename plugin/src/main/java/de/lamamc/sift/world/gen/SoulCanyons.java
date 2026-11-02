package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.worldgen.LandmarkPlacement;
import de.lamamc.sift.core.worldgen.SoulCanyonPlan;
import java.util.Collection;
import java.util.random.RandomGenerator;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Builds soul canyons (SoulCanyonFeature of the original 1.1.1): at most one per cell of 12 × 12 chunks,
 * carved into the terrain of the 3 × 3 chunks around the selected chunk, with soul blocks in the floor and
 * the markers the echo golem needs. The same code builds a canyon on loaded terrain for /sift debug canyon.
 */
public final class SoulCanyons {

    /** The blocks a canyon is carved into: a worldgen region or a loaded world. */
    interface Blocks extends SoulCanyonPlan.Terrain {
        boolean writable(int x, int y, int z);

        BlockData data(int x, int y, int z);

        void set(int x, int y, int z, BlockData data);
    }

    /** Why a canyon was not built (for the command and the counters). */
    public enum Outcome { BUILT, NOT_SELECTED, BIOME, PORTAL, TERRAIN, RESERVED }

    private final CustomBlockRegistry registry;
    private final SoulCanyonLandmarks landmarks;
    private final int cellChunks;
    private final SoulCanyonPlan.Settings settings;

    public SoulCanyons(CustomBlockRegistry registry, SoulCanyonLandmarks landmarks, int cellChunks, int minSouls) {
        this.registry = registry;
        this.landmarks = landmarks;
        this.cellChunks = cellChunks;
        this.settings = SoulCanyonPlan.Settings.defaults().withMinSouls(minSouls);
    }

    public boolean isSelected(long seed, int chunkX, int chunkZ) {
        return LandmarkPlacement.isSelectedChunk(seed, chunkX, chunkZ, cellChunks, LandmarkPlacement.SOUL_CANYON_SALT);
    }

    public SoulCanyonLandmarks landmarks() {
        return landmarks;
    }

    /** The plan of the last successful build, for the command's report. */
    public record Built(Outcome outcome, SoulCanyonPlan.Result plan) {
    }

    /**
     * Plans and builds a canyon centred on x/z. {@code reservation} may be null (command); otherwise the
     * carved rectangle is reserved like a solid landmark, so later landmarks keep out of it.
     */
    Built build(Blocks blocks, int centerX, int centerZ, RandomGenerator random, PortalReservation reservation) {
        SoulCanyonPlan.Result plan = SoulCanyonPlan.plan(blocks, columns -> canCarve(blocks, columns), centerX, centerZ, random,
                settings);
        if (plan == null) {
            return new Built(Outcome.TERRAIN, null);
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SoulCanyonPlan.Column column : plan.columns().values()) {
            minX = Math.min(minX, column.x());
            minZ = Math.min(minZ, column.z());
            maxX = Math.max(maxX, column.x());
            maxZ = Math.max(maxZ, column.z());
        }
        if (reservation != null && !reservation.tryReserveSolid(minX - 2, minZ - 2, maxX + 2, maxZ + 2)) {
            return new Built(Outcome.RESERVED, plan);
        }
        BlockData siftslate = registry.defaultState("siftslate").data();
        BlockData air = Material.AIR.createBlockData();
        for (SoulCanyonPlan.Column column : plan.columns().values()) {
            // reinforce the floor: anything that is not ground (or is rubble, which would fall) becomes siftslate
            for (int y = column.floorY() - 2; y <= column.floorY(); y++) {
                BlockData data = blocks.data(column.x(), y, column.z());
                CustomBlock custom = registry.of(data);
                if (!GenRegion.landmarkGround(registry, data) || custom != null && custom.id().equals("sift_rubble")) {
                    blocks.set(column.x(), y, column.z(), siftslate);
                }
            }
            for (int y = column.floorY() + 1; y <= column.topY(); y++) {
                blocks.set(column.x(), y, column.z(), air);
            }
        }
        BlockData soul = registry.defaultState(SoulCanyonLandmarks.SOUL_BLOCK_ID).data();
        for (SoulCanyonPlan.Pos pos : plan.souls()) {
            blocks.set(pos.x(), pos.y(), pos.z(), soul);
            landmarks.record(SoulCanyonLandmarks.Kind.SOUL_BLOCK, pos.x(), pos.y(), pos.z());
        }
        landmarks.record(SoulCanyonLandmarks.Kind.ENTRANCE, plan.entrance().x(), plan.entrance().y(), plan.entrance().z());
        landmarks.record(SoulCanyonLandmarks.Kind.MIDPOINT, plan.midpoint().x(), plan.midpoint().y(), plan.midpoint().z());
        landmarks.record(SoulCanyonLandmarks.Kind.CLUSTER, plan.cluster().x(), plan.cluster().y(), plan.cluster().z());
        landmarks.record(SoulCanyonLandmarks.Kind.GOLEM_PENDING, plan.entrance().x(), plan.entrance().y(), plan.entrance().z());
        return new Built(Outcome.BUILT, plan);
    }

    private boolean canCarve(Blocks blocks, Collection<SoulCanyonPlan.Column> columns) {
        for (SoulCanyonPlan.Column column : columns) {
            for (int y = column.floorY() - 2; y <= column.topY(); y++) {
                if (!blocks.writable(column.x(), y, column.z())
                        || !GenRegion.landmarkReplaceable(registry, blocks.data(column.x(), y, column.z()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Worldgen: the populator's region, limited to the 3 × 3 chunks around the chunk. */
    static Blocks of(GenRegion region, int chunkX, int chunkZ) {
        int minX = (chunkX - 1) << 4;
        int maxX = ((chunkX + 2) << 4) - 1;
        int minZ = (chunkZ - 1) << 4;
        int maxZ = ((chunkZ + 2) << 4) - 1;
        return new Blocks() {
            @Override
            public int surfaceY(int x, int z) {
                return region.landmarkSurfaceY(x, z);
            }

            @Override
            public boolean contains(int x, int z) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            }

            @Override
            public int minY() {
                return region.minY();
            }

            @Override
            public boolean writable(int x, int y, int z) {
                return contains(x, z) && y >= region.minY() && y < region.maxY() && region.region().isInRegion(x, y, z);
            }

            @Override
            public BlockData data(int x, int y, int z) {
                return region.data(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                region.set(x, y, z, data);
            }
        };
    }

    /** Command: a loaded world, limited to the loaded 3 × 3 chunks around x/z. */
    Blocks of(World world, int centerX, int centerZ) {
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        int minX = (chunkX - 1) << 4;
        int maxX = ((chunkX + 2) << 4) - 1;
        int minZ = (chunkZ - 1) << 4;
        int maxZ = ((chunkZ + 2) << 4) - 1;
        return new Blocks() {
            @Override
            public int surfaceY(int x, int z) {
                // the WG heightmap is gone after generation; WORLD_SURFACE is the same on untouched terrain
                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                int minimum = Math.max(world.getMinHeight() + 1, top - 32);
                for (int y = top; y >= minimum; y--) {
                    BlockData data = world.getBlockData(x, y, z);
                    if (GenRegion.landmarkGround(registry, data)) {
                        return y;
                    }
                    if (!GenRegion.landmarkReplaceable(registry, data)) {
                        return Integer.MIN_VALUE;
                    }
                }
                return Integer.MIN_VALUE;
            }

            @Override
            public boolean contains(int x, int z) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ && world.isChunkLoaded(x >> 4, z >> 4);
            }

            @Override
            public int minY() {
                return world.getMinHeight();
            }

            @Override
            public boolean writable(int x, int y, int z) {
                return contains(x, z) && y >= world.getMinHeight() && y < world.getMaxHeight();
            }

            @Override
            public BlockData data(int x, int y, int z) {
                return world.getBlockData(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, BlockData data) {
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }
        };
    }

    /** Builds a canyon on loaded terrain around x/z (main thread; /sift debug canyon build). */
    public Built buildAt(World world, int x, int z, RandomGenerator random) {
        return build(of(world, x, z), x, z, random, null);
    }
}
