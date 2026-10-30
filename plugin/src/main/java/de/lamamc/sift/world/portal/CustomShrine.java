package de.lamamc.sift.world.portal;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.portal.PortalFrame;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.structure.Structure;

/**
 * A replacement shrine from plugins/TheSift/structures/&lt;overworld|nether|end&gt;.nbt (e.g. built with Axiom and
 * saved with a structure block). Its lowest layer is the floor; it must contain a frame of reinforced
 * deepslate and sonorous deepslate consoles, and fit into 16 × 16 blocks. Structure voids are skipped.
 */
public final class CustomShrine {

    public record Block(int x, int y, int z, BlockData data) {
    }

    private CustomShrine() {
    }

    /** The template's blocks, or an exception saying why it cannot be used. Main thread. */
    public static List<Block> load(File file, CustomBlockRegistry registry) throws IOException {
        Structure structure = Bukkit.getStructureManager().loadStructure(file);
        if (structure.getSize().getBlockX() > 16 || structure.getSize().getBlockZ() > 16) {
            throw new IOException("larger than 16 x 16 blocks (" + structure.getSize() + ")");
        }
        List<Block> blocks = new ArrayList<>();
        for (BlockState state : structure.getPalettes().getFirst().getBlocks()) {
            if (state.getType() != Material.STRUCTURE_VOID) {
                blocks.add(new Block(state.getX(), state.getY(), state.getZ(), state.getBlockData()));
            }
        }
        if (frame(blocks).isEmpty()) {
            throw new IOException("no frame of reinforced deepslate (3-23 blocks, free inside)");
        }
        long consoles = blocks.stream().filter(b -> {
            CustomBlock custom = registry.of(b.data());
            return custom != null && custom.id().equals(SonorousConsoles.BLOCK_ID);
        }).count();
        if (consoles == 0) {
            throw new IOException("no sonorous deepslate (consoles)");
        }
        return blocks;
    }

    /** The frame in template coordinates. */
    static Optional<PortalFrame> frame(List<Block> blocks) {
        Set<String> frame = new HashSet<>();
        Set<String> solid = new HashSet<>();
        for (Block b : blocks) {
            String key = b.x() + "," + b.y() + "," + b.z();
            if (b.data().getMaterial() == Material.REINFORCED_DEEPSLATE) {
                frame.add(key);
            } else if (!b.data().getMaterial().isAir()) {
                solid.add(key);
            }
        }
        PortalFrame.Grid grid = new PortalFrame.Grid() {
            @Override
            public boolean isFrame(int x, int y, int z) {
                return frame.contains(x + "," + y + "," + z);
            }

            @Override
            public boolean isOpen(int x, int y, int z) {
                return !frame.contains(x + "," + y + "," + z) && !solid.contains(x + "," + y + "," + z);
            }
        };
        for (Block b : blocks) {
            if (b.data().getMaterial() == Material.REINFORCED_DEEPSLATE) {
                Optional<PortalFrame> found = PortalFrame.scan(grid, b.x(), b.y(), b.z());
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    static int footprint(List<Block> blocks) {
        return 1 + blocks.stream().mapToInt(b -> Math.max(b.x(), b.z())).max().orElse(0);
    }

    static int height(List<Block> blocks) {
        return blocks.stream().mapToInt(Block::y).max().orElse(0);
    }
}
