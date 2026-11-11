package de.lamamc.sift.world.ichor;

import de.lamamc.sift.core.ichor.FluidIdentity;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.SiftWorlds;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the marks of {@link FluidMarks} in step with the water (alpha.38): flowing water inherits the mark of the
 * block it comes from, buckets (player and dispenser) mark what they pour, taking water away drops the mark, sponges
 * clear the marks they soak up, and pistons clear the marks of the waterlogged blocks they push (they lose their water).
 */
public final class FluidIdentityListener implements Listener {

    /** Measurement of the hot path (every spread of every fluid), for /sift debug ichor marks. */
    public static final class Stats {
        public final AtomicLong flows = new AtomicLong();
        public final AtomicLong flowNanos = new AtomicLong();
        public final AtomicLong flowMaxNanos = new AtomicLong();
        public final AtomicLong inherited = new AtomicLong();
        public final AtomicLong cleared = new AtomicLong();
        public final AtomicLong buckets = new AtomicLong();
        public final AtomicLong dispensers = new AtomicLong();
        public final AtomicLong moved = new AtomicLong();
    }

    private final org.bukkit.plugin.Plugin plugin;
    private final FluidMarks marks;
    private final SiftItems items;
    private final Stats stats = new Stats();

    public FluidIdentityListener(org.bukkit.plugin.Plugin plugin, FluidMarks marks, SiftItems items) {
        this.plugin = plugin;
        this.marks = marks;
        this.items = items;
    }

    public Stats stats() {
        return stats;
    }

    /** Flowing water takes the identity of the water it comes from (a stale mark on the target is replaced). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        long start = System.nanoTime();
        Block from = event.getBlock();
        if (marks.any(from.getWorld()) && FluidMarks.holdsWater(from) && from.getType() != Material.WATER_CAULDRON) {
            Block to = event.getToBlock();
            boolean mark = marks.marked(from);
            if (mark) {
                if (!marks.marked(to)) {
                    stats.inherited.incrementAndGet();
                }
                marks.set(to, true);
            } else if (marks.marked(to)) {
                marks.set(to, false);
                stats.cleared.incrementAndGet();
            }
        }
        long nanos = System.nanoTime() - start;
        stats.flows.incrementAndGet();
        stats.flowNanos.addAndGet(nanos);
        stats.flowMaxNanos.accumulateAndGet(nanos, Math::max);
    }

    /** A bucket poured by a player: ichor or water, unless it goes into water that is already there. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.WATER_BUCKET) {
            return;
        }
        ItemStack held = event.getHand() == null ? null : event.getPlayer().getInventory().getItem(event.getHand());
        pour(event.getBlock(), held != null && "ichor_bucket".equals(SiftItems.idOf(held)));
        stats.buckets.incrementAndGet();
    }

    private void pour(Block target, boolean ichorBucket) {
        Boolean mark = FluidIdentity.onEmpty(SiftWorlds.isSift(target.getWorld()), ichorBucket, FluidMarks.holdsSource(target));
        if (mark != null) {
            marks.set(target, mark);
        }
    }

    /** The water taken with a bucket is gone; so is its mark (the bucket itself is decided in IchorListener). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        marks.set(event.getBlock(), false);
    }

    /**
     * Dispensers: a water or ichor bucket marks what it pours. An empty bucket at an ichor source is left to vanilla
     * (which always makes a water bucket); one tick later that water bucket in the dispenser becomes an ichor bucket.
     * (Cancelling and filling by hand does not work: after a cancelled dispense vanilla writes the old stack back.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getType() != Material.DISPENSER || !(dispenser.getBlockData() instanceof Directional facing)) {
            return;
        }
        Block target = dispenser.getRelative(facing.getFacing());
        ItemStack item = event.getItem();
        if (item.getType() == Material.WATER_BUCKET) {
            pour(target, "ichor_bucket".equals(SiftItems.idOf(item)));
            stats.dispensers.incrementAndGet();
            return;
        }
        if (item.getType() != Material.BUCKET || !FluidMarks.holdsSource(target) || target.getType() == Material.WATER_CAULDRON) {
            return;
        }
        boolean ichor = marks.ichor(target);
        marks.set(target, false);
        if (!ichor) {
            return;
        }
        stats.dispensers.incrementAndGet();
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(dispenser.getState() instanceof Container container)) {
                return;
            }
            var inventory = container.getInventory();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack != null && stack.getType() == Material.WATER_BUCKET && SiftItems.idOf(stack) == null) {
                    inventory.setItem(slot, items.create("ichor_bucket", 1));
                    return;
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSponge(SpongeAbsorbEvent event) {
        event.getBlocks().forEach(state -> marks.set(state.getBlock(), false));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        move(event.getBlocks(), event.getDirection());
    }

    /** A pushed waterlogged block loses its water (checked in 26.3), so its mark goes too. */
    private void move(List<Block> moved, BlockFace direction) {
        if (moved.isEmpty() || !marks.any(moved.getFirst().getWorld())) {
            return;
        }
        for (Block block : moved) {
            if (marks.marked(block)) {
                marks.set(block, false);
                stats.moved.incrementAndGet();
            }
        }
    }

    /** A water bottle does not go into a cauldron of ichor (and an ichor bottle not into water, see IchorListener). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBottle(CauldronLevelChangeEvent event) {
        if (event.getReason() == CauldronLevelChangeEvent.ChangeReason.BOTTLE_EMPTY
                && FluidIdentity.mixes(event.getOldLevel(), marks.ichor(event.getBlock()), false)) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                player.sendActionBar(net.kyori.adventure.text.Component.text("Ichor und Wasser mischen sich nicht."));
            }
        }
    }

    /**
     * Cauldrons: a bucket poured in marks it like the bucket; emptied, it loses its mark. Ichor bottles are handled in
     * IchorListener (they are no vanilla water bottles).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        Block cauldron = event.getBlock();
        if (event.getNewState().getType() != Material.WATER_CAULDRON) {
            marks.set(cauldron, false);
            return;
        }
        if (event.getReason() == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY && event.getEntity() instanceof Player player) {
            boolean ichor = "ichor_bucket".equals(SiftItems.idOf(player.getInventory().getItemInMainHand()))
                    || player.getInventory().getItemInMainHand().getType() != Material.WATER_BUCKET
                    && "ichor_bucket".equals(SiftItems.idOf(player.getInventory().getItemInOffHand()));
            marks.set(cauldron, FluidIdentity.markFor(SiftWorlds.isSift(cauldron.getWorld()), ichor));
        }
    }
}
