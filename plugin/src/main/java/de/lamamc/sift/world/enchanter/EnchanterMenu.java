package de.lamamc.sift.world.enchanter;

import de.lamamc.sift.core.enchanter.EnchantBoost;
import de.lamamc.sift.item.SiftItems;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The menu of a Sculk Enchanter (alpha.40): the item goes into the left slot, Charoit into the right one; the bottom
 * row offers one button per enchantment that can be raised (next level, cost in Charoit and levels). Items left in
 * the slots go back to the player when the menu closes.
 */
public final class EnchanterMenu implements InventoryHolder, Listener {

    static final int ITEM_SLOT = 11;
    static final int CHAROITE_SLOT = 15;
    static final int FIRST_OPTION = 18;

    private final SculkEnchanters enchanters;
    private final Inventory inventory;
    private List<EnchantBoost.Option> shown = new ArrayList<>();

    private final de.lamamc.sift.core.lang.Lang lang;

    private EnchanterMenu(SculkEnchanters enchanters, de.lamamc.sift.core.lang.Lang lang) {
        this.enchanters = enchanters;
        this.lang = lang;
        this.inventory = Bukkit.createInventory(this, 27, de.lamamc.sift.Messages.current().get(lang, "enchanter.menu-title"));
    }

    private static String text(de.lamamc.sift.core.lang.Lang lang, String key) {
        return de.lamamc.sift.Messages.current().raw(lang, key);
    }

    /** Listener instance for the menu events (registered once). */
    public static Listener listener() {
        return new EnchanterMenu(null, de.lamamc.sift.core.lang.Lang.EN);
    }

    static void open(SculkEnchanters enchanters, Player player) {
        EnchanterMenu menu = new EnchanterMenu(enchanters, de.lamamc.sift.Messages.current().lang(player));
        menu.refresh(player);
        player.openInventory(menu.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack pane(String name) {
        ItemStack pane = ItemStack.of(Material.CYAN_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        return pane;
    }

    private void refresh(Player player) {
        for (int slot = 0; slot < 27; slot++) {
            if (slot != ITEM_SLOT && slot != CHAROITE_SLOT) {
                inventory.setItem(slot, pane(slot >= FIRST_OPTION ? text(lang, "enchanter.no-boost") : " "));
            }
        }
        inventory.setItem(ITEM_SLOT - 1, pane(text(lang, "enchanter.item-slot")));
        inventory.setItem(CHAROITE_SLOT - 1, pane(text(lang, "enchanter.charoite-slot")));
        shown = SculkEnchanters.options(enchanters.rules(), inventory.getItem(ITEM_SLOT));
        int charoite = charoite();
        for (int i = 0; i < shown.size() && i < 9; i++) {
            EnchantBoost.Option option = shown.get(i);
            Enchantment enchantment = SculkEnchanters.enchantment(option.enchantment());
            boolean affordable = EnchantBoost.affordable(option.cost(), player.getLevel(), charoite, player.getGameMode() == GameMode.CREATIVE);
            ItemStack button = ItemStack.of(affordable ? Material.ENCHANTED_BOOK : Material.BOOK);
            Component name = enchantment == null ? Component.text(option.enchantment()) : enchantment.displayName(option.to());
            button.editMeta(meta -> {
                meta.displayName(name.color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        de.lamamc.sift.Messages.current().get(lang, "enchanter.cost", "charoite", String.valueOf(option.cost().charoite()),
                                "levels", String.valueOf(option.cost().levels()))
                                .color(affordable ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        Component.text(text(lang, affordable ? "enchanter.click" : "enchanter.cannot-afford"), NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
            });
            inventory.setItem(FIRST_OPTION + i, button);
        }
    }

    private int charoite() {
        ItemStack stack = inventory.getItem(CHAROITE_SLOT);
        return stack != null && "charoite".equals(SiftItems.idOf(stack)) ? stack.getAmount() : 0;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EnchanterMenu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < 27;
        if (top && raw != ITEM_SLOT && raw != CHAROITE_SLOT) {
            event.setCancelled(true);
            if (raw >= FIRST_OPTION && raw - FIRST_OPTION < menu.shown.size()) {
                menu.upgrade(player, menu.shown.get(raw - FIRST_OPTION));
            }
            return;
        }
        Bukkit.getScheduler().runTask(menu.enchanters.plugin(), () -> menu.refresh(player));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EnchanterMenu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(s -> s < 27 && s != ITEM_SLOT && s != CHAROITE_SLOT)) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(menu.enchanters.plugin(), () -> menu.refresh(player));
    }

    private void upgrade(Player player, EnchantBoost.Option option) {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        // check again against the item as it is now
        boolean still = SculkEnchanters.options(enchanters.rules(), item).contains(option);
        Enchantment enchantment = SculkEnchanters.enchantment(option.enchantment());
        if (!still || enchantment == null || !EnchantBoost.affordable(option.cost(), player.getLevel(), charoite(), creative)) {
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            refresh(player);
            return;
        }
        if (!creative) {
            player.giveExpLevels(-option.cost().levels());
            ItemStack charoite = inventory.getItem(CHAROITE_SLOT);
            charoite.setAmount(charoite.getAmount() - option.cost().charoite());
            inventory.setItem(CHAROITE_SLOT, charoite.getAmount() <= 0 ? null : charoite);
        }
        item.addUnsafeEnchantment(enchantment, option.to());
        inventory.setItem(ITEM_SLOT, item);
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.7f);
        player.playSound(player, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.6f, 1.2f);
        refresh(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof EnchanterMenu menu) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        for (int slot : new int[] {ITEM_SLOT, CHAROITE_SLOT}) {
            ItemStack stack = menu.inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                menu.inventory.setItem(slot, null);
                player.getInventory().addItem(stack).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            }
        }
    }
}
