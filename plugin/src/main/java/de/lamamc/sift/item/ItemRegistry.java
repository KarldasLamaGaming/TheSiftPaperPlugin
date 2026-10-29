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

    public ItemRegistry(List<MappingEntry> blocks, Reader itemsYaml) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(itemsYaml);
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
            for (String id : yaml.getStringList("protected-blocks")) {
                ItemDefinition block = items.get(id);
                if (block == null) {
                    throw new IllegalArgumentException("items.yml: protected block " + id + " is unknown");
                }
                items.put(id, new ItemDefinition(id, block.category(), block.baseItem(), block.kind(), true));
            }
        }
    }

    public static ItemRegistry loadFromJar(List<MappingEntry> blocks) {
        try (InputStream in = ItemRegistry.class.getResourceAsStream("/items.yml")) {
            if (in == null) {
                throw new IllegalStateException("items.yml missing from the plugin jar");
            }
            return new ItemRegistry(blocks, new InputStreamReader(in, StandardCharsets.UTF_8));
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
