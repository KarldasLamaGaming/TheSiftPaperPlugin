package de.lamamc.sift.packgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Turns the data files of the mod namespace into a datapack that a vanilla server can load: blocks are
 * replaced by their vanilla stand-ins, features of Java-only feature types (and everything that uses
 * them) are removed, as are spawn entries of mod entities and mod particles.
 *
 * <p>Paths are relative to {@code data/the_sift/}, e.g. {@code worldgen/feature/ichor_lake.json}.
 */
public final class DatapackImporter {

    /** Registries copied into the datapack; everything else (recipes, loot, ...) belongs to later steps. */
    public static final List<String> IMPORTED_DIRS = List.of(
            "dimension/", "dimension_type/", "timeline/", "world_clock/", "tags/timeline/", "tags/block/",
            "worldgen/biome/", "worldgen/carver/", "worldgen/density_function/", "worldgen/noise/",
            "worldgen/noise_settings/", "worldgen/feature/", "worldgen/placed_feature/",
            "worldgen/processor_list/");

    private static final String NS = BlockMapping.MOD_NAMESPACE + ":";
    private static final Pattern MOD_ID = Pattern.compile("#?" + NS + "[a-z0-9_/.-]+");
    private static final String FEATURE = "worldgen/feature/";
    private static final String PLACED = "worldgen/placed_feature/";
    private static final String BIOME = "worldgen/biome/";
    private static final String BLOCK_TAGS = "tags/block/";
    /** Keys whose value is a block state, which 26.x also accepts as a plain block id string. */
    private static final Set<String> STATE_KEYS = Set.of(
            "output_state", "input_state", "block_state", "state", "default_block", "default_fluid");

    private final BlockMapping mapping;
    private final ImportReport report;

    public DatapackImporter(BlockMapping mapping, ImportReport report) {
        this.mapping = mapping;
        this.report = report;
    }

    public Map<String, JsonElement> run(Map<String, JsonElement> source) {
        Map<String, JsonElement> files = new TreeMap<>();
        source.forEach((path, json) -> {
            if (IMPORTED_DIRS.stream().anyMatch(path::startsWith)) {
                files.put(path, json.deepCopy());
            } else {
                report.note("Not imported in this step", path);
            }
        });

        dropJavaFeatures(files);
        files.forEach((path, json) -> {
            if (path.startsWith(BIOME)) {
                cleanBiome(path, json.getAsJsonObject());
            }
        });
        checkRotatedStates(files);
        Map<String, List<String>> originalTags = readBlockTags(files);
        files.forEach((path, json) -> {
            if (path.startsWith(FEATURE)) {
                expandOreTargets(path, json, originalTags);
            }
        });
        files.replaceAll((path, json) -> path.startsWith(BLOCK_TAGS) ? rewriteTag(path, json) : rewrite(path, json));
        checkReferences(files);
        return files;
    }

    // ---- Java-only features -------------------------------------------------------------------------

    private void dropJavaFeatures(Map<String, JsonElement> files) {
        Set<String> droppedConfigured = new HashSet<>();
        Set<String> droppedPlaced = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var e : files.entrySet()) {
                String path = e.getKey();
                if (path.startsWith(FEATURE)) {
                    String id = idOf(path, FEATURE);
                    if (!droppedConfigured.contains(id)
                            && (hasModType(e.getValue()) || referencesAny(e.getValue(), droppedPlaced))) {
                        droppedConfigured.add(id);
                        changed = true;
                    }
                } else if (path.startsWith(PLACED)) {
                    String id = idOf(path, PLACED);
                    if (!droppedPlaced.contains(id)
                            && (hasModType(e.getValue()) || referencesAny(e.getValue(), droppedConfigured))) {
                        droppedPlaced.add(id);
                        changed = true;
                    }
                }
            }
        }
        files.keySet().removeIf(path -> {
            if (path.startsWith(FEATURE) && droppedConfigured.contains(idOf(path, FEATURE))) {
                String type = typeOf(files.get(path));
                report.note("Dropped configured features (Java-only)", idOf(path, FEATURE) + " (" + type + ")");
                return true;
            }
            if (path.startsWith(PLACED) && droppedPlaced.contains(idOf(path, PLACED))) {
                report.note("Dropped placed features", idOf(path, PLACED));
                return true;
            }
            return false;
        });
        files.forEach((path, json) -> {
            if (path.startsWith(BIOME)) {
                JsonArray steps = json.getAsJsonObject().getAsJsonArray("features");
                if (steps != null) {
                    for (JsonElement step : steps) {
                        JsonArray list = step.getAsJsonArray();
                        for (int i = list.size() - 1; i >= 0; i--) {
                            if (list.get(i).isJsonPrimitive() && droppedPlaced.contains(list.get(i).getAsString())) {
                                list.remove(i);
                            }
                        }
                    }
                }
            }
        });
    }

    private static boolean hasModType(JsonElement json) {
        if (json.isJsonObject()) {
            for (var e : json.getAsJsonObject().entrySet()) {
                if (e.getKey().equals("type") && e.getValue().isJsonPrimitive()
                        && e.getValue().getAsString().startsWith(NS)) {
                    return true;
                }
                if (hasModType(e.getValue())) {
                    return true;
                }
            }
        } else if (json.isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray()) {
                if (hasModType(el)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean referencesAny(JsonElement json, Set<String> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        if (json.isJsonPrimitive()) {
            return json.getAsJsonPrimitive().isString() && ids.contains(json.getAsString());
        }
        if (json.isJsonObject()) {
            return json.getAsJsonObject().entrySet().stream().anyMatch(e -> referencesAny(e.getValue(), ids));
        }
        if (json.isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray()) {
                if (referencesAny(el, ids)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- biomes -------------------------------------------------------------------------------------

    private void cleanBiome(String path, JsonObject biome) {
        String biomeId = idOf(path, BIOME);
        JsonObject attributes = biome.getAsJsonObject("attributes");
        if (attributes == null) {
            return;
        }
        JsonObject spawns = attributes.getAsJsonObject("minecraft:gameplay/natural_mob_spawns");
        if (spawns != null) {
            JsonObject byCategory = spawns.getAsJsonObject("spawns_by_category");
            if (byCategory != null) {
                for (var category : byCategory.entrySet()) {
                    JsonArray list = category.getValue().getAsJsonArray();
                    for (int i = list.size() - 1; i >= 0; i--) {
                        String type = list.get(i).getAsJsonObject().get("type").getAsString();
                        if (type.startsWith(NS)) {
                            report.note("Removed spawn entries (mobs come with phase 2)", type + " in " + biomeId);
                            list.remove(i);
                        }
                    }
                }
            }
            JsonObject costs = spawns.getAsJsonObject("spawn_costs");
            if (costs != null) {
                costs.keySet().removeIf(type -> type.startsWith(NS));
            }
        }
        JsonObject particles = attributes.getAsJsonObject("minecraft:visual/ambient_particles");
        if (particles != null && particles.get("argument") instanceof JsonArray list) {
            for (int i = list.size() - 1; i >= 0; i--) {
                JsonObject particle = list.get(i).getAsJsonObject().getAsJsonObject("particle");
                String type = particle.get("type").getAsString();
                if (type.startsWith(NS)) {
                    var replacement = mapping.particle(type);
                    if (replacement.isPresent()) {
                        particle.addProperty("type", replacement.get());
                        report.note("Replaced particles", type + " -> " + replacement.get());
                    } else {
                        list.remove(i);
                        report.note("Removed particles", type + " in " + biomeId);
                    }
                }
            }
        }
    }

    /** Processor lists change blocks of structure templates, which vanilla places rotated and mirrored. */
    private void checkRotatedStates(Map<String, JsonElement> files) {
        files.forEach((path, json) -> {
            if (path.startsWith("worldgen/processor_list/")) {
                collectStrings(json, null, (key, value) -> {
                    if (mapping.isRepurposed(value) && !mapping.survivesRotation(value)) {
                        report.error(path + ": " + value + " is placed in rotated templates; list it under "
                                + "rotation-invariant in block-mapping.yml");
                    }
                });
            }
        });
    }

    // ---- ores and tags ------------------------------------------------------------------------------

    private static Map<String, List<String>> readBlockTags(Map<String, JsonElement> files) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        files.forEach((path, json) -> {
            if (path.startsWith(BLOCK_TAGS)) {
                List<String> values = new ArrayList<>();
                json.getAsJsonObject().getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
                tags.put(idOf(path, BLOCK_TAGS), values);
            }
        });
        return tags;
    }

    /**
     * An ore that replaces a mod tag would, after mapping the tag to base blocks, replace every state of
     * the base block (e.g. all repurposed mushroom stems). Ore targets can list several exact states
     * instead, so such targets are expanded to one blockstate_match per state.
     */
    private void expandOreTargets(String path, JsonElement json, Map<String, List<String>> tags) {
        JsonObject feature = json.getAsJsonObject();
        if (!"minecraft:ore".equals(typeOf(feature)) || !(feature.get("targets") instanceof JsonArray targets)) {
            return;
        }
        JsonArray expanded = new JsonArray();
        for (JsonElement t : targets) {
            JsonObject target = t.getAsJsonObject();
            JsonObject rule = target.getAsJsonObject("target");
            String tag = rule.has("tag") ? rule.get("tag").getAsString().replaceFirst("^#", "") : null;
            if ("minecraft:tag_match".equals(rule.get("predicate_type").getAsString()) && tags.containsKey(tag)) {
                for (String value : tags.get(tag)) {
                    List<BlockState> states = mapping.isMapped(value)
                            ? mapping.allStates(value) : List.of(BlockState.of(value));
                    for (BlockState state : states) {
                        JsonObject newRule = new JsonObject();
                        newRule.addProperty("predicate_type", "minecraft:blockstate_match");
                        newRule.add("block_state", stateJson(state, "id", "properties"));
                        JsonObject newTarget = target.deepCopy();
                        newTarget.add("target", newRule);
                        expanded.add(newTarget);
                    }
                }
                report.note("Ore targets expanded to exact states", idOf(path, FEATURE) + " (#" + tag + ")");
            } else {
                expanded.add(target);
            }
        }
        feature.add("targets", expanded);
    }

    private JsonElement rewriteTag(String path, JsonElement json) {
        JsonObject tag = json.getAsJsonObject().deepCopy();
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement v : tag.getAsJsonArray("values")) {
            String id = v.getAsString();
            if (mapping.isMapped(id)) {
                String base = mapping.baseBlock(id);
                values.add(base);
                if (mapping.isRepurposed(id)) {
                    report.note("Block tags widened to the base block", "#" + idOf(path, BLOCK_TAGS) + ": " + id + " -> " + base);
                }
            } else {
                values.add(id);
            }
        }
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        tag.add("values", array);
        return tag;
    }

    // ---- block rewriting ----------------------------------------------------------------------------

    private JsonElement rewrite(String path, JsonElement json) {
        if (json.isJsonArray()) {
            JsonArray result = new JsonArray();
            json.getAsJsonArray().forEach(el -> result.add(rewrite(path, el)));
            return result;
        }
        if (!json.isJsonObject()) {
            return json;
        }
        JsonObject object = json.getAsJsonObject();

        BlockState state = readState(object, "id", "properties");
        String idKey = "id";
        String propsKey = "properties";
        if (state == null) {
            state = readState(object, "Name", "Properties");
            idKey = "Name";
            propsKey = "Properties";
        }
        if (state != null && mapping.isMapped(state.id())) {
            JsonObject result = stateJson(mapping.resolve(state), idKey, propsKey);
            object.entrySet().stream()
                    .filter(e -> !e.getKey().equals("id") && !e.getKey().equals("properties")
                            && !e.getKey().equals("Name") && !e.getKey().equals("Properties"))
                    .forEach(e -> result.add(e.getKey(), rewrite(path, e.getValue())));
            return result;
        }

        String predicateType = object.has("predicate_type") ? object.get("predicate_type").getAsString() : "";
        if (predicateType.equals("minecraft:block_match") && mapping.isMapped(object.get("block").getAsString())) {
            JsonObject result = new JsonObject();
            result.addProperty("predicate_type", "minecraft:blockstate_match");
            result.add("block_state", stateJson(
                    mapping.resolve(BlockState.of(object.get("block").getAsString())), "id", "properties"));
            return result;
        }

        JsonObject result = new JsonObject();
        boolean matchingBlocks = "minecraft:matching_blocks".equals(typeOf(object));
        for (var e : object.entrySet()) {
            if (STATE_KEYS.contains(e.getKey()) && e.getValue() instanceof JsonPrimitive p && p.isString()
                    && mapping.isMapped(p.getAsString())) {
                result.add(e.getKey(), stateJson(mapping.resolve(BlockState.of(p.getAsString())), "id", "properties"));
            } else if (matchingBlocks && e.getKey().equals("blocks")) {
                result.add("blocks", rewriteBlockIds(path, e.getValue()));
            } else {
                result.add(e.getKey(), rewrite(path, e.getValue()));
            }
        }
        return result;
    }

    private JsonElement rewriteBlockIds(String path, JsonElement blocks) {
        if (blocks.isJsonPrimitive()) {
            return new JsonPrimitive(baseIdFor(path, blocks.getAsString()));
        }
        Set<String> ids = new LinkedHashSet<>();
        blocks.getAsJsonArray().forEach(b -> ids.add(baseIdFor(path, b.getAsString())));
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        return array.size() == 1 ? array.get(0) : array;
    }

    private String baseIdFor(String path, String id) {
        if (!mapping.isMapped(id)) {
            return id;
        }
        String base = mapping.baseBlock(id);
        if (mapping.isRepurposed(id)) {
            report.note("Block predicates widened to the base block", path + ": " + id + " -> " + base);
        }
        return base;
    }

    private static BlockState readState(JsonObject object, String idKey, String propsKey) {
        if (!(object.get(idKey) instanceof JsonPrimitive id) || !id.isString()) {
            return null;
        }
        for (String key : object.keySet()) {
            if (!key.equals(idKey) && !key.equals(propsKey)) {
                return null;
            }
        }
        Map<String, String> props = new TreeMap<>();
        if (object.get(propsKey) instanceof JsonObject p) {
            p.entrySet().forEach(e -> props.put(e.getKey(), e.getValue().getAsString()));
        }
        return BlockState.of(id.getAsString(), props);
    }

    private static JsonObject stateJson(BlockState state, String idKey, String propsKey) {
        JsonObject json = new JsonObject();
        json.addProperty(idKey, state.id());
        if (!state.properties().isEmpty()) {
            JsonObject props = new JsonObject();
            state.properties().forEach(props::addProperty);
            json.add(propsKey, props);
        }
        return json;
    }

    // ---- validation ---------------------------------------------------------------------------------

    /** Every remaining mod id has to point to a file of the datapack, otherwise the server rejects the pack. */
    private void checkReferences(Map<String, JsonElement> files) {
        Set<String> known = new HashSet<>();
        for (String path : files.keySet()) {
            String withoutExt = path.substring(0, path.lastIndexOf('.'));
            int split = registrySplit(withoutExt);
            String registry = withoutExt.substring(0, split);
            String id = NS + withoutExt.substring(split + 1);
            known.add(id);
            if (registry.startsWith("tags/")) {
                // tag references are written with '#' in most places, but without it in rule tests
                known.add("#" + id);
            }
        }
        files.forEach((path, json) -> collectStrings(json, null, (key, s) -> {
            if (STATE_KEYS.contains(key) && mapping.isMapped(s)) {
                report.error(path + ": block " + s + " in '" + key + "' was not rewritten");
                return;
            }
            // features are often named like the block they place, so a known file id wins
            if (!MOD_ID.matcher(s).matches() || known.contains(s)) {
                return;
            }
            if (mapping.isMapped(s.replaceFirst("^#", ""))) {
                report.error(path + ": block " + s + " was not rewritten");
            } else {
                report.error(path + ": unresolved reference " + s);
            }
        }));
    }

    private static int registrySplit(String pathWithoutExt) {
        for (String dir : IMPORTED_DIRS) {
            if (pathWithoutExt.startsWith(dir)) {
                return dir.length() - 1;
            }
        }
        return pathWithoutExt.indexOf('/');
    }

    private static void collectStrings(JsonElement json, String key,
                                       java.util.function.BiConsumer<String, String> sink) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            // random sequence names are free-form ids, not references
            if (!"random_name".equals(key)) {
                sink.accept(key, json.getAsString());
            }
        } else if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(e -> collectStrings(e.getValue(), e.getKey(), sink));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(el -> collectStrings(el, key, sink));
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    static String idOf(String path, String dir) {
        return NS + path.substring(dir.length(), path.length() - ".json".length());
    }

    private static String typeOf(JsonElement json) {
        if (json.isJsonObject() && json.getAsJsonObject().get("type") instanceof JsonPrimitive p) {
            return p.getAsString();
        }
        return "";
    }
}
