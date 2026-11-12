package de.lamamc.sift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.lang.Lang;
import de.lamamc.sift.core.lang.LangCheck;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** alpha.42c: player texts in German and English. */
class MessagesLangTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(MessagesLangTest.class.getResourceAsStream(name),
                StandardCharsets.UTF_8));
    }

    private static Map<String, String> strings(YamlConfiguration yaml) {
        Map<String, String> out = new TreeMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                out.put(key, yaml.getString(key));
            }
        }
        return out;
    }

    @Test
    void bothMessageFilesHaveEveryKeyWithTheSamePlaceholders() {
        Map<String, String> de = strings(resource("/messages.yml"));
        Map<String, String> en = strings(resource("/messages_en.yml"));
        assertTrue(de.size() > 100, "keys: " + de.size());
        List<String> problems = LangCheck.compare("messages.yml", de, "messages_en.yml", en);
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void germanClientsGetGermanEveryoneElseEnglish() {
        assertEquals(Lang.DE, Lang.of("de_de"));
        assertEquals(Lang.DE, Lang.of("de_AT"));
        assertEquals(Lang.DE, Lang.of("de-CH"));
        assertEquals(Lang.EN, Lang.of("en_us"));
        assertEquals(Lang.EN, Lang.of("fr_fr"));
        assertEquals(Lang.EN, Lang.of(null));
        assertEquals(Lang.DE, Lang.ofSetting("de"));
        assertEquals(Lang.EN, Lang.ofSetting("EN"));
        assertEquals(Lang.DE, Lang.ofSetting("auto"));
    }

    @Test
    void bundledTextsRenderInBothLanguages() {
        Messages m = Messages.bundled();
        assertEquals("Ichor und Wasser mischen sich nicht.", m.raw(Lang.DE, "ichor.no-mix"));
        assertEquals("Ichor and water do not mix.", m.raw(Lang.EN, "ichor.no-mix"));
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(m.get(Lang.EN, "compass.temple", "distance", "300"));
        assertEquals("Temple: 300 blocks", plain);
    }

    /** Old German defaults become de/en sections; an admin's own text stays for every language. */
    @Test
    void oldGermanConfigTextsBecomeBilingual() {
        YamlConfiguration defaults = resource("/config.yml");
        YamlConfiguration server = new YamlConfiguration();
        YamlConfiguration old = resource("/migrations/alpha42c.yml");
        server.set("resource-pack.prompt", old.getString("resource-pack.prompt"));
        server.set("resource-pack.kick-on-decline", "<red>Eigener Text");
        server.set("phase4.temples.hints.text", old.getStringList("phase4.temples.hints.text"));
        List<String> dropped = ConfigUpdate.dropOldDefaults(server, old);
        assertTrue(dropped.contains("resource-pack.prompt"));
        assertTrue(dropped.contains("phase4.temples.hints.text"));
        assertFalse(dropped.contains("resource-pack.kick-on-decline"));
        ConfigUpdate.addMissing(server, defaults);
        var prompt = LocalizedConfig.text(server.getConfigurationSection("resource-pack"), "prompt", "", "");
        assertTrue(prompt.get(Lang.EN).contains("needs a resource pack"), prompt.toString());
        assertTrue(prompt.get(Lang.DE).contains("braucht ein Resource Pack"));
        var kick = LocalizedConfig.text(server.getConfigurationSection("resource-pack"), "kick-on-decline", "", "");
        assertEquals("<red>Eigener Text", kick.get(Lang.EN), "own text for everyone");
        assertEquals("<red>Eigener Text", kick.get(Lang.DE));
        var lines = LocalizedConfig.lines(server.getConfigurationSection("phase4.temples.hints"), "text");
        assertTrue(lines.get(Lang.EN).get(1).contains("sleeping temple"));
        assertTrue(lines.get(Lang.DE).get(1).contains("schlafender Tempel"));
        assertEquals("en", Lang.EN.code());
        assertTrue(new MemoryConfiguration().getKeys(false).isEmpty());
    }
}
