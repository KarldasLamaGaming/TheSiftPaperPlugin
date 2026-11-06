package de.lamamc.sift.world.admin;

import de.lamamc.sift.core.admin.ResetRules;
import de.lamamc.sift.item.SiftItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Go-live player reset (/sift admin reset-players, config admin.reset-players.enabled, permission
 * thesift.admin.reset): players start the Sift from zero. For each player it removes all of the plugin's data in
 * the player (intro flag, siftite backup, return points …: every {@code the_sift:} key), revokes all
 * {@code the_sift:} advancements and takes every Sift item out of the inventory, the ender chest and shulker boxes
 * in them. Online players are reset at once, offline players at their next login (reset number in the main
 * world's persistent data against the one in the player).
 */
public final class PlayerReset implements Listener {

    public static final NamespacedKey SERVER_RESET = new NamespacedKey("the_sift", "player_reset");
    public static final NamespacedKey PLAYER_RESET = new NamespacedKey("the_sift", "player_reset_done");
    private static final long CONFIRM_WINDOW = 30_000;

    /** What one reset removed. */
    public record Result(int keys, int advancements, int items) {
    }

    private final boolean enabled;
    private final Logger logger;
    private long requestedAt;

    public PlayerReset(boolean enabled, Logger logger) {
        this.enabled = enabled;
        this.logger = logger;
    }

    public boolean enabled() {
        return enabled;
    }

    public void listen(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** First step: remembers the request; the confirmation must follow within 30 s. */
    public void request() {
        requestedAt = System.currentTimeMillis();
    }

    /** Second step: true if a request is pending and recent; then raises the server reset number. */
    public boolean confirm() {
        if (!ResetRules.confirmable(requestedAt, System.currentTimeMillis(), CONFIRM_WINDOW)) {
            return false;
        }
        requestedAt = 0;
        World main = Bukkit.getWorlds().get(0);
        main.getPersistentDataContainer().set(SERVER_RESET, PersistentDataType.LONG, serverReset() + 1);
        return true;
    }

    public long serverReset() {
        return Bukkit.getWorlds().get(0).getPersistentDataContainer().getOrDefault(SERVER_RESET, PersistentDataType.LONG, 0L);
    }

    /** Players who are offline and were not reset yet are only counted when they join; this counts the online ones still due. */
    public int onlineDue() {
        long server = serverReset();
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> ResetRules.due(p.getPersistentDataContainer().getOrDefault(PLAYER_RESET, PersistentDataType.LONG, 0L), server))
                .count();
    }

    /** Resets every online player that is due; returns how many. */
    public int resetOnline() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (resetIfDue(player)) {
                count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (enabled) {
            resetIfDue(event.getPlayer());
        }
    }

    private boolean resetIfDue(Player player) {
        long server = serverReset();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!ResetRules.due(pdc.getOrDefault(PLAYER_RESET, PersistentDataType.LONG, 0L), server)) {
            return false;
        }
        Result r = reset(player);
        pdc.set(PLAYER_RESET, PersistentDataType.LONG, server);
        logger.info("Sift player reset " + server + " for " + player.getName() + ": " + r.keys() + " data entries, "
                + r.advancements() + " advancements, " + r.items() + " Sift items removed");
        return true;
    }

    /** The reset of one player (also used without the reset number, e.g. for tests). */
    public static Result reset(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        int keys = 0;
        for (NamespacedKey key : new ArrayList<>(pdc.getKeys())) {
            if (key.getNamespace().equals("the_sift") && !key.equals(PLAYER_RESET)) {
                pdc.remove(key);
                keys++;
            }
        }
        int advancements = 0;
        for (Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement advancement = it.next();
            if (!advancement.getKey().getNamespace().equals("the_sift")) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            List<String> awarded = new ArrayList<>(progress.getAwardedCriteria());
            if (!awarded.isEmpty()) {
                awarded.forEach(progress::revokeCriteria);
                advancements++;
            }
        }
        int items = strip(player.getInventory()) + strip(player.getEnderChest());
        ItemStack cursor = player.getItemOnCursor();
        if (SiftItems.idOf(cursor) != null) {
            items += cursor.getAmount();
            player.setItemOnCursor(null);
        }
        return new Result(keys, advancements, items);
    }

    /** Removes Sift items from an inventory and from shulker boxes in it; returns the number of items removed. */
    static int strip(Inventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (SiftItems.idOf(stack) != null) {
                removed += stack.getAmount();
                inventory.setItem(i, null);
            } else if (stack.getItemMeta() instanceof BlockStateMeta meta && meta.getBlockState() instanceof ShulkerBox box) {
                int inBox = strip(box.getInventory());
                if (inBox > 0) {
                    meta.setBlockState(box);
                    stack.setItemMeta(meta);
                    inventory.setItem(i, stack);
                    removed += inBox;
                }
            }
        }
        return removed;
    }
}
