package de.lamamc.sift;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Defaults that must not change by accident in the shipped config.yml. */
class ConfigDefaultsTest {

    private static YamlConfiguration shipped() throws Exception {
        try (var in = ConfigDefaultsTest.class.getResourceAsStream("/config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void testPopulatorIsOff() throws Exception {
        YamlConfiguration config = shipped();
        assertTrue(config.isBoolean("debug.populator-marker"));
        assertFalse(config.getBoolean("debug.populator-marker"));
    }

    @Test
    void resourcePackIsOfferedButNotRequired() throws Exception {
        YamlConfiguration config = shipped();
        assertTrue(config.getBoolean("resource-pack.enabled"));
        assertTrue(config.isBoolean("resource-pack.required"));
        assertFalse(config.getBoolean("resource-pack.required"));
    }

    @Test
    void customBlocksStayInTheSift() throws Exception {
        assertTrue(shipped().getBoolean("custom-blocks.place-only-in-sift"));
    }
}
