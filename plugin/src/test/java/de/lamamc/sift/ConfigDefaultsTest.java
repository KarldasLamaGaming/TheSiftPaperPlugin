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
    void testPopulatorIsGoneAndWorldgenIsOn() throws Exception {
        YamlConfiguration config = shipped();
        assertFalse(config.contains("debug.populator-marker"));
        assertTrue(config.getBoolean("worldgen.willow-trees.enabled"));
        assertTrue(config.getBoolean("worldgen.main-portal.enabled"));
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
