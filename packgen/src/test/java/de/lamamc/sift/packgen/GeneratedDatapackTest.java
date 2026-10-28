package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Checks the committed output of the import in pack-src/ (no network or mod jar needed). */
class GeneratedDatapackTest {

    private static final Pattern MOD_BLOCK_STATE = Pattern.compile("\"(?:id|Name)\"\\s*:\\s*\"(the_sift:[a-z0-9_]+)\"");

    private static Path datapack() {
        String dir = System.getProperty("sift.packSrc", "../pack-src");
        Path path = Path.of(dir, "datapack", "data", "the_sift");
        assumeTrue(Files.isDirectory(path), "pack-src not generated yet");
        return path;
    }

    @Test
    void noModBlockIsLeftInTheDatapack() throws IOException {
        BlockMapping mapping = BlockMapping.loadDefault();
        List<String> leftovers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(datapack())) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Matcher m = MOD_BLOCK_STATE.matcher(Files.readString(file));
                while (m.find()) {
                    if (mapping.isMapped(m.group(1))) {
                        leftovers.add(file.getFileName() + ": " + m.group(1));
                    }
                }
            }
        }
        assertTrue(leftovers.isEmpty(), "mod blocks left: " + leftovers);
    }

    @Test
    void dimensionAndNoiseSettingsArePresent() {
        Path root = datapack();
        assertTrue(Files.isRegularFile(root.resolve("dimension/the_sift.json")));
        assertTrue(Files.isRegularFile(root.resolve("dimension_type/the_sift.json")));
        assertTrue(Files.isRegularFile(root.resolve("worldgen/noise_settings/the_sift.json")));
    }

    @Test
    void everyRepurposedStateHasAnOverrideWithAModModel() throws IOException {
        Path packSrc = datapack().getParent().getParent().getParent();
        var mapping = com.google.gson.JsonParser.parseString(Files.readString(packSrc.resolve("block-mapping.json")))
                .getAsJsonObject().getAsJsonObject("repurposed");
        List<String> missing = new ArrayList<>();
        for (var e : mapping.entrySet()) {
            String vanilla = e.getValue().getAsString();
            String block = vanilla.substring("minecraft:".length(), vanilla.indexOf('['));
            String props = vanilla.substring(vanilla.indexOf('[') + 1, vanilla.length() - 1);
            String blockstate = Files.readString(packSrc.resolve("resourcepack/assets/minecraft/blockstates/" + block + ".json"));
            var json = com.google.gson.JsonParser.parseString(blockstate).getAsJsonObject();
            String id = e.getKey().replaceFirst("\\[.*", "").substring("the_sift:".length());
            boolean found;
            if (json.has("variants")) {
                var variant = json.getAsJsonObject("variants").get(props);
                found = variant != null && variant.toString().contains("the_sift:block/");
            } else {
                found = blockstate.contains("the_sift:block/" + id);
            }
            if (!found) {
                missing.add(e.getKey());
            }
        }
        assertTrue(missing.isEmpty(), "no override for " + missing);
    }
}
