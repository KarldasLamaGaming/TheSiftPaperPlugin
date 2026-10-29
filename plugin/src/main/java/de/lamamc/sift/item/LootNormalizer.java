package de.lamamc.sift.item;

import java.util.List;
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        List<ItemStack> loot = event.getLoot();
        ListIterator<ItemStack> it = loot.listIterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            ItemStack real = normalize(stack);
            if (real != stack) {
                it.set(real);
            }
        }
        event.setLoot(loot);
    }

    /** The registry item for a stack with a Sift id, keeping the amount; other stacks unchanged. */
    public ItemStack normalize(ItemStack stack) {
        String id = SiftItems.idOf(stack);
        if (id == null) {
            return stack;
        }
        ItemStack real = items.create(id, stack.getAmount());
        return real == null ? stack : real;
    }
}
