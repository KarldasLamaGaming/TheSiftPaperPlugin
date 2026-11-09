package de.lamamc.sift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigUpdateTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(ConfigUpdateTest.class.getResourceAsStream(name), StandardCharsets.UTF_8));
    }

    /** A server config as alpha.36 left it: old key recipes and loot chances, one recipe changed by the admin. */
    private static YamlConfiguration alpha36Server() {
        YamlConfiguration server = new YamlConfiguration();
        YamlConfiguration old = resource("/migrations/alpha37.yml");
        for (String path : old.getKeys(true)) {
            if (!old.isConfigurationSection(path)) {
                server.set(path, old.get(path));
            }
        }
        server.set("portals.recipes.soul_ember_core.key.A", "minecraft:netherite_scrap"); // the admin's own change
        server.set("portals.melody.per-portal", true);
        return server;
    }

    @Test
    void oldDefaultsAreReplacedAndOwnChangesStay() {
        YamlConfiguration server = alpha36Server();
        YamlConfiguration defaults = resource("/config.yml");
        List<String> dropped = ConfigUpdate.dropOldDefaults(server, resource("/migrations/alpha37.yml"));
        assertTrue(dropped.containsAll(List.of("portals.recipes.echo_resonator", "portals.recipes.void_resonator",
                "portals.keys.overworld.loot.chance", "portals.keys.end.loot.chance")), dropped.toString());
        assertFalse(dropped.contains("portals.recipes.soul_ember_core"), "changed by the admin");
        ConfigUpdate.addMissing(server, defaults);

        assertEquals("minecraft:heavy_core", server.getString("portals.recipes.echo_resonator.key.H"));
        assertEquals("minecraft:elytra", server.getString("portals.recipes.void_resonator.key.E"));
        assertEquals(0.03, server.getDouble("portals.keys.overworld.loot.chance"), 1e-9);
        assertEquals(0.0, server.getDouble("portals.keys.end.loot.chance"), 1e-9);
        assertEquals(0.01, server.getDouble("portals.keys.ancient-city.loot.chance"), 1e-9);
        assertTrue(server.getBoolean("portals.keys.consume"));
        assertTrue(server.getBoolean("mobs.singer.summon.enabled"));
    }

    @Test
    void anExistingRecipeIsNeverMergedWithTheNewDefault() {
        YamlConfiguration server = alpha36Server();
        ConfigUpdate.dropOldDefaults(server, resource("/migrations/alpha37.yml"));
        ConfigUpdate.addMissing(server, resource("/config.yml"));
        // before alpha.37 copyDefaults added the new keys W and N to the admin's recipe and broke it
        assertEquals(Set.of("B", "G", "A", "S"), server.getConfigurationSection("portals.recipes.soul_ember_core.key").getKeys(false));
        assertEquals("minecraft:netherite_scrap", server.getString("portals.recipes.soul_ember_core.key.A"));
    }
}
