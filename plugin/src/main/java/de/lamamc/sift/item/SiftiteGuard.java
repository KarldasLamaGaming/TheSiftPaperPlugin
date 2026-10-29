package de.lamamc.sift.item;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import de.lamamc.sift.core.SiftiteGuardRules;
import de.lamamc.sift.core.SiftiteGuardRules.Input;
import io.papermc.paper.event.player.PlayerTradeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.SmithingTrimRecipe;

/**
 * Keeps protected Sift items (Siftite, items.yml {@code protect: true}) from being used like the vanilla
 * item they are built on. Only stations with a protected item in them are touched; see
 * {@link SiftiteGuardRules} for the rules.
 */
public final class SiftiteGuard implements Listener {

    private final SiftItems items;
    private final SiftiteGuardRules rules = new SiftiteGuardRules(ItemComponents.REPAIR_ITEM);

    public SiftiteGuard(SiftItems items) {
        this.items = items;
    }

    private Input input(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Input.EMPTY;
        }
        String id = SiftItems.idOf(stack);
        if (id == null) {
            return stack.getType() == Material.ENCHANTED_BOOK ? Input.book() : Input.vanilla();
        }
        return Input.sift(id, items.isProtected(stack));
    }

    private List<Input> inputs(ItemStack... stacks) {
        List<Input> inputs = new ArrayList<>();
        Arrays.stream(stacks).forEach(stack -> inputs.add(input(stack)));
        return inputs;
    }

    private static boolean isSiftRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(SiftRecipes.NAMESPACE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe != null && !rules.recipeAllowed(isSiftRecipe(recipe), inputs(event.getInventory().getMatrix()))) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (isSiftRecipe(event.getRecipe())) {
            return;
        }
        if (event.getBlock().getState(false) instanceof Container crafter
                && !rules.recipeAllowed(false, inputs(crafter.getInventory().getContents()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnace(FurnaceStartSmeltEvent event) {
        if (!rules.recipeAllowed(isSiftRecipe(event.getRecipe()), inputs(event.getSource()))) {
            event.setTotalCookTime(Integer.MAX_VALUE); // never finishes; the item stays in the furnace
        }
    }

    /** Anvil, grindstone, smithing table, stonecutter, loom and cartography table. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareResult(PrepareResultEvent event) {
        Inventory inventory = event.getInventory();
        boolean allowed;
        if (event instanceof PrepareAnvilEvent anvil) {
            allowed = rules.anvilAllowed(input(anvil.getInventory().getFirstItem()), input(anvil.getInventory().getSecondItem()));
        } else if (event instanceof PrepareGrindstoneEvent grindstone) {
            allowed = rules.grindstoneAllowed(input(grindstone.getInventory().getUpperItem()),
                    input(grindstone.getInventory().getLowerItem()));
        } else if (event instanceof PrepareSmithingEvent smithing) {
            SmithingInventory table = smithing.getInventory();
            Recipe recipe = table.getRecipe();
            allowed = rules.smithingAllowed(recipe != null && isSiftRecipe(recipe), recipe instanceof SmithingTrimRecipe,
                    input(table.getInputTemplate()), input(table.getInputEquipment()), input(table.getInputMineral()));
        } else {
            allowed = rules.otherStationAllowed(inputs(inventory.getContents()).toArray(Input[]::new));
        }
        if (!allowed) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory() instanceof MerchantInventory merchant
                && !rules.otherStationAllowed(input(merchant.getItem(0)), input(merchant.getItem(1)))) {
            event.setCancelled(true);
        }
    }
}
