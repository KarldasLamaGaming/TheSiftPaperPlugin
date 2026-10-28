package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockstateOverridesTest {

    private static final JsonObject VANILLA_TRIPWIRE = vanillaTripwire();
    private static final JsonObject VANILLA_CHORUS = JsonParser.parseString("""
            {"multipart": [
              {"apply": {"model": "minecraft:block/chorus_plant_side"}, "when": {"north": "true"}},
              {"apply": {"model": "minecraft:block/chorus_plant_side", "x": 90}, "when": {"down": "true"}},
              {"apply": {"model": "minecraft:block/chorus_plant_noside"}, "when": {"north": "false"}},
              {"apply": {"model": "minecraft:block/chorus_plant_noside", "x": 90}, "when": {"down": "false"}}
            ]}""").getAsJsonObject();

    private static JsonObject vanillaTripwire() {
        JsonObject variants = new JsonObject();
        for (int bits = 0; bits < 32; bits++) {
            String key = "attached=" + ((bits & 1) == 1) + ",east=" + ((bits & 2) == 2) + ",north=" + ((bits & 4) == 4)
                    + ",south=" + ((bits & 8) == 8) + ",west=" + ((bits & 16) == 16);
            JsonObject model = new JsonObject();
            model.addProperty("model", "minecraft:block/tripwire_" + bits);
            variants.add(key, model);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    /** Every mod block has a one-variant blockstate pointing to the_sift:block/<id>. */
    private static JsonObject modBlockstate(String id) {
        return JsonParser.parseString("{\"variants\": {\"\": {\"model\": \"the_sift:block/" + id + "\"}}}")
                .getAsJsonObject();
    }

    private Map<String, JsonElement> generate(ImportReport report) {
        return new BlockstateOverrides(BlockMapping.loadDefault(), BlockstateOverridesTest::modBlockstate, report)
                .generate(block -> block.equals("tripwire") ? VANILLA_TRIPWIRE : VANILLA_CHORUS);
    }

    @Test
    void mushroomStemHasAllStatesWithModModelsForCustomStates() {
        ImportReport report = new ImportReport();
        Map<String, JsonElement> files = generate(report);
        JsonObject variants = files.get("assets/minecraft/blockstates/mushroom_stem.json").getAsJsonObject()
                .getAsJsonObject("variants");
        assertEquals(64, variants.size());
        assertEquals("the_sift:block/siftslate", variants
                .getAsJsonObject("down=false,east=false,north=false,south=false,up=false,west=false")
                .get("model").getAsString());
        String placed = variants.getAsJsonObject("down=true,east=true,north=true,south=true,up=true,west=true")
                .get("model").getAsString();
        assertEquals("the_sift:block/vanilla/mushroom_stem_densuw", placed);
        JsonObject model = files.get("assets/the_sift/models/block/vanilla/mushroom_stem_densuw.json").getAsJsonObject();
        assertEquals("minecraft:block/mushroom_stem", model.getAsJsonObject("textures").get("outside").getAsString());
        assertTrue(report.errors().isEmpty(), report.errors().toString());
    }

    @Test
    void tripwireUsesFullKeysAndKeepsVanillaModels() {
        Map<String, JsonElement> files = generate(new ImportReport());
        JsonObject variants = files.get("assets/minecraft/blockstates/tripwire.json").getAsJsonObject()
                .getAsJsonObject("variants");
        assertEquals(128, variants.size());
        // plain string as placed in vanilla
        assertEquals("minecraft:block/tripwire_0", variants.getAsJsonObject(
                "attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=false")
                .get("model").getAsString());
        assertEquals("the_sift:block/siftslate_stalks", variants.getAsJsonObject(
                "attached=false,disarmed=true,east=false,north=false,powered=true,south=false,west=false")
                .get("model").getAsString());
    }

    @Test
    void chorusVanillaPartsRequireDownTrue() {
        Map<String, JsonElement> files = generate(new ImportReport());
        var parts = files.get("assets/minecraft/blockstates/chorus_plant.json").getAsJsonObject().getAsJsonArray("multipart");
        boolean customPart = false;
        for (JsonElement p : parts) {
            JsonObject when = p.getAsJsonObject().getAsJsonObject("when");
            String model = p.getAsJsonObject().get("apply").toString();
            if (model.contains("the_sift:block/ichor_glass")) {
                customPart = true;
                assertEquals("false", when.get("down").getAsString());
                assertEquals(6, when.size());
            } else {
                assertEquals("true", when.get("down").getAsString());
            }
        }
        assertTrue(customPart);
        // 4 vanilla parts, the one for down=false is gone, plus one part for ichor glass
        assertEquals(4, parts.size());
    }
}
