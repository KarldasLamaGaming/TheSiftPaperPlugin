package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.KeyLoot;
import de.lamamc.sift.item.SiftItems;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/** Puts a portal key into some ancient city, bastion and end city chests (portals.keys.*.loot). */
public final class PortalKeyLoot implements Listener {

    private final KeyLoot loot;
    private final SiftItems items;

    public PortalKeyLoot(KeyLoot loot, SiftItems items) {
        this.loot = loot;
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        for (String id : loot.roll(event.getLootTable().getKey().toString(), ThreadLocalRandom.current())) {
            ItemStack key = items.create(id, 1);
            if (key != null) {
                event.getLoot().add(key); // edit the list in place; never setLoot(getLoot()) (alpha.6 empty chests)
            }
        }
    }
}
