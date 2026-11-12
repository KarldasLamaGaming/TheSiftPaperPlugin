package de.lamamc.sift.world.rift;

import de.lamamc.sift.item.SiftItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * With rifts.enabled: false (default since alpha.42a.2) no rift opens at all. This only takes away what rifts of earlier
 * versions may have left: light blocks recorded in chunk data (removed when the chunk loads, as the rift manager does)
 * and rift displays; the sift_rift item does nothing then. The Siftite keeper's mini rift does not depend on any of this.
 */
public final class RiftCleanup implements Listener {

    private final Plugin plugin;
    private int lights;

    public RiftCleanup(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                clean(chunk);
            }
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(RiftManager.DISPLAY)) {
                    display.remove();
                }
            }
        }
    }

    /** Light blocks of rifts removed since the start. */
    public int lightsRemoved() {
        return lights;
    }

    private void clean(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] keys = pdc.get(RiftManager.LIGHTS, PersistentDataType.LONG_ARRAY);
        if (keys == null) {
            return;
        }
        for (long key : keys) {
            Block block = chunk.getWorld().getBlockAtKey(key);
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
                lights++;
            }
        }
        pdc.remove(RiftManager.LIGHTS);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                clean(chunk);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if ("sift_rift".equals(SiftItems.idOf(event.getItem()))) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(de.lamamc.sift.Messages.current().get(event.getPlayer(), "rifts.disabled"));
        }
    }
}
