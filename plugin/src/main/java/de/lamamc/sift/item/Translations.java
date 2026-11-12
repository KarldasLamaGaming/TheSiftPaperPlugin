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
 * Names of Sift items as translatable components. The resource pack provides the translations (de_de, en_us); since
 * alpha.42c the English text is the fallback for players without the pack (before: German).
 */
public final class Translations {

    private final Map<String, String> english = new HashMap<>();
    private final Map<String, String> german = new HashMap<>();

    public static Translations loadFromJar() {
        Translations translations = new Translations();
        read("/lang/en_us.json", translations.english);
        read("/lang/lamakingdom_en_us.json", translations.english);
        read("/lang/de_de.json", translations.german);
        return translations;
    }

    /** Reads a lang file from the jar into {@code into} (later files win). */
    public static void read(String path, Map<String, String> into) {
        try (InputStream in = Translations.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e -> into.put(e.getKey(), e.getValue().getAsString()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code item.the_sift.<id>}, else {@code block.the_sift.<id>}, with the English name as fallback. */
    public Component itemName(String id) {
        String key = "item.the_sift." + id;
        if (!english.containsKey(key) && english.containsKey("block.the_sift." + id)) {
            key = "block.the_sift." + id;
        }
        return Component.translatable().key(key).fallback(english.getOrDefault(key, german.getOrDefault(key, id))).build();
    }

    /** Fallback text of a key: English, else German, else null. */
    public String fallback(String key) {
        return english.getOrDefault(key, german.get(key));
    }

    public Map<String, String> english() {
        return Map.copyOf(english);
    }

    public Map<String, String> german() {
        return Map.copyOf(german);
    }
}
