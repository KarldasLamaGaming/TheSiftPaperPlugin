package de.lamamc.sift.block;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.DropRoller;
import de.lamamc.sift.core.HeldTool;
import de.lamamc.sift.core.ItemDrop;
import de.lamamc.sift.core.ToolType;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Placing, mining and destroying custom blocks: sets the stand-in state when a Sift block item is
 * placed, replaces the drops of the stand-in with the configured Sift drops, and removes plants
 * whose support block is gone (block updates of the stand-ins are off in the Sift).
 */
public final class CustomBlockListener implements Listener {

    private final Plugin plugin;
    private final CustomBlockRegistry registry;
    private final BlockSettings settings;
    private final SiftItems items;

    public CustomBlockListener(Plugin plugin, CustomBlockRegistry registry, BlockSettings settings, SiftItems items) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
        this.items = items;
    }

    // ---- placing ------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        String id = SiftItems.idOf(event.getItemInHand());
        CustomBlock custom = id == null ? null : registry.defaultState(id);
        if (custom == null) {
            guardVanillaPlacement(block);
            return;
        }
        Player player = event.getPlayer();
        if (settings.placeOnlyInSift() && !SiftWorlds.isSift(block.getWorld())) {
            String fallback = settings.fallbackBlock(id);
            if (settings.fallback() == BlockSettings.Fallback.VANILLA && fallback != null) {
                block.setBlockData(Bukkit.createBlockData(fallback), false);
            } else {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "Sift-Blöcke lassen sich nur in der Sift platzieren.",
                        NamedTextColor.RED));
            }
            return;
        }
        if (!supported(id, block)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Kann hier nicht wachsen.",
                    NamedTextColor.RED));
            return;
        }
        block.setBlockData(custom.data(), false);
    }

    /**
     * A vanilla mushroom block or string placed next to custom blocks could compute one of the
     * repurposed states from its neighbors; it gets the plain vanilla state instead.
     */
    private void guardVanillaPlacement(Block block) {
        if (registry.of(block.getBlockData()) != null) {
            block.setBlockData(block.getType().createBlockData(), false);
        }
    }

    // ---- mining -------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CustomBlock custom = registry.at(block);
        if (custom != null) {
            Player player = event.getPlayer();
            BlockProfile profile = settings.profile(custom.id());
            if (profile.unbreakable() && player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
                return;
            }
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                HeldTool held = HeldTools.of(tool);
                boolean harvested = profile.canHarvest(held);
                boolean silk = HeldTools.silkTouch(tool);
                drop(block.getLocation(), DropRoller.roll(profile.drops(), "the_sift:" + custom.id(), harvested, silk,
                        HeldTools.fortune(tool), held.type(), ThreadLocalRandom.current()));
                event.setExpToDrop(DropRoller.experience(profile, harvested, silk, ThreadLocalRandom.current()));
            }
        }
        checkNeighborsLater(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    /**
     * Stand-ins have the blast resistance of mushroom blocks; explosion-proof custom blocks are taken off
     * the list, the others drop their Sift items (simplified: no ray strength like vanilla).
     */
    private void explode(List<Block> blocks, float yield) {
        List<Block> changed = new ArrayList<>();
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block block = it.next();
            CustomBlock custom = registry.at(block);
            if (custom == null) {
                continue;
            }
            it.remove();
            BlockProfile profile = settings.profile(custom.id());
            if (profile.explosionProof() || profile.unbreakable()) {
                continue;
            }
            if (ThreadLocalRandom.current().nextFloat() < yield) {
                drop(block.getLocation(), DropRoller.roll(profile.drops(), "the_sift:" + custom.id(), true, false, 0,
                        ToolType.NONE, ThreadLocalRandom.current()));
            }
            block.setType(Material.AIR, false);
            changed.add(block);
        }
        blocks.forEach(changed::add);
        changed.forEach(this::checkNeighborsLater);
    }

    /** Paper fires this when a block is destroyed by water, pistons or physics; plants drop Sift items. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        CustomBlock custom = registry.at(event.getBlock());
        if (custom == null) {
            return;
        }
        if (event.willDrop()) {
            event.setWillDrop(false);
            BlockProfile profile = settings.profile(custom.id());
            drop(event.getBlock().getLocation(), DropRoller.roll(profile.drops(), "the_sift:" + custom.id(), true,
                    false, 0, ToolType.NONE, ThreadLocalRandom.current()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(this::checkNeighborsLater);
        checkNeighborsLater(event.getBlock().getRelative(event.getDirection()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(this::checkNeighborsLater);
    }

    // ---- pick block ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent event) {
        CustomBlock custom = registry.at(event.getBlock());
        if (custom == null) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (custom.id().equals(SiftItems.idOf(inventory.getItem(slot)))) {
                event.setSourceSlot(slot);
                return;
            }
        }
        // the vanilla pick would give the stand-in (mushroom stem, string)
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.CREATIVE) {
            ItemStack stack = items.create(custom.id(), 1);
            int target = event.getTargetSlot();
            if (stack != null && target >= 0 && target < 9) {
                inventory.setItem(target, stack);
                inventory.setHeldItemSlot(target);
            }
        }
    }

    // ---- support ------------------------------------------------------------------------------------

    private void checkNeighborsLater(Block block) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            checkSupport(block.getRelative(BlockFace.UP));
            checkSupport(block.getRelative(BlockFace.DOWN));
        });
    }

    private void checkSupport(Block block) {
        CustomBlock custom = registry.at(block);
        if (custom == null || supported(custom.id(), block)) {
            return;
        }
        BlockProfile profile = settings.profile(custom.id());
        drop(block.getLocation(), DropRoller.roll(profile.drops(), "the_sift:" + custom.id(), true, false, 0,
                ToolType.NONE, ThreadLocalRandom.current()));
        block.setType(Material.AIR, false);
        // a plant on a plant (hanging roots chains) falls too
        checkNeighborsLater(block);
    }

    private boolean supported(String id, Block block) {
        BlockSettings.Support support = settings.support(id);
        if (support == null) {
            return true;
        }
        Block base = block.getRelative(support.above() ? BlockFace.UP : BlockFace.DOWN);
        if (support.blocks().isEmpty()) {
            return base.getType().isSolid();
        }
        CustomBlock baseCustom = registry.at(base);
        for (String entry : support.blocks()) {
            if (entry.startsWith("#")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.fromString(entry.substring(1)),
                        Material.class);
                if (baseCustom == null && tag != null && tag.isTagged(base.getType())) {
                    return true;
                }
            } else if (entry.startsWith("the_sift:")) {
                if (baseCustom != null && entry.equals("the_sift:" + baseCustom.id())) {
                    return true;
                }
            } else if (baseCustom == null) {
                Material material = Material.matchMaterial(entry);
                if (material == base.getType()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private void drop(Location location, List<ItemDrop> drops) {
        Location center = location.toCenterLocation();
        for (ItemDrop drop : drops) {
            ItemStack stack = items.stackFor(drop.item(), drop.amount());
            if (stack == null) {
                plugin.getLogger().warning("Unknown drop item " + drop.item());
                continue;
            }
            location.getWorld().dropItemNaturally(center, stack);
        }
    }
}
