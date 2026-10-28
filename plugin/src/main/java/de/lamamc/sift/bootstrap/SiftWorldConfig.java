package de.lamamc.sift.bootstrap;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The Paper world config of the Sift dimension. Custom blocks use mushroom block, tripwire and chorus plant
 * states that vanilla would change on neighbor updates, so the Sift world turns those updates off. Only
 * that world: elsewhere the switches would break tripwire traps and chorus farms.
 */
public final class SiftWorldConfig {

    /** Keys of Paper's WorldConfiguration.BlockUpdates that must be true in the Sift world. */
    public static final List<String> REQUIRED_SWITCHES = List.of(
            "block-updates.disable-mushroom-block-updates",
            "block-updates.disable-tripwire-updates",
            "block-updates.disable-chorus-plant-updates");

    private SiftWorldConfig() {
    }

    /** {@code <world folder>/dimensions/the_sift/the_sift/paper-world.yml} */
    public static Path configFile(Path worldFolder) {
        return worldFolder.resolve("dimensions").resolve("the_sift").resolve("the_sift").resolve("paper-world.yml");
    }

    /** The main world folder: the configured one, else level-name from server.properties, else "world". */
    public static Path worldFolder(Path serverRoot, String configured) throws IOException {
        if (configured != null && !configured.isBlank()) {
            return serverRoot.resolve(configured);
        }
        Properties props = new Properties();
        Path file = serverRoot.resolve("server.properties");
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                props.load(reader);
            }
        }
        return serverRoot.resolve(props.getProperty("level-name", "world"));
    }

    /** The switches that are not set to true in {@code file} (all of them if it does not exist). */
    public static List<String> missingSwitches(Path file) throws IOException {
        YamlConfiguration yaml = load(file);
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_SWITCHES) {
            if (!yaml.getBoolean(key, false)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** Sets the missing switches, keeping everything else in the file; returns what was changed. */
    public static List<String> ensureSwitches(Path file) throws IOException {
        YamlConfiguration yaml = load(file);
        List<String> changed = new ArrayList<>();
        for (String key : REQUIRED_SWITCHES) {
            if (!yaml.getBoolean(key, false)) {
                yaml.set(key, true);
                changed.add(key);
            }
        }
        if (!changed.isEmpty()) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, yaml.saveToString());
        }
        return changed;
    }

    private static YamlConfiguration load(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (Files.isRegularFile(file)) {
            try {
                yaml.loadFromString(Files.readString(file));
            } catch (InvalidConfigurationException e) {
                throw new IOException("Invalid YAML in " + file, e);
            }
        }
        return yaml;
    }
}
