package de.lamamc.sift;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-facing texts from messages.yml (German, MiniMessage). New keys of an update are added to the
 * server's file; missing keys fall back to the bundled text. Placeholders are given as name/value pairs
 * and inserted as plain text.
 */
public final class Messages {

    private final YamlConfiguration messages;
    private final YamlConfiguration defaults;

    private Messages(YamlConfiguration messages, YamlConfiguration defaults) {
        this.messages = messages;
        this.defaults = defaults;
    }

    public static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration defaults = bundled(plugin.getLogger());
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(file);
        messages.setDefaults(defaults);
        messages.options().copyDefaults(true);
        try {
            messages.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not update " + file + ": " + e.getMessage());
        }
        return new Messages(messages, defaults);
    }

    /** Only the bundled texts (tests, or before the plugin is enabled). */
    public static Messages bundled() {
        YamlConfiguration defaults = bundled(Logger.getLogger("TheSift"));
        return new Messages(defaults, defaults);
    }

    private static YamlConfiguration bundled(Logger logger) {
        try (InputStream in = Messages.class.getResourceAsStream("/messages.yml")) {
            if (in == null) {
                logger.warning("messages.yml missing in the plugin jar");
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null) {
            value = defaults.getString(key, "<red>" + key);
        }
        return value;
    }

    public boolean has(String key) {
        return defaults.isString(key) || messages.isString(key);
    }

    public Component get(String key, String... placeholders) {
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolver.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return MiniMessage.miniMessage().deserialize(raw(key), resolver.build());
    }
}
