package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobRigImporterTest {

    private static final String GEO = """
            {"format_version": "1.12.0", "minecraft:geometry": [{
              "description": {"identifier": "geometry.test", "texture_width": 128, "texture_height": 64},
              "bones": [
                {"name": "body", "pivot": [1, 2, 3], "rotation": [10, 20, 30],
                 "cubes": [{"origin": [2, 0, -1], "size": [4, 5, 6], "uv": [8, 4]}]},
                {"name": "arm", "parent": "body", "pivot": [-2, 4, 0]},
                {"name": "tail", "parent": "body",
                 "cubes": [{"origin": [0, 0, 0], "size": [40, 2, 2], "uv": [0, 40], "pivot": [3, 0, 0], "rotation": [5, 6, 7]}]}
              ]}]}
            """;

    private static JsonObject rig() {
        return MobRigImporter.convert(JsonParser.parseString(GEO).getAsJsonObject());
    }

    private static double[] numbers(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsDouble();
        }
        return values;
    }

    @Test
    void keepsAllBonesAndCubesWithParents() {
        JsonObject rig = rig();
        assertEquals(3, rig.getAsJsonArray("bones").size());
        assertEquals(2, rig.getAsJsonArray("cubes").size());
        assertFalse(rig.getAsJsonArray("bones").get(0).getAsJsonObject().has("parent"));
        assertEquals("body", rig.getAsJsonArray("bones").get(1).getAsJsonObject().get("parent").getAsString());
        assertEquals("tail", rig.getAsJsonArray("cubes").get(1).getAsJsonObject().get("bone").getAsString());
    }

    @Test
    void mirrorsXLikeGeckoLib() {
        JsonObject body = rig().getAsJsonArray("bones").get(0).getAsJsonObject();
        assertEquals(-1.0, numbers(body.getAsJsonArray("pivot"))[0]);
        double[] rotation = numbers(body.getAsJsonArray("rotation"));
        assertEquals(-10.0, rotation[0]);
        assertEquals(-20.0, rotation[1]);
        assertEquals(30.0, rotation[2]);
        JsonObject cube = rig().getAsJsonArray("cubes").get(0).getAsJsonObject();
        double[] from = numbers(cube.getAsJsonArray("from"));
        assertEquals(-6.0, from[0]); // -(2 + 4)
        assertEquals(0.0, from[1]);
        assertEquals(-1.0, from[2]);
    }

    @Test
    void largeCubesAreModelledSmaller() {
        JsonObject tail = rig().getAsJsonArray("cubes").get(1).getAsJsonObject();
        assertEquals(0.8, tail.get("model_scale").getAsDouble());
        JsonObject model = MobRigImporter.cubeModel(tail, 128, 64, "the_sift:item/mob/test");
        double[] to = numbers(model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to"));
        assertTrue(to[0] <= MobRigImporter.MAX_ELEMENT);
        assertEquals(1.6, to[1], 1e-9);
    }

    @Test
    void boxUvStaysOnTheTexture() {
        JsonObject rig = rig();
        for (var element : rig.getAsJsonArray("cubes")) {
            JsonObject model = MobRigImporter.cubeModel(element.getAsJsonObject(), 128, 64, "the_sift:item/mob/test");
            JsonObject faces = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
            assertEquals(6, faces.size());
            for (String face : faces.keySet()) {
                for (double uv : numbers(faces.getAsJsonObject(face).getAsJsonArray("uv"))) {
                    assertTrue(uv >= 0 && uv <= 16, face + " uv " + uv);
                }
            }
        }
        // body cube: u 8, v 4, w 4, h 5, d 6 on 128×64 -> north (u+d, v+d) .. (u+d+w, v+d+h)
        JsonObject body = MobRigImporter.cubeModel(rig.getAsJsonArray("cubes").get(0).getAsJsonObject(), 128, 64, "t");
        double[] north = numbers(body.getAsJsonArray("elements").get(0).getAsJsonObject()
                .getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv"));
        assertEquals(14 * 16 / 128.0, north[0]);
        assertEquals(10 * 16 / 64.0, north[1]);
        assertEquals(18 * 16 / 128.0, north[2]);
        assertEquals(15 * 16 / 64.0, north[3]);
    }

    /** The committed pack sources: every rig cube has its item model and definition for every variant. */
    @Test
    void committedRigsHaveAllModels() throws IOException {
        Path packSrc = Path.of(System.getProperty("sift.packSrc", "../pack-src"));
        Path assets = packSrc.resolve("resourcepack/assets/the_sift");
        for (var mob : MobRigImporter.MOBS.entrySet()) {
            Path rigFile = packSrc.resolve("mob-data/" + mob.getKey() + ".rig.json");
            assertTrue(Files.exists(rigFile), rigFile.toString());
            int cubes = JsonParser.parseString(Files.readString(rigFile)).getAsJsonObject().getAsJsonArray("cubes").size();
            assertTrue(cubes > 0);
            for (String variant : mob.getValue()) {
                assertTrue(Files.exists(assets.resolve("textures/item/mob/" + variant + ".png")), variant);
                for (int i = 0; i < cubes; i++) {
                    assertTrue(Files.exists(assets.resolve("models/item/mob/" + variant + "/" + i + ".json")), variant + " " + i);
                    assertTrue(Files.exists(assets.resolve("items/mob/" + variant + "/" + i + ".json")), variant + " " + i);
                }
            }
        }
    }
}
