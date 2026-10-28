package de.lamamc.sift.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiftWorldConfigTest {

    @TempDir
    Path tmp;

    @Test
    void createsTheFileWithAllSwitches() throws IOException {
        Path file = SiftWorldConfig.configFile(tmp.resolve("world"));
        assertEquals(SiftWorldConfig.REQUIRED_SWITCHES, SiftWorldConfig.ensureSwitches(file));
        assertTrue(SiftWorldConfig.missingSwitches(file).isEmpty());
        assertTrue(file.endsWith(Path.of("world", "dimensions", "the_sift", "the_sift", "paper-world.yml")));
    }

    @Test
    void keepsOtherSettingsAndOnlyAddsMissingSwitches() throws IOException {
        Path file = tmp.resolve("paper-world.yml");
        Files.writeString(file, """
                _version: 31
                block-updates:
                  disable-tripwire-updates: true
                  disable-noteblock-updates: false
                entities:
                  spawning:
                    monster-spawn-max-light-level: default
                """);
        List<String> changed = SiftWorldConfig.ensureSwitches(file);
        assertEquals(List.of("block-updates.disable-mushroom-block-updates",
                "block-updates.disable-chorus-plant-updates"), changed);
        String text = Files.readString(file);
        assertTrue(text.contains("_version: 31"));
        assertTrue(text.contains("monster-spawn-max-light-level: default"));
        assertTrue(text.contains("disable-noteblock-updates: false"));
        assertTrue(SiftWorldConfig.ensureSwitches(file).isEmpty());
    }

    @Test
    void worldFolderComesFromServerProperties() throws IOException {
        Files.writeString(tmp.resolve("server.properties"), "level-name=lama\n");
        assertEquals(tmp.resolve("lama"), SiftWorldConfig.worldFolder(tmp, ""));
        assertEquals(tmp.resolve("other"), SiftWorldConfig.worldFolder(tmp, "other"));
        Path noProperties = tmp.resolve("missing");
        assertEquals(noProperties.resolve("world"), SiftWorldConfig.worldFolder(noProperties, null));
    }
}
