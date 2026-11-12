package de.lamamc.sift.core.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class LangCheckTest {

    @Test
    void findsMissingKeysAndPlaceholders() {
        var de = Map.of("a", "Hallo %s", "b", "<green><count> × <mob> erschienen.", "c", "nur deutsch");
        var en = Map.of("a", "Hello", "b", "<green><count> × <mob> spawned.", "d", "only English");
        assertEquals(List.of("a: Platzhalter [%1$] (de) ≠ [] (en)", "c: fehlt in en", "d: fehlt in de"),
                LangCheck.compare("de", de, "en", en));
        assertEquals(List.of("%1$", "%2$"), LangCheck.placeholders("%s und %s"));
        assertEquals(List.of("<time>"), LangCheck.placeholders("<red>geschlossen <gray>(öffnet in <time>)"));
    }

    /** The resource pack: German (overlay) against English (original + own keys), every key and placeholder in both. */
    @Test
    void packLanguagesMatch() throws IOException {
        Path pack = Path.of(System.getProperty("sift.packSrc", "../pack-src"));
        Map<String, String> de = read(pack.resolve("resourcepack-overlay/assets/the_sift/lang/de_de.json"));
        Map<String, String> en = read(pack.resolve("resourcepack/assets/the_sift/lang/en_us.json"));
        en.putAll(read(pack.resolve("resourcepack-overlay/assets/lamakingdom/lang/en_us.json")));
        List<String> problems = LangCheck.compare("de_de", de, "en_us", en);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static Map<String, String> read(Path file) throws IOException {
        Map<String, String> out = new TreeMap<>();
        JsonParser.parseString(Files.readString(file)).getAsJsonObject().entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        return out;
    }
}
