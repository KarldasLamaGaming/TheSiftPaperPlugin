package de.lamamc.sift.world.portal;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalSites;
import de.lamamc.sift.core.portal.PortalState;
import de.lamamc.sift.core.portal.ShrineLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Builds the portal shrine of a dimension at its sites (overworld, nether, end) while chunks generate:
 * a foundation down to the ground (a rock pedestal over water, basalt over lava, a small island over the
 * void), a cleared space, the shrine with a frame and eight sleeping consoles, note blocks with random
 * sounds. The portal itself is registered on the main thread (the registry lives in world data).
 */
public final class ShrinePopulator extends BlockPopulator {

    /** A shrine built by worldgen, waiting to be registered on the main thread. */
    public record Built(String worldKey, SiftPortal portal) {
    }

    public enum Dimension { OVERWORLD, NETHER, END }

    private static final int NETHER_MIN_Y = 32;
    private static final int NETHER_MAX_Y = 100;
    private static final int NETHER_FALLBACK_Y = 70;
    private static final int END_ISLAND_Y = 60;
    private static final int END_ISLAND_RADIUS = 9;
    private static final int MAX_FOUNDATION = 40;

    private final Dimension dimension;
    private final ShrinePalette palette;
    private final PortalSites sites;
    private final CustomBlockRegistry blocks;
    private final BlockData console;
    private final List<CustomShrine.Block> custom;
    private final Queue<Built> built;
    private final Logger logger;
    final AtomicLong count = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    /** @param custom blocks of a replacement template (plugins/TheSift/structures), or null for the built-in shrine */
    public ShrinePopulator(Dimension dimension, PortalSites sites, CustomBlockRegistry blocks, List<CustomShrine.Block> custom,
                           Queue<Built> built, Logger logger) {
        this.dimension = dimension;
        this.palette = ShrinePalette.values()[dimension.ordinal()];
        this.palette.prepare();
        this.sites = sites;
        this.blocks = blocks;
        CustomBlock horn = blocks.state(SonorousConsoles.BLOCK_ID, Map.of("mode", "horn"));
        this.console = horn == null ? null : horn.data();
        this.custom = custom;
        this.built = built;
        this.logger = logger;
    }

    public static Queue<Built> newQueue() {
        return new ConcurrentLinkedQueue<>();
    }

    public PortalSites sites() {
        return sites;
    }

    @Override
    public void populate(WorldInfo world, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        if (console == null || !sites.isSite(world.getSeed(), chunkX, chunkZ)) {
            return;
        }
        try {
            build(world, new Random(world.getSeed() ^ ((long) chunkX << 32) ^ chunkZ ^ sites.salt()), chunkX, chunkZ, region);
        } catch (RuntimeException e) {
            if (errors.incrementAndGet() <= 5) {
                logger.log(Level.WARNING, "Portal shrine in chunk " + chunkX + " " + chunkZ + " failed", e);
            }
        }
    }

    private void build(WorldInfo world, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int size = custom == null ? ShrineLayout.SIZE : CustomShrine.footprint(custom);
        int floorY = switch (dimension) {
            case OVERWORLD -> overworldFloor(region, x0, z0, size);
            case NETHER -> netherFloor(region, x0, z0, size, world.getMinHeight());
            case END -> endFloor(region, x0, z0, size, world.getMinHeight());
        };
        int height = custom == null ? ShrineLayout.CLEAR_HEIGHT : CustomShrine.height(custom);
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                foundation(region, x, floorY - 1, z, world.getMinHeight());
                for (int y = floorY + 1; y <= floorY + height && y < world.getMaxHeight(); y++) {
                    region.setType(x, y, z, Material.AIR);
                }
            }
        }
        if (dimension == Dimension.NETHER) {
            sealLava(region, x0, z0, size, floorY, height);
        }
        List<int[]> consoles = new ArrayList<>();
        PortalFrame frame;
        if (custom == null) {
            for (ShrineLayout.Placement p : ShrineLayout.build(palette.ruined, random.nextLong())) {
                set(region, x0 + p.x(), floorY + p.y(), z0 + p.z(), p.role(), random, consoles);
            }
            frame = ShrineLayout.frameAt(x0, floorY, z0);
        } else {
            for (CustomShrine.Block b : custom) {
                int x = x0 + b.x();
                int y = floorY + b.y();
                int z = z0 + b.z();
                CustomBlock customBlock = blocks.of(b.data());
                if (customBlock != null && customBlock.id().equals(SonorousConsoles.BLOCK_ID)) {
                    region.setBlockData(x, y, z, console);
                    consoles.add(new int[] {x, y, z});
                } else if (b.data().getMaterial() == Material.NOTE_BLOCK) {
                    region.setBlockData(x, y, z, note(random));
                } else {
                    region.setBlockData(x, y, z, b.data());
                }
            }
            frame = CustomShrine.frame(custom).orElseThrow();
            frame = new PortalFrame(frame.axis(), x0 + frame.minX(), floorY + frame.minY(), z0 + frame.minZ(), frame.width(),
                    frame.height());
        }
        String id = "shrine-" + chunkX + "-" + chunkZ;
        built.add(new Built(world.getKey().toString(), new SiftPortal(id, SiftPortal.Kind.ENTRANCE, frame, PortalState.LOCKED, consoles)));
        count.incrementAndGet();
    }

    private void set(LimitedRegion region, int x, int y, int z, ShrineLayout.Role role, Random random, List<int[]> consoles) {
        switch (role) {
            case CONSOLE -> {
                region.setBlockData(x, y, z, console);
                consoles.add(new int[] {x, y, z});
            }
            case NOTE_BLOCK -> region.setBlockData(x, y, z, note(random));
            case DECOR -> {
                region.setBlockData(x, y, z, palette.block(role));
                if (dimension == Dimension.NETHER && random.nextInt(3) == 0 && region.getType(x, y + 1, z).isAir()) {
                    region.setType(x, y + 1, z, Material.SOUL_FIRE);
                }
            }
            default -> region.setBlockData(x, y, z, palette.block(role));
        }
    }

    private static BlockData note(Random random) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setNote(new Note(random.nextInt(8)));
        return data;
    }

    /** Fills from below the floor down to solid ground (pedestal over water, basalt over lava). */
    private void foundation(LimitedRegion region, int x, int fromY, int z, int minY) {
        for (int y = fromY, depth = 0; y > minY && depth < MAX_FOUNDATION; y--, depth++) {
            Material type = region.getType(x, y, z);
            if (type.isSolid() && type != Material.BEDROCK && depth > 0) {
                return;
            }
            region.setBlockData(x, y, z, palette.foundation());
        }
    }

    /** Top of the ground under the shrine, sunk one block; over water one block above the water. */
    private static int overworldFloor(LimitedRegion region, int x0, int z0, int size) {
        int[] heights = new int[size * size];
        boolean wet = false;
        int i = 0;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                int top = region.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE_WG) - 1;
                heights[i++] = top;
                wet |= region.getType(x, top, z) == Material.WATER;
            }
        }
        Arrays.sort(heights);
        int median = heights[heights.length / 2];
        return wet ? median + 1 : median - 1;
    }

    /** The lowest cave floor in the shrine's middle with room above, else y 70 hollowed out. */
    private static int netherFloor(LimitedRegion region, int x0, int z0, int size, int minY) {
        int cx = x0 + size / 2;
        int cz = z0 + size / 2;
        for (int y = Math.max(minY + 1, NETHER_MIN_Y); y <= NETHER_MAX_Y; y++) {
            if (!region.getType(cx, y, cz).isSolid()) {
                continue;
            }
            boolean room = true;
            for (int up = 1; up <= ShrineLayout.CLEAR_HEIGHT + 2 && room; up++) {
                Material above = region.getType(cx, y + up, cz);
                room = above.isAir();
            }
            if (room) {
                return y;
            }
        }
        return NETHER_FALLBACK_Y;
    }

    /** Lava next to the cleared space would pour in: the walls around it become blackstone. */
    private static void sealLava(LimitedRegion region, int x0, int z0, int size, int floorY, int height) {
        for (int y = floorY - 1; y <= floorY + height; y++) {
            for (int x = x0 - 1; x <= x0 + size; x++) {
                for (int z = z0 - 1; z <= z0 + size; z++) {
                    boolean ring = x == x0 - 1 || z == z0 - 1 || x == x0 + size || z == z0 + size;
                    if ((ring || y == floorY + height) && region.isInRegion(x, y, z) && region.getType(x, y, z) == Material.LAVA) {
                        region.setType(x, y, z, Material.BLACKSTONE);
                    }
                }
            }
        }
    }

    /** The island surface; over the void a small end stone island at y 60. */
    private static int endFloor(LimitedRegion region, int x0, int z0, int size, int minY) {
        int cx = x0 + size / 2;
        int cz = z0 + size / 2;
        int top = region.getHighestBlockYAt(cx, cz, HeightMap.WORLD_SURFACE_WG) - 1;
        if (top > minY + 5 && region.getType(cx, top, cz) != Material.AIR) {
            return top;
        }
        for (int x = cx - END_ISLAND_RADIUS; x <= cx + END_ISLAND_RADIUS; x++) {
            for (int z = cz - END_ISLAND_RADIUS; z <= cz + END_ISLAND_RADIUS; z++) {
                double d = Math.hypot(x - cx, z - cz);
                int depth = (int) Math.round((END_ISLAND_RADIUS - d) * 0.8);
                for (int y = END_ISLAND_Y - depth; y <= END_ISLAND_Y && d <= END_ISLAND_RADIUS; y++) {
                    if (region.isInRegion(x, y, z)) {
                        region.setType(x, y, z, Material.END_STONE);
                    }
                }
            }
        }
        return END_ISLAND_Y;
    }
}
