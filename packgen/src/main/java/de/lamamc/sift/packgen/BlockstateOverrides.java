package de.lamamc.sift.packgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Generates the resource pack overrides of the vanilla blockstate files whose unused states stand in for
 * custom blocks: those states get the models of the mod, all others keep their vanilla look.
 *
 * <ul>
 *   <li>Mushroom blocks are multipart in vanilla (one part per face). They become 64 variants: custom
 *       states use the mod model, vanilla states a generated cube with the outside or inside texture per
 *       face, which is what the vanilla parts add up to.</li>
 *   <li>Tripwire is variants over five properties; it is expanded to all 128 states with full keys.</li>
 *   <li>Chorus plant stays multipart: the vanilla parts only apply with {@code down=true} (all custom
 *       states have {@code down=false}, which a vanilla chorus plant cannot have), plus one part per
 *       custom state.</li>
 * </ul>
 */
public final class BlockstateOverrides {

    /** Generated vanilla-look models live here (assets/the_sift/models/block/vanilla/...). */
    public static final String VANILLA_MODEL_PREFIX = "the_sift:block/vanilla/";

    private static final List<String> MUSHROOM_PROPS = List.of("down", "east", "north", "south", "up", "west");
    private static final List<String> TRIPWIRE_PROPS = List.of("attached", "disarmed", "east", "north", "powered",
            "south", "west");
    private static final Map<String, String> MUSHROOM_TEXTURES = Map.of(
            "mushroom_stem", "minecraft:block/mushroom_stem",
            "brown_mushroom_block", "minecraft:block/brown_mushroom_block",
            "red_mushroom_block", "minecraft:block/red_mushroom_block");

    private final BlockMapping mapping;
    private final Function<String, JsonObject> modBlockstate;
    private final ImportReport report;
    /** assets path (e.g. assets/minecraft/blockstates/tripwire.json) -> content */
    private final Map<String, JsonElement> files = new TreeMap<>();

    /**
     * @param modBlockstate blockstate file of a mod block by id without namespace (null if missing)
     */
    public BlockstateOverrides(BlockMapping mapping, Function<String, JsonObject> modBlockstate, ImportReport report) {
        this.mapping = mapping;
        this.modBlockstate = modBlockstate;
        this.report = report;
    }

    /** Generates all overrides; {@code vanilla} gives a vanilla blockstate file by block name. */
    public Map<String, JsonElement> generate(Function<String, JsonObject> vanilla) {
        for (String block : MUSHROOM_TEXTURES.keySet()) {
            mushroom(block);
        }
        tripwire(vanilla.apply("tripwire"));
        chorus(vanilla.apply("chorus_plant"));
        return files;
    }

    /** custom vanilla state (as "k=v,..." over the given properties) -> mod variant to render */
    private Map<String, JsonElement> customVariants(String baseBlock, List<String> props) {
        Map<String, JsonElement> result = new LinkedHashMap<>();
        mapping.repurposed().values().forEach(entries -> entries.forEach(entry -> {
            if (entry.vanilla().id().equals("minecraft:" + baseBlock)) {
                result.put(key(entry.vanilla().properties(), props), modVariant(entry.custom()));
            }
        }));
        return result;
    }

    /** The variant of the mod blockstate that matches the custom state's properties. */
    private JsonElement modVariant(BlockState custom) {
        String id = custom.id().substring(BlockMapping.MOD_NAMESPACE.length() + 1);
        JsonObject blockstate = modBlockstate.apply(id);
        if (blockstate == null || !blockstate.has("variants")) {
            report.error("No variants blockstate in the mod for " + custom);
            return new JsonObject();
        }
        JsonObject variants = blockstate.getAsJsonObject("variants");
        for (var e : variants.entrySet()) {
            if (matches(e.getKey(), custom.properties())) {
                return e.getValue().deepCopy();
            }
        }
        report.error("No variant of the_sift:" + id + " matches " + custom.properties());
        return new JsonObject();
    }

    private static boolean matches(String variantKey, Map<String, String> props) {
        if (variantKey.isEmpty()) {
            return true;
        }
        for (String pair : variantKey.split(",")) {
            String[] kv = pair.split("=", 2);
            if (!kv[1].equals(props.get(kv[0]))) {
                return false;
            }
        }
        return true;
    }

    private void mushroom(String block) {
        Map<String, JsonElement> custom = customVariants(block, MUSHROOM_PROPS);
        JsonObject variants = new JsonObject();
        for (int bits = 0; bits < 64; bits++) {
            Map<String, String> state = new TreeMap<>();
            for (int i = 0; i < MUSHROOM_PROPS.size(); i++) {
                state.put(MUSHROOM_PROPS.get(i), Boolean.toString((bits >> i & 1) == 1));
            }
            String key = key(state, MUSHROOM_PROPS);
            if (custom.containsKey(key)) {
                variants.add(key, custom.get(key));
            } else {
                String model = block + "_" + faceCode(state);
                files.computeIfAbsent("assets/the_sift/models/block/vanilla/" + model + ".json",
                        k -> mushroomModel(MUSHROOM_TEXTURES.get(block), state));
                JsonObject variant = new JsonObject();
                variant.addProperty("model", VANILLA_MODEL_PREFIX + model);
                variants.add(key, variant);
            }
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        files.put("assets/minecraft/blockstates/" + block + ".json", root);
    }

    /** e.g. "d_n__w": the first letter of each face with the outside texture, "_" for inside */
    private static String faceCode(Map<String, String> state) {
        StringBuilder sb = new StringBuilder();
        for (String prop : MUSHROOM_PROPS) {
            sb.append("true".equals(state.get(prop)) ? prop.charAt(0) : '_');
        }
        return sb.toString();
    }

    private static JsonObject mushroomModel(String outside, Map<String, String> state) {
        JsonObject textures = new JsonObject();
        textures.addProperty("outside", outside);
        textures.addProperty("inside", "minecraft:block/mushroom_block_inside");
        textures.addProperty("particle", outside);
        JsonObject faces = new JsonObject();
        for (String face : MUSHROOM_PROPS) {
            JsonObject f = new JsonObject();
            f.addProperty("texture", "true".equals(state.get(face)) ? "#outside" : "#inside");
            f.addProperty("cullface", face);
            faces.add(face, f);
        }
        JsonObject element = new JsonObject();
        element.add("from", array(0, 0, 0));
        element.add("to", array(16, 16, 16));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private void tripwire(JsonObject vanilla) {
        JsonObject vanillaVariants = vanilla.getAsJsonObject("variants");
        Map<String, JsonElement> custom = customVariants("tripwire", TRIPWIRE_PROPS);
        JsonObject variants = new JsonObject();
        for (int bits = 0; bits < 128; bits++) {
            Map<String, String> state = new TreeMap<>();
            for (int i = 0; i < TRIPWIRE_PROPS.size(); i++) {
                state.put(TRIPWIRE_PROPS.get(i), Boolean.toString((bits >> i & 1) == 1));
            }
            String key = key(state, TRIPWIRE_PROPS);
            JsonElement variant = custom.get(key);
            if (variant == null) {
                variant = vanillaVariant(vanillaVariants, state);
            }
            variants.add(key, variant);
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        files.put("assets/minecraft/blockstates/tripwire.json", root);
    }

    private JsonElement vanillaVariant(JsonObject vanillaVariants, Map<String, String> state) {
        for (var e : vanillaVariants.entrySet()) {
            if (matches(e.getKey(), state)) {
                return e.getValue().deepCopy();
            }
        }
        report.error("Vanilla tripwire has no variant for " + state);
        return new JsonObject();
    }

    private void chorus(JsonObject vanilla) {
        JsonArray parts = new JsonArray();
        for (JsonElement p : vanilla.getAsJsonArray("multipart")) {
            JsonObject part = p.getAsJsonObject().deepCopy();
            JsonObject when = part.has("when") ? part.getAsJsonObject("when") : new JsonObject();
            if (when.has("OR") || when.has("AND")) {
                report.error("Unexpected combined condition in vanilla chorus_plant: " + when);
                continue;
            }
            if (when.has("down") && !"true".equals(when.get("down").getAsString())) {
                // only states with down=false would use this part, and those are all custom
                continue;
            }
            when.addProperty("down", "true");
            part.add("when", when);
            parts.add(part);
        }
        Map<String, JsonElement> custom = customVariants("chorus_plant", MUSHROOM_PROPS);
        for (var e : custom.entrySet()) {
            JsonObject when = new JsonObject();
            for (String pair : e.getKey().split(",")) {
                String[] kv = pair.split("=", 2);
                when.addProperty(kv[0], kv[1]);
            }
            JsonObject part = new JsonObject();
            part.add("when", when);
            part.add("apply", e.getValue());
            parts.add(part);
        }
        JsonObject root = new JsonObject();
        root.add("multipart", parts);
        files.put("assets/minecraft/blockstates/chorus_plant.json", root);
    }

    private static String key(Map<String, String> state, List<String> props) {
        List<String> pairs = new ArrayList<>();
        for (String prop : props) {
            pairs.add(prop + "=" + state.get(prop));
        }
        return String.join(",", pairs);
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int v : values) {
            array.add(v);
        }
        return array;
    }
}
