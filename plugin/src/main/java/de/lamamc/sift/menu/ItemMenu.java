package de.lamamc.sift.menu;

import de.lamamc.sift.core.Paging;
import de.lamamc.sift.item.ItemCategory;
import de.lamamc.sift.item.ItemDefinition;
import de.lamamc.sift.item.SiftItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The creative menu of /sift items: 45 item slots per page, bottom row = previous page, one tab per
 * category, next page. Holds its own state; {@link ItemMenuListener} handles the clicks.
 */
public final class ItemMenu implements InventoryHolder {

    public static final int ITEM_SLOTS = 45;
    public static final int PREVIOUS = 45;
    public static final int FIRST_TAB = 46;
    public static final int NEXT = 53;

    private final SiftItems items;
    private final ItemCategory category;
    private final int page;
    private final List<ItemDefinition> entries;
    private final Inventory inventory;

    private ItemMenu(SiftItems items, ItemCategory category, int page) {
        this.items = items;
        this.category = category;
        this.entries = items.registry().inCategory(category);
        this.page = Paging.clamp(page, entries.size(), ITEM_SLOTS);
        int pages = Paging.pageCount(entries.size(), ITEM_SLOTS);
        this.inventory = Bukkit.createInventory(this, 54,
                Component.text("Sift-Items: " + category.title() + " (" + (this.page + 1) + "/" + pages + ")"));
        fill(pages);
    }

    public static void open(Player player, SiftItems items, ItemCategory category, int page) {
        player.openInventory(new ItemMenu(items, category, page).getInventory());
    }

    /** Builds the menu without a viewer (for /sift debug menu). */
    public static ItemMenu build(SiftItems items, ItemCategory category, int page) {
        return new ItemMenu(items, category, page);
    }

    private void fill(int pages) {
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            int index = Paging.index(page, slot, entries.size(), ITEM_SLOTS);
            if (index >= 0) {
                inventory.setItem(slot, items.create(entries.get(index).id(), 1));
            }
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, named(Material.BARRIER, "Noch keine Items in dieser Kategorie", NamedTextColor.GRAY));
        }
        inventory.setItem(PREVIOUS, page > 0
                ? named(Material.ARROW, "Vorherige Seite", NamedTextColor.YELLOW)
                : named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        inventory.setItem(NEXT, page < pages - 1
                ? named(Material.ARROW, "Nächste Seite", NamedTextColor.YELLOW)
                : named(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        ItemCategory[] categories = ItemCategory.values();
        for (int i = 0; i < categories.length; i++) {
            inventory.setItem(FIRST_TAB + i, tab(categories[i]));
        }
    }

    private ItemStack tab(ItemCategory tab) {
        ItemStack icon = items.stackFor(tab.icon(), 1);
        if (icon == null) {
            icon = ItemStack.of(Material.CHEST);
        }
        boolean active = tab == category;
        icon.setData(DataComponentTypes.ITEM_NAME, Component.text(tab.title(),
                active ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, active);
        return icon;
    }

    private static ItemStack named(Material material, String name, NamedTextColor color) {
        ItemStack stack = ItemStack.of(material);
        stack.setData(DataComponentTypes.ITEM_NAME, Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        return stack;
    }

    /** The Sift item id in an item slot, or null. */
    public String itemAt(int slot) {
        int index = Paging.index(page, slot, entries.size(), ITEM_SLOTS);
        return index < 0 ? null : entries.get(index).id();
    }

    public ItemCategory category() {
        return category;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
