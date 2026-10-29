package de.lamamc.sift.packgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Imports the item-related data of the mod: recipes and item tags for the plugin (which registers the
 * recipes itself, because a datapack recipe cannot name items that only exist as components), and chest
 * loot tables, advancements and jukebox songs for the datapack.
 *
 * <p>Sift items do not exist as item types on a vanilla server. In loot tables and advancements a Sift
 * item {@code the_sift:x} becomes {@code minecraft:paper} with the model of the original and the Sift id
 * in its custom data (the format of Paper's persistent data container). The plugin replaces such loot
 * with the real item; advancement criteria match the id in the custom data. Blocks that only exist as a
 * vanilla substitute become that vanilla item.
 */
public final class ItemDataImporter {

    /** Copied unchanged for the plugin (relative to {@code pack-src/item-data/}). */
    static final List<String> PLUGIN_DIRS = List.of("recipe/", "tags/item/");
    /** Rewritten into the datapack. Recipe advancements are left out: the plugin unlocks its recipes. */
    static final List<String> DATAPACK_DIRS = List.of(
            "loot_table/chests/", "advancement/the_sift/", "advancement/story/", "jukebox_song/");

    static final String PLACEHOLDER_ITEM = "minecraft:paper";

    private static final String NS = BlockMapping.MOD_NAMESPACE + ":";

    private final BlockMapping mapping;
    private final Map<String, String> german;
    private final ImportReport report;

    /** @param german the German lang file, used as fallback for translated texts */
    public ItemDataImporter(BlockMapping mapping, Map<String, String> german, ImportReport report) {
        this.mapping = mapping;
        this.german = german;
        this.report = report;
    }

    /** Whether a mod data file is imported here (or deliberately left out here). */
    static boolean handles(String path) {
        return PLUGIN_DIRS.stream().anyMatch(path::startsWith) || DATAPACK_DIRS.stream().anyMatch(path::startsWith)
                || path.startsWith("advancement/recipes/");
    }

    /** Files for the plugin, keyed by path relative to {@code data/the_sift/}. */
    public Map<String, JsonElement> pluginFiles(Map<String, JsonElement> source) {
        Map<String, JsonElement> files = new TreeMap<>();
        source.forEach((path, json) -> {
            if (PLUGIN_DIRS.stream().anyMatch(path::startsWith)) {
                files.put(path, json.deepCopy());
            }
        });
        return files;
    }

    /** Files for the datapack, keyed by path relative to {@code data/the_sift/}. */
    public Map<String, JsonElement> datapackFiles(Map<String, JsonElement> source) {
        Map<String, JsonElement> files = new TreeMap<>();
        source.forEach((path, json) -> {
            if (path.startsWith("loot_table/chests/")) {
                files.put(path, rewriteLoot(json.deepCopy()));
            } else if (path.startsWith("advancement/the_sift/") || path.startsWith("advancement/story/")) {
                files.put(path, rewriteAdvancement(path, json.deepCopy().getAsJsonObject()));
            } else if (path.startsWith("jukebox_song/")) {
                files.put(path, rewriteSong(json.deepCopy().getAsJsonObject()));
            } else if (path.startsWith("advancement/recipes/")) {
                report.note("Recipe advancements left out (the plugin unlocks its recipes)", path);
            }
        });
        return files;
    }

    // ---- loot tables --------------------------------------------------------------------------------

    private JsonElement rewriteLoot(JsonElement json) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(this::rewriteLoot);
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if ("minecraft:item".equals(string(object, "type")) && isModId(string(object, "name"))) {
                String id = string(object, "name");
                String vanilla = substituteItem(id);
                if (vanilla != null) {
                    object.addProperty("name", vanilla);
                } else {
                    object.addProperty("name", PLACEHOLDER_ITEM);
                    // 26.x: "modifier" is a single loot function or a list of them
                    JsonArray modifiers = new JsonArray();
                    JsonElement existing = object.remove("modifier");
                    if (existing instanceof JsonArray list) {
                        list.forEach(modifiers::add);
                    } else if (existing != null) {
                        modifiers.add(existing);
                    }
                    JsonObject setComponents = new JsonObject();
                    setComponents.addProperty("type", "minecraft:set_components");
                    setComponents.add("components", siftComponents(id));
                    modifiers.add(setComponents);
                    object.add("modifier", modifiers);
                }
            }
            object.entrySet().forEach(e -> rewriteLoot(e.getValue()));
        }
        return json;
    }

    // ---- advancements -------------------------------------------------------------------------------

    private JsonObject rewriteAdvancement(String path, JsonObject advancement) {
        if (advancement.get("display") instanceof JsonObject display) {
            if (display.get("icon") instanceof JsonObject icon && isModId(string(icon, "id"))) {
                display.add("icon", iconFor(string(icon, "id")));
            }
            addFallback(display.get("title"));
            addFallback(display.get("description"));
        }
        if (advancement.get("criteria") instanceof JsonObject criteria) {
            criteria.entrySet().forEach(e -> rewriteItemPredicates(path, e.getValue()));
        }
        return advancement;
    }

    /** Item predicates {"items": "the_sift:x"} anywhere in a criterion. */
    private void rewriteItemPredicates(String path, JsonElement json) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(el -> rewriteItemPredicates(path, el));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.get("items") instanceof JsonPrimitive p && p.isString() && isModId(p.getAsString())) {
                String id = p.getAsString();
                String vanilla = substituteItem(id);
                if (vanilla != null) {
                    object.addProperty("items", vanilla);
                } else {
                    object.remove("items");
                    JsonObject predicates = new JsonObject();
                    predicates.addProperty("minecraft:custom_data", customDataSnbt(id));
                    object.add("predicates", predicates);
                }
            }
            object.entrySet().forEach(e -> rewriteItemPredicates(path, e.getValue()));
        }
    }

    private JsonObject iconFor(String id) {
        JsonObject icon = new JsonObject();
        String vanilla = substituteItem(id);
        if (vanilla != null) {
            icon.addProperty("id", vanilla);
            return icon;
        }
        icon.addProperty("id", PLACEHOLDER_ITEM);
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", id);
        icon.add("components", components);
        return icon;
    }

    // ---- jukebox songs ------------------------------------------------------------------------------

    /** The mod registers its sound events in code; a datapack refers to the sound file directly. */
    private JsonObject rewriteSong(JsonObject song) {
        if (song.get("sound_event") instanceof JsonPrimitive p && p.isString()) {
            JsonObject sound = new JsonObject();
            sound.addProperty("sound_id", p.getAsString());
            song.add("sound_event", sound);
        }
        addFallback(song.get("description"));
        return song;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** item_model and the Sift id; the plugin completes the item from its registry. */
    static JsonObject siftComponents(String id) {
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", id);
        JsonObject bukkit = new JsonObject();
        bukkit.addProperty(NS + "id", id.substring(NS.length()));
        JsonObject customData = new JsonObject();
        customData.add("PublicBukkitValues", bukkit);
        components.add("minecraft:custom_data", customData);
        return components;
    }

    /** custom_data predicate as SNBT: matches items whose persistent data has this Sift id. */
    static String customDataSnbt(String id) {
        return "{PublicBukkitValues:{\"" + NS + "id\":\"" + id.substring(NS.length()) + "\"}}";
    }

    /** The vanilla item of a block that only exists as substitute, else null. */
    private String substituteItem(String id) {
        BlockState state = mapping.substitutes().get(id);
        return state == null ? null : state.id();
    }

    private void addFallback(JsonElement text) {
        if (text instanceof JsonObject object && object.get("translate") instanceof JsonPrimitive key
                && !object.has("fallback")) {
            String german = this.german.get(key.getAsString());
            if (german == null) {
                report.note("No German text for", key.getAsString());
            } else {
                object.addProperty("fallback", german);
            }
        }
    }

    private static boolean isModId(String s) {
        return s != null && s.startsWith(NS);
    }

    private static String string(JsonObject object, String key) {
        return object.get(key) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }
}
