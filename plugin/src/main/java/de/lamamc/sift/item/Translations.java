package de.lamamc.sift.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;

/**
 * Names of Sift items as translatable components. The resource pack provides the translations; the
 * German text from the same lang file is the fallback for players without the pack.
 */
public final class Translations {

    private final Map<String, String> german = new HashMap<>();

    public static Translations loadFromJar() {
        Translations translations = new Translations();
        try (InputStream in = Translations.class.getResourceAsStream("/lang/de_de.json")) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e -> translations.german.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return translations;
    }

    /** {@code item.the_sift.<id>}, else {@code block.the_sift.<id>}, with the German name as fallback. */
    public Component itemName(String id) {
        String key = "item.the_sift." + id;
        if (!german.containsKey(key) && german.containsKey("block.the_sift." + id)) {
            key = "block.the_sift." + id;
        }
        return Component.translatable().key(key).fallback(german.getOrDefault(key, id)).build();
    }

    public String german(String key) {
        return german.get(key);
    }
}
