package de.lamamc.sift.item;

import de.lamamc.sift.block.BlockSettings;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Sift items that burn in furnaces like in the original (willow logs and planks 300 ticks, sapling 100;
 * there via the vanilla tags #logs_that_burn, #planks, #saplings). The server does not count their base
 * items as fuel, so the fuel slot does not take them by itself: clicks put them there, and the burn time
 * comes from config.yml (blocks.<id>.fuel). Hoppers cannot fill them in.
 */
public final class SiftFuel implements Listener {

    private static final int FUEL_SLOT = 1;

    private final BlockSettings settings;

    public SiftFuel(BlockSettings settings) {
        this.settings = settings;
    }

    private int burnTime(ItemStack stack) {
        String id = SiftItems.idOf(stack);
        return id == null ? 0 : settings.fuel(id);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        int ticks = burnTime(event.getFuel());
        if (ticks > 0) {
            event.setBurnTime(ticks);
            event.setBurning(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof FurnaceInventory furnace)) {
            return;
        }
        boolean fuelSlot = event.getClickedInventory() == furnace && event.getSlot() == FUEL_SLOT;
        if (fuelSlot && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
            ItemStack cursor = event.getCursor();
            if (burnTime(cursor) > 0) {
                event.setCancelled(true);
                placeFromCursor(event, furnace, cursor, event.getClick() == ClickType.RIGHT);
            }
        } else if (event.getClickedInventory() != furnace && event.isShiftClick() && burnTime(event.getCurrentItem()) > 0) {
            event.setCancelled(true);
            ItemStack moving = event.getCurrentItem();
            ItemStack rest = merge(furnace, moving, moving.getAmount());
            event.setCurrentItem(rest);
        }
    }

    private static void placeFromCursor(InventoryClickEvent event, FurnaceInventory furnace, ItemStack cursor, boolean one) {
        ItemStack fuel = furnace.getFuel();
        if (fuel != null && !fuel.isEmpty() && !fuel.isSimilar(cursor)) {
            // swap like vanilla does with a different item on the cursor
            furnace.setFuel(cursor.clone());
            event.getView().setCursor(fuel);
            return;
        }
        event.getView().setCursor(merge(furnace, cursor, one ? 1 : cursor.getAmount()));
    }

    /** Puts up to {@code amount} of the stack into the fuel slot; returns what is left of the stack, or null. */
    private static ItemStack merge(FurnaceInventory furnace, ItemStack stack, int amount) {
        ItemStack fuel = furnace.getFuel();
        int max = stack.getMaxStackSize();
        int inSlot = fuel == null || fuel.isEmpty() ? 0 : fuel.getAmount();
        if (inSlot > 0 && !fuel.isSimilar(stack)) {
            return stack;
        }
        int moved = Math.min(amount, max - inSlot);
        if (moved <= 0) {
            return stack;
        }
        ItemStack newFuel = stack.clone();
        newFuel.setAmount(inSlot + moved);
        furnace.setFuel(newFuel);
        int left = stack.getAmount() - moved;
        if (left <= 0) {
            return null;
        }
        ItemStack rest = stack.clone();
        rest.setAmount(left);
        return rest;
    }
}
