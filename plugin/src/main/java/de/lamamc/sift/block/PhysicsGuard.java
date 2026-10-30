package de.lamamc.sift.block;

import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.Plugin;

/**
 * Keeps the repurposed states of custom blocks: vanilla recomputes the faces of mushroom blocks and the
 * connections of tripwire and chorus plants whenever a neighbor changes, which would turn one custom
 * block into another. Paper's block-updates switches would prevent that, but they are global (they also
 * break tripwire traps and chorus farms in every other world), so the plugin guards the custom blocks
 * itself.
 *
 * <p>Paper fires a BlockPhysicsEvent for a changed block before it updates the shapes of its neighbors.
 * If a custom block is next to it, the event is cancelled; the vanilla neighbors then get the important
 * part of their update one tick later: blocks that lost their support break, liquids flow.
 */
public final class PhysicsGuard implements Listener {

    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};

    private final Plugin plugin;
    private final CustomBlockRegistry registry;
    private final boolean siftOnly;

    /** @param siftOnly guard only the Sift (custom blocks cannot be placed elsewhere) */
    public PhysicsGuard(Plugin plugin, CustomBlockRegistry registry, boolean siftOnly) {
        this.plugin = plugin;
        this.registry = registry;
        this.siftOnly = siftOnly;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (siftOnly && !SiftWorlds.isSift(block.getWorld()) && !nearStandIn(block)) {
            return; // outside the Sift only the few blocks allowed there (consoles) need guarding
        }
        if (isCustom(block)) {
            event.setCancelled(true);
            return;
        }
        List<Block> vanillaNeighbors = null;
        boolean nextToCustom = false;
        for (BlockFace face : FACES) {
            Block neighbor = block.getRelative(face);
            if (isCustom(neighbor)) {
                nextToCustom = true;
            } else {
                if (vanillaNeighbors == null) {
                    vanillaNeighbors = new ArrayList<>(6);
                }
                vanillaNeighbors.add(neighbor);
            }
        }
        if (!nextToCustom) {
            return;
        }
        event.setCancelled(true);
        if (vanillaNeighbors != null) {
            List<Block> toUpdate = vanillaNeighbors;
            Bukkit.getScheduler().runTask(plugin, () -> toUpdate.forEach(PhysicsGuard::followUp));
        }
    }

    /** Cheap pre-check outside the Sift: a stand-in material at or next to the block. */
    private static boolean nearStandIn(Block block) {
        if (CustomBlockRegistry.STAND_IN_BLOCKS.contains(block.getType())) {
            return true;
        }
        for (BlockFace face : FACES) {
            if (CustomBlockRegistry.STAND_IN_BLOCKS.contains(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCustom(Block block) {
        return CustomBlockRegistry.STAND_IN_BLOCKS.contains(block.getType()) && registry.at(block) != null;
    }

    /** The part of the skipped shape update that matters for gameplay. */
    private static void followUp(Block block) {
        BlockData data = block.getBlockData();
        if (data.getMaterial().isAir()) {
            return;
        }
        if (!data.isSupported(block)) {
            block.breakNaturally();
            return;
        }
        if (block.isLiquid() || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            block.fluidTick();
        }
    }
}
