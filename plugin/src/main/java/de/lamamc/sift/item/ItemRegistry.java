package de.lamamc.sift.item;

import de.lamamc.sift.block.MappingEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * All Sift items: one per custom block (from block-mapping.json) plus the items of items.yml. The item
 * menu, /sift give, drops, and later recipes and loot all create their items from here, so a new
 * entry shows up everywhere.
 */
public final class ItemRegistry {

    private final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    private final Map<String, String> substitutes = new TreeMap<>();

    public ItemRegistry(List<MappingEntry> blocks, Reader itemsYaml) {
        this(blocks, Map.of(), itemsYaml);
    }

    /** @param blockSubstitutes blocks that only exist as vanilla blocks (id -> vanilla block id) */
    public ItemRegistry(List<MappingEntry> blocks, Map<String, String> blockSubstitutes, Reader itemsYaml) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(itemsYaml);
        substitutes.putAll(blockSubstitutes);
        ConfigurationSection itemSubstitutes = yaml.getConfigurationSection("item-substitutes");
        if (itemSubstitutes != null) {
            itemSubstitutes.getKeys(false).forEach(id -> substitutes.put(id, itemSubstitutes.getString(id)));
        }
        List<String> withoutItem = yaml.getStringList("blocks-without-item");
        for (MappingEntry entry : blocks) {
            if (items.containsKey(entry.id()) || withoutItem.contains(entry.id())) {
                continue; // further states of the same block, or a block without item
            }
            String standIn = entry.vanillaState().replaceFirst("\\[.*", "");
            items.put(entry.id(), new ItemDefinition(entry.id(), ItemCategory.forBlock(entry.id(), standIn),
                    blockItemBase(standIn)));
        }
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (items.containsKey(id)) {
                    throw new IllegalArgumentException("items.yml: " + id + " is already a block");
                }
                items.put(id, new ItemDefinition(id, ItemCategory.parse(section.getString(id + ".category", "misc")),
                        section.getString(id + ".base", "minecraft:paper"),
                        ItemKind.parse(section.getString(id + ".kind", "plain")),
                        section.getBoolean(id + ".protect", false)));
            }
        }
    }

    public static ItemRegistry loadFromJar(List<MappingEntry> blocks) {
        try (InputStream in = ItemRegistry.class.getResourceAsStream("/items.yml")) {
            if (in == null) {
                throw new IllegalStateException("items.yml missing from the plugin jar");
            }
            return new ItemRegistry(blocks, MappingEntry.bundledSubstitutes(), new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The item that places a stand-in block: string for tripwire, glass for chorus plants. */
    static String blockItemBase(String standInBlock) {
        return switch (standInBlock) {
            case "minecraft:tripwire" -> "minecraft:string";
            case "minecraft:chorus_plant" -> "minecraft:glass";
            default -> standInBlock;
        };
    }

    public ItemDefinition get(String id) {
        return items.get(id.startsWith("the_sift:") ? id.substring("the_sift:".length()) : id);
    }

    /** The vanilla item of a Sift block or item of the original that only exists as vanilla item, or null. */
    public String substitute(String id) {
        return substitutes.get(id.startsWith("the_sift:") ? id.substring("the_sift:".length()) : id);
    }

    /** Ids of the items that must not be used like their vanilla base item. */
    public Set<String> protectedIds() {
        Set<String> ids = new TreeSet<>();
        items.values().forEach(def -> {
            if (def.protectedItem()) {
                ids.add(def.id());
            }
        });
        return ids;
    }

    public Collection<ItemDefinition> all() {
        return items.values();
    }

    public List<ItemDefinition> inCategory(ItemCategory category) {
        List<ItemDefinition> result = new ArrayList<>();
        items.values().forEach(def -> {
            if (def.category() == category) {
                result.add(def);
            }
        });
        return result;
    }
}
