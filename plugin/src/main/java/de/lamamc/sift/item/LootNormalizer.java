package de.lamamc.sift.item;

import java.util.ListIterator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Loot tables of the datapack can only name Sift items as paper with the Sift id in its custom data; this
 * replaces them with the real item from the registry when the loot is generated (chests, entities).
 */
public final class LootNormalizer implements Listener {

    private final SiftItems items;

    public LootNormalizer(SiftItems items) {
        this.items = items;
    }

    /**
     * Replaces Sift items in place. getLoot() is the event's own mutable list; never pass it back to
     * setLoot(), which clears that list first and so empties every chest.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        ListIterator<ItemStack> it = event.getLoot().listIterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            ItemStack real = normalize(stack);
            if (real != stack) {
                it.set(real);
            }
        }
    }

    /** The registry item for a stack with a Sift id, keeping the amount; other stacks (and null = air) unchanged. */
    public ItemStack normalize(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        String id = SiftItems.idOf(stack);
        if (id == null) {
            return stack;
        }
        ItemStack real = items.create(id, stack.getAmount());
        return real == null ? stack : real;
    }
}
