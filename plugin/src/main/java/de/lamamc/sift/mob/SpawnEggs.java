package de.lamamc.sift.mob;

import de.lamamc.sift.item.SiftItems;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** Spawn eggs of the Sift mobs (&lt;mob&gt;_spawn_egg): right-click a block like a vanilla spawn egg. */
public final class SpawnEggs implements Listener {

    private static final String SUFFIX = "_spawn_egg";

    private final MobManager mobs;

    public SpawnEggs(MobManager mobs) {
        this.mobs = mobs;
    }

    /** Mob id of a spawn egg item, or null. */
    public static String mobOf(ItemStack stack) {
        String id = SiftItems.idOf(stack);
        return id != null && id.endsWith(SUFFIX) ? id.substring(0, id.length() - SUFFIX.length()) : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
            return;
        }
        String id = mobOf(event.getItem());
        MobKind kind = id == null ? null : mobs.kind(id);
        if (kind == null) {
            return;
        }
        event.setCancelled(true);
        if (!kind.enabled()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Block target = clicked.isPassable() ? clicked : clicked.getRelative(event.getBlockFace());
        Location at = target.getLocation().add(0.5, 0, 0.5);
        at.setYaw(event.getPlayer().getLocation().getYaw() + 180f);
        if (mobs.spawn(kind, at, false, null) != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.getItem().subtract(1);
        }
    }
}
