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
        MOBS.put("echo_golem", List.of("echo_golem", "echo_golem_glow"));
        MOBS.put("dark_sniffer", List.of("dark_sniffer"));
        MOBS.put("charoite_wisp", List.of("charoite_wisp"));
    }

    /**
     * Mobs whose model is our own (packgen resources mobs/&lt;id&gt;.geo.json and .animation.json) instead of the
     * original's: the Dark Sniffer is the vanilla sniffer model in the original, which must not be copied.
     */
    static final java.util.Set<String> OWN_MODELS = java.util.Set.of("dark_sniffer", "charoite_wisp");

    /**
     * Own mobs that are not in the original at all (alpha.42d Charoite Wisp): their texture is ours too
     * (packgen resources mobs/&lt;id&gt;.png, scripts/gen_charoite_wisp.py), and every cube glows.
     */
    static final java.util.Set<String> OWN_TEXTURES = java.util.Set.of("charoite_wisp");

    /**
     * Variants made here instead of read from the jar: the carrying echo golem (EchoGolemSoulGlowLayer of the
     * original) shows its emissive texture over the base one, and the cubes of the glowing bone give off light.
     */
    record GlowVariant(String base, String emissive, String bone) {
    }

    static final Map<String, GlowVariant> GLOW_VARIANTS = Map.of("echo_golem_glow",
            new GlowVariant("echo_golem", "echo_golem/echo_golem_emissive", "soul_block"));

    /** Light level of glowing cubes (model element light_emission, 0–15). */
    static final int GLOW_LIGHT = 15;

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
            boolean own = OWN_MODELS.contains(mob.getKey());
            JsonObject rig = convert((own ? resource("mobs/" + mob.getKey() + ".geo.json")
                    : read(zip, GEO + mob.getKey() + ".geo.json")).getAsJsonObject());
            Main.writeJson(mobData.resolve(mob.getKey() + ".rig.json"), rig);
            if (own) {
                Main.writeJson(mobData.resolve(mob.getKey() + ".animation.json"), resource("mobs/" + mob.getKey() + ".animation.json"));
            } else if (zip.getEntry(ANIMATIONS + mob.getKey() + ".animation.json") != null) {
                Main.writeJson(mobData.resolve(mob.getKey() + ".animation.json"),
                        read(zip, ANIMATIONS + mob.getKey() + ".animation.json"));
            }
            JsonArray cubes = rig.getAsJsonArray("cubes");
            for (String variant : mob.getValue()) {
                if (OWN_TEXTURES.contains(variant)) {
                    ownTexture(variant, cubes, rig, assets);
                    report.note("Mob rigs (one item model per cube)", variant + ": " + cubes.size() + " cubes (own model and texture)");
                    continue;
                }
                GlowVariant glow = GLOW_VARIANTS.get(variant);
                ZipEntry texture = zip.getEntry(TEXTURES + (glow != null ? glow.base() : variant) + ".png");
                if (texture == null) {
                    report.error("Mob texture missing: " + variant);
                    continue;
                }
                Path png = assets.resolve("textures/item/mob/" + variant + ".png");
                Files.createDirectories(png.getParent());
                try (InputStream in = zip.getInputStream(texture)) {
                    byte[] bytes = in.readAllBytes();
                    if (glow != null) {
                        ZipEntry emissive = zip.getEntry(TEXTURES + glow.emissive() + ".png");
                        if (emissive == null) {
                            report.error("Emissive texture missing: " + glow.emissive());
                        } else {
                            try (InputStream e = zip.getInputStream(emissive)) {
                                bytes = overlay(bytes, e.readAllBytes());
                            }
                        }
                    }
                    Files.write(png, bytes);
                }
                for (int i = 0; i < cubes.size(); i++) {
                    String id = "mob/" + variant + "/" + i;
                    JsonObject cubeModel = cubeModel(cubes.get(i).getAsJsonObject(),
                            rig.get("texture_width").getAsInt(), rig.get("texture_height").getAsInt(), "the_sift:item/mob/" + variant);
                    if (glow != null && glow.bone().equals(cubes.get(i).getAsJsonObject().get("bone").getAsString())) {
                        cubeModel.getAsJsonArray("elements").get(0).getAsJsonObject().addProperty("light_emission", GLOW_LIGHT);
                    }
                    Main.writeJson(assets.resolve("models/item/" + id + ".json"), cubeModel);
                    JsonObject model = new JsonObject();
                    model.addProperty("type", "minecraft:model");
                    model.addProperty("model", "the_sift:item/" + id);
                    // the plugin tints all cubes of a mob at once (hurt flash) through custom_model_data colors
                    JsonObject tint = new JsonObject();
                    tint.addProperty("type", "minecraft:custom_model_data");
                    tint.addProperty("index", 0);
                    tint.addProperty("default", 0xFFFFFF);
                    JsonArray tints = new JsonArray();
                    tints.add(tint);
                    model.add("tints", tints);
                    JsonObject definition = new JsonObject();
                    definition.add("model", model);
                    Main.writeJson(assets.resolve("items/" + id + ".json"), definition);
                }
                report.note("Mob rigs (one item model per cube)", variant + ": " + cubes.size() + " cubes");
            }
        }
    }

    /** The base texture with every visible pixel of the emissive texture drawn over it. */
    static byte[] overlay(byte[] base, byte[] emissive) throws IOException {
        java.awt.image.BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(base));
        java.awt.image.BufferedImage e = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(emissive));
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(b.getWidth(), b.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < b.getWidth(); x++) {
            for (int y = 0; y < b.getHeight(); y++) {
                int argb = b.getRGB(x, y);
                if (x < e.getWidth() && y < e.getHeight() && (e.getRGB(x, y) >>> 24) > 0) {
                    argb = e.getRGB(x, y) | 0xFF000000;
                }
                out.setRGB(x, y, argb);
            }
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(out, "png", bytes);
        return bytes.toByteArray();
    }

    /** A mob with our own texture: the PNG from the packgen resources, every cube model full bright. */
    private static void ownTexture(String variant, JsonArray cubes, JsonObject rig, Path assets) throws IOException {
        Path png = assets.resolve("textures/item/mob/" + variant + ".png");
        Files.createDirectories(png.getParent());
        try (InputStream in = MobRigImporter.class.getResourceAsStream("/mobs/" + variant + ".png")) {
            if (in == null) {
                throw new IOException("missing packgen resource mobs/" + variant + ".png");
            }
            Files.write(png, in.readAllBytes());
        }
        for (int i = 0; i < cubes.size(); i++) {
            String id = "mob/" + variant + "/" + i;
            JsonObject cubeModel = cubeModel(cubes.get(i).getAsJsonObject(), rig.get("texture_width").getAsInt(),
                    rig.get("texture_height").getAsInt(), "the_sift:item/mob/" + variant);
            for (JsonElement element : cubeModel.getAsJsonArray("elements")) {
                element.getAsJsonObject().addProperty("light_emission", GLOW_LIGHT);
            }
            Main.writeJson(assets.resolve("models/item/" + id + ".json"), cubeModel);
            JsonObject model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", "the_sift:item/" + id);
            JsonObject tint = new JsonObject();
            tint.addProperty("type", "minecraft:custom_model_data");
            tint.addProperty("index", 0);
            tint.addProperty("default", 0xFFFFFF);
            JsonArray tints = new JsonArray();
            tints.add(tint);
            model.add("tints", tints);
            JsonObject definition = new JsonObject();
            definition.add("model", model);
            Main.writeJson(assets.resolve("items/" + id + ".json"), definition);
        }
    }

    static JsonElement resource(String name) throws IOException {
        try (InputStream in = MobRigImporter.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("missing packgen resource " + name);
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
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
                if (cube.get("uv").isJsonObject()) {
                    // per-face UV: [u, v, width, height] per face; east and west swap with the x mirror
                    JsonObject faces = new JsonObject();
                    for (var face : cube.getAsJsonObject("uv").entrySet()) {
                        JsonObject f = face.getValue().getAsJsonObject();
                        String name = switch (face.getKey()) {
                            case "east" -> "west";
                            case "west" -> "east";
                            default -> face.getKey();
                        };
                        faces.add(name, array(f.getAsJsonArray("uv").get(0).getAsDouble(), f.getAsJsonArray("uv").get(1).getAsDouble(),
                                f.getAsJsonArray("uv_size").get(0).getAsDouble(), f.getAsJsonArray("uv_size").get(1).getAsDouble()));
                    }
                    outCube.add("face_uv", faces);
                    outCube.add("uv", array(0, 0));
                } else {
                    JsonArray uv = cube.getAsJsonArray("uv");
                    outCube.add("uv", array(uv.get(0).getAsDouble(), uv.get(1).getAsDouble()));
                }
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
        if (cube.has("face_uv")) {
            for (var f : cube.getAsJsonObject("face_uv").entrySet()) {
                double[] r = numbers(f.getValue().getAsJsonArray());
                faces.add(f.getKey(), face(r[0], r[1], r[0] + r[2], r[1] + r[3], textureWidth, textureHeight));
            }
        } else {
        faces.add("north", face(u + d, v + d, u + d + w, v + d + h, textureWidth, textureHeight));
        faces.add("south", face(u + d + w + d, v + d, u + d + w + d + w, v + d + h, textureWidth, textureHeight));
        faces.add("west", face(u + d + w, v + d, u + d + w + d, v + d + h, textureWidth, textureHeight));
        faces.add("east", face(u, v + d, u + d, v + d + h, textureWidth, textureHeight));
        faces.add("up", face(u + d, v, u + d + w, v + d, textureWidth, textureHeight));
        faces.add("down", face(u + d + w, v + d, u + d + w + w, v, textureWidth, textureHeight));
        }
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
        face.addProperty("tintindex", 0);
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
