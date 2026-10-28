package de.lamamc.sift.packgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Block ids and property values of the vanilla server, read from the data generator report
 * ({@code java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports} writes
 * {@code generated/reports/blocks.json}). Used to check that every state the import produces exists.
 */
public final class VanillaBlocks {

    private final Map<String, Map<String, Set<String>>> blocks;

    private VanillaBlocks(Map<String, Map<String, Set<String>>> blocks) {
        this.blocks = blocks;
    }

    public static VanillaBlocks load(Path blocksJson) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(blocksJson)).getAsJsonObject();
        Map<String, Map<String, Set<String>>> blocks = new HashMap<>();
        root.entrySet().forEach(e -> {
            Map<String, Set<String>> props = new HashMap<>();
            JsonObject properties = e.getValue().getAsJsonObject().getAsJsonObject("properties");
            if (properties != null) {
                properties.entrySet().forEach(p -> {
                    Set<String> values = new TreeSet<>();
                    p.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString()));
                    props.put(p.getKey(), values);
                });
            }
            blocks.put(e.getKey(), props);
        });
        return new VanillaBlocks(blocks);
    }

    /** Null if the state exists, otherwise why not. */
    public String check(BlockState state) {
        Map<String, Set<String>> props = blocks.get(state.id());
        if (props == null) {
            return "unknown block " + state.id();
        }
        for (var e : state.properties().entrySet()) {
            Set<String> values = props.get(e.getKey());
            if (values == null) {
                return "unknown property " + e.getKey() + " of " + state.id();
            }
            if (!values.contains(e.getValue())) {
                return "bad value " + e.getKey() + "=" + e.getValue() + " of " + state.id();
            }
        }
        return null;
    }

    /** Checks the mapping and every block state written into the datapack under {@code datapackRoot}. */
    public void validate(BlockMapping mapping, ImportReport report, Path datapackRoot) throws IOException {
        mapping.repurposed().values().forEach(entries -> entries.forEach(entry -> {
            String problem = check(entry.vanilla());
            if (problem != null) {
                report.error("mapping " + entry.custom() + ": " + problem);
            }
        }));
        mapping.substitutes().forEach((id, state) -> {
            String problem = check(state);
            if (problem != null) {
                report.error("mapping " + id + ": " + problem);
            }
        });
        try (Stream<Path> walk = Files.walk(datapackRoot)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".json")).toList()) {
                checkJson(JsonParser.parseString(Files.readString(file)), datapackRoot.relativize(file).toString(), report);
            }
        }
    }

    private void checkJson(JsonElement json, String file, ImportReport report) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(el -> checkJson(el, file, report));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("id") && object.get("id").isJsonPrimitive() && isStateObject(object)) {
                String id = object.get("id").getAsString();
                if (id.startsWith("minecraft:") && blocks.containsKey(id)) {
                    Map<String, String> props = new HashMap<>();
                    if (object.get("properties") instanceof JsonObject p) {
                        p.entrySet().forEach(e -> props.put(e.getKey(), e.getValue().getAsString()));
                    }
                    String problem = check(BlockState.of(id, props));
                    if (problem != null) {
                        report.error(file + ": " + problem);
                    }
                }
            }
            object.entrySet().forEach(e -> checkJson(e.getValue(), file, report));
        }
    }

    private static boolean isStateObject(JsonObject object) {
        return object.keySet().stream().allMatch(k -> List.of("id", "properties").contains(k));
    }
}
