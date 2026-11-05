package de.lamamc.sift.core.atmosphere;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/**
 * Clouds of the Sift (alpha.29). The original draws its own sky and shows clouds only while it rains; a
 * vanilla client draws clouds whenever the cloud colour of the dimension has an alpha above 0. Setting the
 * alpha of every {@code minecraft:visual/cloud_color} to 0 (in the dimension type and in the day/night
 * timeline) hides them completely.
 */
public final class CloudSwitch {

    public static final String ATTRIBUTE = "minecraft:visual/cloud_color";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CloudSwitch() {
    }

    /** "#AARRGGBB" (or "#RRGGBB") with alpha 0. */
    public static String transparent(String color) {
        String hex = color.startsWith("#") ? color.substring(1) : color;
        return "#00" + (hex.length() == 8 ? hex.substring(2) : hex);
    }

    /** The JSON with every cloud colour made transparent; returns the input unchanged if it has none. */
    public static String hide(String json) {
        JsonElement root = JsonParser.parseString(json);
        return hide(root, false) ? GSON.toJson(root) : json;
    }

    private static boolean hide(JsonElement element, boolean inCloudColor) {
        boolean changed = false;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : object.entrySet()) {
                boolean cloud = inCloudColor || ATTRIBUTE.equals(e.getKey());
                JsonElement value = e.getValue();
                if (cloud && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && value.getAsString().startsWith("#") && (ATTRIBUTE.equals(e.getKey()) || "value".equals(e.getKey()))) {
                    e.setValue(new JsonPrimitive(transparent(value.getAsString())));
                    changed = true;
                } else {
                    changed |= hide(value, cloud);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                changed |= hide(child, inCloudColor);
            }
        }
        return changed;
    }
}
