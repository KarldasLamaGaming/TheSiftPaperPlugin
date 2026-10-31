package de.lamamc.sift.packgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns the GeckoLib mobs of the original (geo model + texture) into what a vanilla client can show: one
 * item model per cube (box UV like GeckoLib), shown by one item display per cube, and a rig description
 * for the plugin (bones, pivots, cubes) in Java model space. GeckoLib reads Bedrock geometry with x
 * mirrored (x and the x/y rotations negated); the rig is written already converted, so the plugin only
 * composes transformations. Animations are copied unchanged.
 */
final class MobRigImporter {

    /** Mob -> its texture variants (the first is the default). */
    static final Map<String, List<String>> MOBS = new LinkedHashMap<>();

    static {
        MOBS.put("blub", List.of("blub", "tamed_blub"));
        MOBS.put("sifter", List.of("sifter"));
        MOBS.put("singer", List.of("singer"));
        MOBS.put("echo_golem", List.of("echo_golem"));
    }

    /** Item model elements must stay within -16..32; larger cubes are modelled smaller and scaled up by the display. */
    static final double MAX_ELEMENT = 32.0;

    private static final String GEO = "assets/the_sift/geckolib/models/entity/";
    private static final String ANIMATIONS = "assets/the_sift/geckolib/animations/entity/";
    private static final String TEXTURES = "assets/the_sift/textures/entity/";

    private MobRigImporter() {
    }

    static void run(ZipFile zip, Path pack, Path mobData, ImportReport report) throws IOException {
        Path assets = pack.resolve("assets/the_sift");
        for (var mob : MOBS.entrySet()) {
            JsonObject rig = convert(read(zip, GEO + mob.getKey() + ".geo.json").getAsJsonObject());
            Main.writeJson(mobData.resolve(mob.getKey() + ".rig.json"), rig);
            if (zip.getEntry(ANIMATIONS + mob.getKey() + ".animation.json") != null) {
                Main.writeJson(mobData.resolve(mob.getKey() + ".animation.json"),
                        read(zip, ANIMATIONS + mob.getKey() + ".animation.json"));
            }
            JsonArray cubes = rig.getAsJsonArray("cubes");
            for (String variant : mob.getValue()) {
                ZipEntry texture = zip.getEntry(TEXTURES + variant + ".png");
                if (texture == null) {
                    report.error("Mob texture missing: " + variant);
                    continue;
                }
                Path png = assets.resolve("textures/item/mob/" + variant + ".png");
                Files.createDirectories(png.getParent());
                try (InputStream in = zip.getInputStream(texture)) {
                    Files.write(png, in.readAllBytes());
                }
                for (int i = 0; i < cubes.size(); i++) {
                    String id = "mob/" + variant + "/" + i;
                    Main.writeJson(assets.resolve("models/item/" + id + ".json"), cubeModel(cubes.get(i).getAsJsonObject(),
                            rig.get("texture_width").getAsInt(), rig.get("texture_height").getAsInt(), "the_sift:item/mob/" + variant));
                    JsonObject model = new JsonObject();
                    model.addProperty("type", "minecraft:model");
                    model.addProperty("model", "the_sift:item/" + id);
                    JsonObject definition = new JsonObject();
                    definition.add("model", model);
                    Main.writeJson(assets.resolve("items/" + id + ".json"), definition);
                }
                report.note("Mob rigs (one item model per cube)", variant + ": " + cubes.size() + " cubes");
            }
        }
    }

    private static JsonElement read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("missing in the jar: " + name);
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Bedrock geometry -> rig in Java model space (pixels): bones with parent, pivot, rest rotation; cubes per bone. */
    static JsonObject convert(JsonObject geoFile) {
        JsonObject geometry = geoFile.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        JsonObject rig = new JsonObject();
        rig.addProperty("texture_width", description.get("texture_width").getAsInt());
        rig.addProperty("texture_height", description.get("texture_height").getAsInt());
        JsonArray bones = new JsonArray();
        JsonArray cubes = new JsonArray();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            JsonObject out = new JsonObject();
            out.addProperty("name", bone.get("name").getAsString());
            if (bone.has("parent")) {
                out.addProperty("parent", bone.get("parent").getAsString());
            }
            out.add("pivot", mirrorPoint(vector(bone, "pivot")));
            out.add("rotation", mirrorRotation(vector(bone, "rotation")));
            bones.add(out);
            if (!bone.has("cubes")) {
                continue;
            }
            for (JsonElement c : bone.getAsJsonArray("cubes")) {
                JsonObject cube = c.getAsJsonObject();
                double[] origin = vector(cube, "origin");
                double[] size = vector(cube, "size");
                JsonObject outCube = new JsonObject();
                outCube.addProperty("bone", bone.get("name").getAsString());
                outCube.add("from", array(-(origin[0] + size[0]), origin[1], origin[2]));
                outCube.add("size", array(size));
                double largest = Math.max(size[0], Math.max(size[1], size[2]));
                outCube.addProperty("model_scale", largest > MAX_ELEMENT ? MAX_ELEMENT / largest : 1.0);
                JsonArray uv = cube.getAsJsonArray("uv");
                outCube.add("uv", array(uv.get(0).getAsDouble(), uv.get(1).getAsDouble()));
                outCube.add("pivot", mirrorPoint(cube.has("pivot") ? vector(cube, "pivot") : new double[3]));
                outCube.add("rotation", mirrorRotation(vector(cube, "rotation")));
                cubes.add(outCube);
            }
        }
        rig.add("bones", bones);
        rig.add("cubes", cubes);
        return rig;
    }

    /**
     * Item model of one cube: one element from 0 to its size (times model_scale), faces with GeckoLib's box UV
     * (in Java space after the x mirror: west = u+d+w, east = u; down flipped).
     */
    static JsonObject cubeModel(JsonObject cube, int textureWidth, int textureHeight, String texture) {
        double[] size = numbers(cube.getAsJsonArray("size"));
        double k = cube.get("model_scale").getAsDouble();
        double u = cube.getAsJsonArray("uv").get(0).getAsDouble();
        double v = cube.getAsJsonArray("uv").get(1).getAsDouble();
        double w = size[0];
        double h = size[1];
        double d = size[2];
        JsonObject faces = new JsonObject();
        faces.add("north", face(u + d, v + d, u + d + w, v + d + h, textureWidth, textureHeight));
        faces.add("south", face(u + d + w + d, v + d, u + d + w + d + w, v + d + h, textureWidth, textureHeight));
        faces.add("west", face(u + d + w, v + d, u + d + w + d, v + d + h, textureWidth, textureHeight));
        faces.add("east", face(u, v + d, u + d, v + d + h, textureWidth, textureHeight));
        faces.add("up", face(u + d, v, u + d + w, v + d, textureWidth, textureHeight));
        faces.add("down", face(u + d + w, v + d, u + d + w + w, v, textureWidth, textureHeight));
        JsonObject element = new JsonObject();
        element.add("from", array(0, 0, 0));
        element.add("to", array(w * k, h * k, d * k));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject textures = new JsonObject();
        textures.addProperty("0", texture);
        textures.addProperty("particle", texture);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonObject face(double u1, double v1, double u2, double v2, int width, int height) {
        JsonObject face = new JsonObject();
        face.add("uv", array(round(u1 * 16 / width), round(v1 * 16 / height), round(u2 * 16 / width), round(v2 * 16 / height)));
        face.addProperty("texture", "#0");
        return face;
    }

    private static double round(double value) {
        return Math.round(value * 10000) / 10000.0;
    }

    private static double[] vector(JsonObject object, String key) {
        return object.has(key) ? numbers(object.getAsJsonArray(key)) : new double[3];
    }

    private static double[] numbers(JsonArray array) {
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsDouble();
        }
        return values;
    }

    private static JsonArray mirrorPoint(double[] p) {
        return array(-p[0], p[1], p[2]);
    }

    private static JsonArray mirrorRotation(double[] r) {
        return array(-r[0], -r[1], r[2]);
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value == 0 ? 0.0 : value); // no -0.0 in the files
        }
        return array;
    }
}
