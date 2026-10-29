package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ItemDataImporterTest {

    private final ItemDataImporter importer = new ItemDataImporter(BlockMapping.loadDefault(),
            Map.of("advancement.the_sift.x.title", "Titel"), new ImportReport());

    private static Map<String, JsonElement> source(Map<String, String> files) {
        Map<String, JsonElement> source = new TreeMap<>();
        files.forEach((path, json) -> source.put(path, JsonParser.parseString(json)));
        return source;
    }

    @Test
    void lootEntriesOfSiftItemsBecomePaperWithModelAndId() {
        JsonObject entry = importer.datapackFiles(source(Map.of("loot_table/chests/c.json", """
                {"pools": [{"entries": [{"type": "minecraft:item", "name": "the_sift:siftslate",
                  "modifier": [{"type": "minecraft:set_count", "count": 3}]}]}]}""")))
                .get("loot_table/chests/c.json").getAsJsonObject().getAsJsonArray("pools").get(0)
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();

        assertEquals("minecraft:paper", entry.get("name").getAsString());
        JsonArray modifiers = entry.getAsJsonArray("modifier");
        assertEquals("minecraft:set_count", modifiers.get(0).getAsJsonObject().get("type").getAsString());
        JsonObject set = modifiers.get(1).getAsJsonObject();
        assertEquals("minecraft:set_components", set.get("type").getAsString());
        assertEquals("the_sift:siftslate", set.getAsJsonObject("components").get("minecraft:item_model").getAsString());
        assertEquals("siftslate", set.getAsJsonObject("components").getAsJsonObject("minecraft:custom_data")
                .getAsJsonObject("PublicBukkitValues").get("the_sift:id").getAsString());
    }

    @Test
    void substituteBlocksBecomeTheirVanillaItem() {
        JsonObject entry = importer.datapackFiles(source(Map.of("loot_table/chests/c.json", """
                {"pools": [{"entries": [{"type": "minecraft:item", "name": "the_sift:siftslate_brick_stairs"}]}]}""")))
                .get("loot_table/chests/c.json").getAsJsonObject().getAsJsonArray("pools").get(0)
                .getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:deepslate_brick_stairs", entry.get("name").getAsString());
        assertFalse(entry.has("modifier"));
    }

    @Test
    void advancementsMatchTheSiftIdAndGetGermanFallbacks() {
        JsonObject adv = importer.datapackFiles(source(Map.of("advancement/the_sift/x.json", """
                {"display": {"icon": {"id": "the_sift:siftite_ingot"},
                  "title": {"translate": "advancement.the_sift.x.title"}},
                 "criteria": {"c": {"trigger": "minecraft:inventory_changed",
                   "conditions": {"items": [{"items": "the_sift:siftite_ingot"}]}}}}""")))
                .get("advancement/the_sift/x.json").getAsJsonObject();

        JsonObject icon = adv.getAsJsonObject("display").getAsJsonObject("icon");
        assertEquals("minecraft:paper", icon.get("id").getAsString());
        assertEquals("the_sift:siftite_ingot", icon.getAsJsonObject("components").get("minecraft:item_model").getAsString());
        assertEquals("Titel", adv.getAsJsonObject("display").getAsJsonObject("title").get("fallback").getAsString());
        JsonObject predicate = adv.getAsJsonObject("criteria").getAsJsonObject("c").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject();
        assertFalse(predicate.has("items"));
        assertEquals("{PublicBukkitValues:{\"the_sift:id\":\"siftite_ingot\"}}",
                predicate.getAsJsonObject("predicates").get("minecraft:custom_data").getAsString());
    }

    @Test
    void songsReferToTheSoundFile() {
        JsonObject song = importer.datapackFiles(source(Map.of("jukebox_song/rift.json",
                "{\"sound_event\": \"the_sift:music_disc.rift\", \"description\": {\"translate\": \"k\"}}")))
                .get("jukebox_song/rift.json").getAsJsonObject();
        assertEquals("the_sift:music_disc.rift", song.getAsJsonObject("sound_event").get("sound_id").getAsString());
    }

    @Test
    void recipesAndItemTagsGoToThePluginNotTheDatapack() {
        Map<String, JsonElement> source = source(Map.of(
                "recipe/r.json", "{\"type\": \"minecraft:crafting_shapeless\"}",
                "tags/item/t.json", "{\"values\": []}",
                "advancement/recipes/misc/r.json", "{}"));
        assertEquals(List.of("recipe/r.json", "tags/item/t.json"), List.copyOf(importer.pluginFiles(source).keySet()));
        assertTrue(importer.datapackFiles(source).isEmpty());
    }

    /** The committed datapack: advancement parents exist and no Sift item id is used as item type. */
    @Test
    void generatedAdvancementsAndLootAreConsistent() throws IOException {
        Path root = Path.of(System.getProperty("sift.packSrc", "../pack-src"), "datapack", "data", "the_sift");
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("advancement"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject adv = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (adv.has("parent") && adv.get("parent").getAsString().startsWith("the_sift:")) {
                    String parent = adv.get("parent").getAsString().substring("the_sift:".length());
                    if (!Files.isRegularFile(root.resolve("advancement/" + parent + ".json"))) {
                        problems.add(file.getFileName() + ": missing parent " + parent);
                    }
                }
                String icon = adv.getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString();
                if (icon.startsWith("the_sift:")) {
                    problems.add(file.getFileName() + ": icon " + icon);
                }
            }
        }
        String loot = Files.readString(root.resolve("loot_table/chests/abandoned_portal.json"));
        if (loot.contains("\"name\": \"the_sift:")) {
            problems.add("abandoned_portal: Sift item as item type");
        }
        assertTrue(problems.isEmpty(), problems.toString());
    }
}
