package de.lamamc.sift.world.portal;

import de.lamamc.sift.Messages;
import de.lamamc.sift.core.portal.ConsoleSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * The tune of the Ancient City portals is not told when the consoles wake; it is written in a book that lies
 * in the city's chests with a chance (portals.ancient-city.book).
 */
public final class AncientCityBook implements Listener {

    private final Set<String> tables;
    private final double chance;
    private final int[] melody;
    private final Messages messages;

    public AncientCityBook(List<String> tables, double chance, int[] melody, Messages messages) {
        this.tables = Set.copyOf(tables);
        this.chance = chance;
        this.melody = melody.clone();
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (tables.contains(event.getLootTable().getKey().toString()) && ThreadLocalRandom.current().nextDouble() < chance) {
            event.getLoot().add(book()); // edit the list in place (see LootNormalizer)
        }
    }

    public ItemStack book() {
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(messages.get("portals.ancient-city-book.title"));
        meta.author(messages.get("portals.ancient-city-book.author"));
        List<Component> pages = new ArrayList<>();
        pages.add(messages.get("portals.ancient-city-book.page-1"));
        pages.add(messages.get("portals.ancient-city-book.page-2", "melody", ConsoleSounds.text(melody)));
        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }
}
