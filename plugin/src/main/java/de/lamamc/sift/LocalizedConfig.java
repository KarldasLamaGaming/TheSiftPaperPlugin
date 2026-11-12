package de.lamamc.sift;

import de.lamamc.sift.core.lang.Lang;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Player texts in config.yml in both languages (alpha.42c): a value is either a section {@code {de: …, en: …}} or, as
 * before, one text for everyone (an admin's own text from an older config stays valid). A missing language falls back to
 * the other one, then to the given default.
 */
public final class LocalizedConfig {

    private LocalizedConfig() {
    }

    public static Map<Lang, String> text(ConfigurationSection config, String path, String defaultDe, String defaultEn) {
        Map<Lang, String> out = new EnumMap<>(Lang.class);
        if (config != null && config.isConfigurationSection(path)) {
            ConfigurationSection s = config.getConfigurationSection(path);
            String de = s.getString("de");
            String en = s.getString("en");
            out.put(Lang.DE, de != null ? de : en != null ? en : defaultDe);
            out.put(Lang.EN, en != null ? en : de != null ? de : defaultEn);
        } else if (config != null && config.isString(path)) {
            out.put(Lang.DE, config.getString(path));
            out.put(Lang.EN, config.getString(path));
        } else {
            out.put(Lang.DE, defaultDe);
            out.put(Lang.EN, defaultEn);
        }
        return out;
    }

    public static Map<Lang, List<String>> lines(ConfigurationSection config, String path) {
        Map<Lang, List<String>> out = new EnumMap<>(Lang.class);
        if (config != null && config.isConfigurationSection(path)) {
            ConfigurationSection s = config.getConfigurationSection(path);
            List<String> de = s.getStringList("de");
            List<String> en = s.getStringList("en");
            out.put(Lang.DE, de.isEmpty() ? en : de);
            out.put(Lang.EN, en.isEmpty() ? de : en);
        } else {
            List<String> both = config == null ? List.of() : config.getStringList(path);
            out.put(Lang.DE, both);
            out.put(Lang.EN, both);
        }
        return out;
    }
}
