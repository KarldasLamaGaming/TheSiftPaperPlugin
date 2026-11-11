package de.lamamc.sift.world.temple;

import de.lamamc.sift.core.temple.TempleHints;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Rumour notes about the temples (phase 4, alpha.41b, phase4.temples.hints). A note is the Sift item temple_rumor;
 * it comes into chests by chance per loot table (config) or from any loot table that names it (datapack, custom_data
 * id). When the loot is generated, the note gets the direction and the rounded distance from the chest to the
 * nearest temple whose guardian still sleeps (not the temple the chest stands in). Outside the Sift, or when the
 * temples are off, notes are taken out of the loot.
 */
public final class TempleRumors implements Listener {

    public static final String ID = "temple_rumor";

    /** @param chances loot table key -> chance of one extra note; text lines with {@code <direction>} and {@code <distance>} */
    public record Settings(Map<String, Double> chances, int distanceStep, List<String> text, List<String> allDefeated) {
    }

    private final Supplier<TempleRegistry> temples;
    private final SiftItems items;
    private final Settings settings;

    public TempleRumors(Supplier<TempleRegistry> temples, SiftItems items, Settings settings) {
        this.temples = temples;
        this.items = items;
        this.settings = settings;
    }

    // after the LootNormalizer (LOWEST) turned datapack paper into the real item
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        Location where = event.getLootContext().getLocation();
        TempleRegistry registry = temples.get();
        boolean usable = registry != null && where.getWorld() != null && SiftWorlds.isSift(where.getWorld());
        Double chance = settings.chances().get(event.getLootTable().getKey().toString());
        if (usable && chance != null && ThreadLocalRandom.current().nextDouble() < chance) {
            ItemStack note = items.create(ID, 1);
            if (note != null) {
                event.getLoot().add(note); // in place; never setLoot(getLoot())
            }
        }
        ListIterator<ItemStack> it = event.getLoot().listIterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (stack == null || !ID.equals(SiftItems.idOf(stack))) {
                continue;
            }
            if (!usable) {
                it.remove();
                continue;
            }
            write(stack, registry, where);
        }
    }

    /** Puts the way to the nearest sleeping temple into the note's lore. */
    public void write(ItemStack note, TempleRegistry registry, Location from) {
        String here = registry.at(from.getBlock()).map(TempleRegistry.Temple::id).orElse(null);
        TempleSites.Site site = registry.nearestOpenSite(from.getX(), from.getZ(), here);
        List<String> lines = site == null ? settings.allDefeated() : settings.text();
        String direction = site == null ? "" : TempleHints.direction(from.getX(), from.getZ(), site.x(), site.z());
        String distance = site == null ? "" : String.valueOf(TempleHints.roundedDistance(from.getX(), from.getZ(), site.x(),
                site.z(), settings.distanceStep()));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(MiniMessage.miniMessage().deserialize(line, Placeholder.unparsed("direction", direction),
                    Placeholder.unparsed("distance", distance)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        note.lore(lore);
    }
}
