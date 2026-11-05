package de.lamamc.sift.core.atmosphere;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CloudSwitchTest {

    @Test
    void alphaBecomesZero() {
        assertEquals("#00ffffff", CloudSwitch.transparent("#ffffffff"));
        assertEquals("#006e8fc4", CloudSwitch.transparent("#ff6e8fc4"));
        assertEquals("#00d6e8ff", CloudSwitch.transparent("#d6e8ff"));
    }

    @Test
    void onlyCloudColoursChange() {
        String json = "{\"attributes\":{\"minecraft:visual/cloud_color\":\"#ffffffff\",\"minecraft:visual/sky_color\":\"#ff123456\"},"
                + "\"tracks\":{\"minecraft:visual/cloud_color\":{\"keyframes\":[{\"ticks\":0,\"value\":\"#fffff0ef\"}]},"
                + "\"minecraft:visual/fog_color\":{\"keyframes\":[{\"ticks\":0,\"value\":\"#ffabcdef\"}]}}}";
        String hidden = CloudSwitch.hide(json);
        assertTrue(hidden.contains("\"#00ffffff\""));
        assertTrue(hidden.contains("\"#00fff0ef\""));
        assertTrue(hidden.contains("\"#ff123456\""), "sky colour stays");
        assertTrue(hidden.contains("\"#ffabcdef\""), "fog keyframes stay");
        assertEquals("{\"a\":1}", CloudSwitch.hide("{\"a\":1}"), "untouched without clouds");
    }

    @Test
    void shippedDatapackHasNoVisibleCloudsAfterwards() throws Exception {
        for (String file : new String[] {"dimension_type/the_sift.json", "timeline/sift_day_night.json"}) {
            Path path = Path.of("../pack-src/datapack/data/the_sift").resolve(file);
            java.util.List<String> colours = new java.util.ArrayList<>();
            collect(com.google.gson.JsonParser.parseString(CloudSwitch.hide(Files.readString(path))), false, colours);
            assertFalse(colours.isEmpty(), file + " has cloud colours");
            assertTrue(colours.stream().allMatch(c -> c.startsWith("#00")), file + ": " + colours);
        }
    }

    private static void collect(com.google.gson.JsonElement e, boolean cloud, java.util.List<String> out) {
        if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(en -> collect(en.getValue(), cloud || CloudSwitch.ATTRIBUTE.equals(en.getKey()), out));
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(c -> collect(c, cloud, out));
        } else if (cloud && e.isJsonPrimitive() && e.getAsString().startsWith("#")) {
            out.add(e.getAsString());
        }
    }
}
