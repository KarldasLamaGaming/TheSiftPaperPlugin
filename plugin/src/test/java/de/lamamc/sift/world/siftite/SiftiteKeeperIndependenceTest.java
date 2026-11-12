package de.lamamc.sift.world.siftite;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * alpha.42a.2: rifts are off by default; the Siftite keeper (keep on death and its mini rift) must work exactly as
 * before. Its compiled classes must not refer to anything of the rift system, and its own switch stays on.
 */
class SiftiteKeeperIndependenceTest {

    @Test
    void theKeeperDoesNotUseTheRiftSystem() throws Exception {
        Path dir = Path.of(SiftiteKeeper.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("de/lamamc/sift/world/siftite");
        int classes = 0;
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().startsWith("SiftiteKeeper")).toList()) {
                String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("de/lamamc/sift/world/rift"), file + " refers to the rift system");
                assertFalse(bytes.contains("rifts."), file + " reads a rifts.* setting");
                classes++;
            }
        }
        assertTrue(classes >= 1);
    }

    @Test
    void riftsAreOffAndTheKeeperIsOnByDefault() throws Exception {
        YamlConfiguration config;
        try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        assertFalse(config.getBoolean("rifts.enabled", true));
        assertTrue(config.getBoolean("siftite.keep-on-death.enabled", false));
        assertTrue(config.getBoolean("siftite.keep-on-death.particles", false));
        // the mini rift's look is its own item model in the pack, not the rift's
        Path pack = Path.of(System.getProperty("sift.packSrc", "../pack-src"));
        assertTrue(Files.isRegularFile(pack.resolve("resourcepack/assets/the_sift/items/mini_rift.json"))
                || Files.isRegularFile(pack.resolve("resourcepack-overlay/assets/the_sift/items/mini_rift.json")), "mini_rift model");
        assertNotNull(config.getConfigurationSection("portals.unlock-schedule"));
    }
}
