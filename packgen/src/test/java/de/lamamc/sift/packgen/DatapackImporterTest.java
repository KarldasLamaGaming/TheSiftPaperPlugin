package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DatapackImporterTest {

    private final BlockMapping mapping = BlockMapping.loadDefault();
    private final ImportReport report = new ImportReport();

    private Map<String, JsonElement> run(Map<String, String> files) {
        Map<String, JsonElement> source = new TreeMap<>();
        files.forEach((path, json) -> source.put(path, JsonParser.parseString(json)));
        return new DatapackImporter(mapping, report).run(source);
    }

    @Test
    void javaFeaturesAndEverythingUsingThemAreDropped() {
        Map<String, JsonElement> out = run(Map.of(
                "worldgen/feature/monolith.json", "{\"type\": \"the_sift:lush_monolith\", \"config\": {}}",
                "worldgen/placed_feature/monolith.json", "{\"feature\": \"the_sift:monolith\", \"placement\": []}",
                "worldgen/feature/selector.json", """
                        {"type": "minecraft:simple_random_selector",
                         "features": ["the_sift:monolith"]}""",
                "worldgen/placed_feature/selector.json", "{\"feature\": \"the_sift:selector\", \"placement\": []}",
                "worldgen/feature/rock.json", """
                        {"type": "minecraft:simple_block",
                         "to_place": {"type": "minecraft:simple", "state": {"id": "the_sift:siftslate"}}}""",
                "worldgen/placed_feature/rock.json", "{\"feature\": \"the_sift:rock\", \"placement\": []}",
                "worldgen/biome/b.json", """
                        {"features": [["the_sift:monolith", "the_sift:selector", "the_sift:rock"]]}"""));

        assertFalse(out.containsKey("worldgen/feature/monolith.json"));
        assertFalse(out.containsKey("worldgen/placed_feature/monolith.json"));
        assertFalse(out.containsKey("worldgen/feature/selector.json"));
        assertFalse(out.containsKey("worldgen/placed_feature/selector.json"));
        assertTrue(out.containsKey("worldgen/placed_feature/rock.json"));
        assertEquals("[[\"the_sift:rock\"]]",
                out.get("worldgen/biome/b.json").getAsJsonObject().get("features").toString());
        assertTrue(report.errors().isEmpty(), report.errors().toString());
    }

    @Test
    void blockStatesAreRewritten() {
        Map<String, JsonElement> out = run(Map.of(
                "worldgen/feature/rock.json", """
                        {"type": "minecraft:simple_block",
                         "to_place": {"type": "minecraft:simple",
                                      "state": {"id": "the_sift:siftslate_growth", "properties": {"snowy": "true"}}}}"""));
        JsonObject state = out.get("worldgen/feature/rock.json").getAsJsonObject()
                .getAsJsonObject("to_place").getAsJsonObject("state");
        assertEquals("minecraft:mushroom_stem", state.get("id").getAsString());
        assertEquals(mapping.resolve(BlockState.of("the_sift:siftslate_growth", Map.of("snowy", "true"))).properties(),
                new TreeMap<>(state.getAsJsonObject("properties").asMap().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAsString()))));
    }

    @Test
    void blockIdStringsInStateFieldsAreRewrittenEvenIfAFeatureHasTheSameName() {
        Map<String, JsonElement> out = run(Map.of(
                "worldgen/placed_feature/siftslate_coal_ore.json", "{\"feature\": \"minecraft:ore_coal\", \"placement\": []}",
                "worldgen/processor_list/fossil.json", """
                        {"processors": [{"processor_type": "minecraft:rule", "rules": [{
                          "input_predicate": {"predicate_type": "minecraft:block_match", "block": "minecraft:coal_ore"},
                          "location_predicate": {"predicate_type": "minecraft:always_true"},
                          "output_state": "the_sift:siftslate_coal_ore"}]}]}"""));
        JsonObject output = out.get("worldgen/processor_list/fossil.json").getAsJsonObject()
                .getAsJsonArray("processors").get(0).getAsJsonObject().getAsJsonArray("rules").get(0)
                .getAsJsonObject().getAsJsonObject("output_state");
        assertEquals("minecraft:mushroom_stem", output.get("id").getAsString());
        assertTrue(report.errors().isEmpty(), report.errors().toString());
    }

    @Test
    void oreTargetsOnModTagsBecomeExactStates() {
        Map<String, JsonElement> out = run(Map.of(
                "tags/block/replaceables.json", "{\"values\": [\"the_sift:siftslate\", \"minecraft:stone\"]}",
                "worldgen/feature/ore.json", """
                        {"type": "minecraft:ore", "size": 4, "discard_chance_on_air_exposure": 0,
                         "targets": [{"target": {"predicate_type": "minecraft:tag_match",
                                                 "tag": "the_sift:replaceables"},
                                      "state": {"id": "the_sift:siftslate_coal_ore"}}]}"""));
        var targets = out.get("worldgen/feature/ore.json").getAsJsonObject().getAsJsonArray("targets");
        assertEquals(2, targets.size());
        JsonObject first = targets.get(0).getAsJsonObject();
        assertEquals("minecraft:blockstate_match", first.getAsJsonObject("target").get("predicate_type").getAsString());
        assertEquals("minecraft:mushroom_stem", first.getAsJsonObject("target")
                .getAsJsonObject("block_state").get("id").getAsString());
        assertEquals("minecraft:mushroom_stem", first.getAsJsonObject("state").get("id").getAsString());
        assertEquals("{\"values\":[\"minecraft:mushroom_stem\",\"minecraft:stone\"]}",
                out.get("tags/block/replaceables.json").toString());
    }

    @Test
    void modSpawnsAndParticlesAreCleanedFromBiomes() {
        Map<String, JsonElement> out = run(Map.of("worldgen/biome/b.json", """
                {"attributes": {
                  "minecraft:visual/ambient_particles": {"argument": [{"particle": {"type": "the_sift:sift_note"},
                                                                      "probability": 0.1}]},
                  "minecraft:gameplay/natural_mob_spawns": {
                    "spawn_costs": {"the_sift:blub": {"charge": 1, "energy_budget": 1}},
                    "spawns_by_category": {"creature": [{"type": "the_sift:blub", "count": 1, "weight": 1},
                                                        {"type": "minecraft:sniffer", "count": 1, "weight": 1}]}}},
                 "features": []}"""));
        JsonObject attributes = out.get("worldgen/biome/b.json").getAsJsonObject().getAsJsonObject("attributes");
        assertEquals("minecraft:note", attributes.getAsJsonObject("minecraft:visual/ambient_particles")
                .getAsJsonArray("argument").get(0).getAsJsonObject().getAsJsonObject("particle")
                .get("type").getAsString());
        JsonObject spawns = attributes.getAsJsonObject("minecraft:gameplay/natural_mob_spawns");
        assertEquals(1, spawns.getAsJsonObject("spawns_by_category").getAsJsonArray("creature").size());
        assertTrue(spawns.getAsJsonObject("spawn_costs").isEmpty());
    }

    @Test
    void unresolvedReferencesAreReported() {
        run(Map.of("worldgen/placed_feature/p.json", "{\"feature\": \"the_sift:missing\", \"placement\": []}"));
        assertEquals(1, report.errors().size());
        assertTrue(report.errors().getFirst().contains("the_sift:missing"));
    }
}
