package de.lamamc.sift;

import de.lamamc.sift.core.lang.Lang;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-facing texts in German and English (MiniMessage, alpha.42c): {@code messages.yml} (German, the file servers
 * already have) and {@code messages_en.yml} (English). Each player gets the language of their client ({@link Lang#of},
 * German for de_*, English for everything else); the console and other senders get {@code language} from config.yml.
 * New keys of an update are added to the server's files; missing keys fall back to the bundled text. Placeholders are
 * name/value pairs inserted as plain text; components (e.g. an item name) go in as {@link TagResolver}s.
 */
public final class Messages {

    private static final Map<Lang, String> FILES = Map.of(Lang.DE, "messages.yml", Lang.EN, "messages_en.yml");
    private static Messages current;

    private final Map<Lang, YamlConfiguration> texts;
    private final Map<Lang, YamlConfiguration> defaults;
    private Lang console = Lang.DE;

    private Messages(Map<Lang, YamlConfiguration> texts, Map<Lang, YamlConfiguration> defaults) {
        this.texts = texts;
        this.defaults = defaults;
    }

    /** The texts of the running plugin (bundled texts before it is enabled and in tests). */
    public static Messages current() {
        if (current == null) {
            current = bundled();
        }
        return current;
    }

    public static Messages load(JavaPlugin plugin) {
        Map<Lang, YamlConfiguration> texts = new EnumMap<>(Lang.class);
        Map<Lang, YamlConfiguration> defaults = new EnumMap<>(Lang.class);
        for (Lang lang : Lang.values()) {
            File file = new File(plugin.getDataFolder(), FILES.get(lang));
            if (!file.isFile()) {
                plugin.saveResource(FILES.get(lang), false);
            }
            YamlConfiguration bundled = bundled(FILES.get(lang), plugin.getLogger());
            YamlConfiguration own = YamlConfiguration.loadConfiguration(file);
            own.setDefaults(bundled);
            own.options().copyDefaults(true);
            try {
                own.save(file);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not update " + file + ": " + e.getMessage());
            }
            texts.put(lang, own);
            defaults.put(lang, bundled);
        }
        current = new Messages(texts, defaults);
        return current;
    }

    /** Only the bundled texts (tests, or before the plugin is enabled). */
    public static Messages bundled() {
        Map<Lang, YamlConfiguration> defaults = new EnumMap<>(Lang.class);
        for (Lang lang : Lang.values()) {
            defaults.put(lang, bundled(FILES.get(lang), Logger.getLogger("TheSift")));
        }
        return new Messages(defaults, defaults);
    }

    private static YamlConfiguration bundled(String name, Logger logger) {
        try (InputStream in = Messages.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                logger.warning(name + " missing in the plugin jar");
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Language of the console and other non-player senders (config.yml language). */
    public void console(Lang lang) {
        this.console = lang;
    }

    public Lang console() {
        return console;
    }

    public Lang lang(CommandSender sender) {
        return sender instanceof Player player ? Lang.of(player.locale().toString()) : console;
    }

    public String raw(Lang lang, String key) {
        String value = texts.get(lang).getString(key);
        if (value == null) {
            value = defaults.get(lang).getString(key);
        }
        if (value == null && lang != Lang.EN) {
            value = raw(Lang.EN, key);
        }
        return value != null ? value : "<red>" + key;
    }

    /** German text, for the console (old callers). */
    public String raw(String key) {
        return raw(console, key);
    }

    public boolean has(String key) {
        return defaults.get(Lang.EN).isString(key) || texts.get(Lang.DE).isString(key);
    }

    public Component get(Lang lang, String key, String... placeholders) {
        return get(lang, key, TagResolver.empty(), placeholders);
    }

    public Component get(Lang lang, String key, TagResolver components, String... placeholders) {
        TagResolver.Builder resolver = TagResolver.builder().resolver(components);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolver.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return MiniMessage.miniMessage().deserialize(raw(lang, key), resolver.build());
    }

    /** The text for this recipient (its client language, the console's setting otherwise). */
    public Component get(CommandSender to, String key, String... placeholders) {
        return get(lang(to), key, placeholders);
    }

    public Component get(CommandSender to, String key, TagResolver components, String... placeholders) {
        return get(lang(to), key, components, placeholders);
    }

    /** Console language (old callers without a recipient). */
    public Component get(String key, String... placeholders) {
        return get(console, key, placeholders);
    }

    public void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(get(to, key, placeholders));
    }

    public void actionBar(Player to, String key, String... placeholders) {
        to.sendActionBar(get(to, key, placeholders));
    }

    /** To every player in their language and to the console. */
    public void broadcast(String key, String... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(get(player, key, placeholders));
        }
        Bukkit.getConsoleSender().sendMessage(get(console, key, placeholders));
    }

    /**
     * The texts in use, per language (for the language check): every key the plugin knows (bundled in either language),
     * with the server's text. Keys left over from older versions in a server's file are not counted.
     */
    public Map<String, String> all(Lang lang) {
        java.util.Set<String> known = new java.util.TreeSet<>();
        for (ConfigurationSection source : defaults.values()) {
            for (String key : source.getKeys(true)) {
                if (source.isString(key)) {
                    known.add(key);
                }
            }
        }
        Map<String, String> out = new TreeMap<>();
        for (String key : known) {
            String value = texts.get(lang).getString(key, defaults.get(lang).getString(key));
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    /** Remaining time in this language (SiftAccess#remaining). */
    public String duration(Lang lang, long millis) {
        return de.lamamc.sift.core.access.SiftAccess.remaining(millis, lang);
    }
}
