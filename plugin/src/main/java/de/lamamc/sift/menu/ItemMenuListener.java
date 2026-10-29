package de.lamamc.sift.menu;

import de.lamamc.sift.item.ItemCategory;
import de.lamamc.sift.item.SiftItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/** Clicks in the item menu: items are copied out, nothing can be put in or taken. */
public final class ItemMenuListener implements Listener {

    private final SiftItems items;
    private final String permission;

    public ItemMenuListener(SiftItems items, String permission) {
        this.items = items;
        this.permission = permission;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof ItemMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != menu.getInventory()) {
            return;
        }
        if (!player.hasPermission(permission)) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot < ItemMenu.ITEM_SLOTS) {
            String id = menu.itemAt(slot);
            if (id == null) {
                return;
            }
            ItemStack stack = items.create(id, 1);
            if (event.isShiftClick()) {
                stack.setAmount(stack.getMaxStackSize());
            }
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        } else if (slot == ItemMenu.PREVIOUS) {
            ItemMenu.open(player, items, menu.category(), menu.page() - 1);
        } else if (slot == ItemMenu.NEXT) {
            ItemMenu.open(player, items, menu.category(), menu.page() + 1);
        } else {
            int tab = slot - ItemMenu.FIRST_TAB;
            if (tab >= 0 && tab < ItemCategory.values().length) {
                ItemMenu.open(player, items, ItemCategory.values()[tab], 0);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ItemMenu) {
            event.setCancelled(true);
        }
    }
}
