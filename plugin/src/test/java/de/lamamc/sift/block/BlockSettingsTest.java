package de.lamamc.sift.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.DropSpec;
import de.lamamc.sift.core.ToolTier;
import de.lamamc.sift.core.ToolType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BlockSettingsTest {

    private static final Logger LOG = Logger.getLogger("test");
    /** Items that blocks may drop; must match SiftItems.MATERIALS plus the block ids. */
    private static final List<String> MATERIALS = List.of("charoite", "siftite_nugget", "ichor_shard",
            "ichor_snowball", "sculkflower_seeds");

    private static YamlConfiguration defaults() throws IOException {
        try (var in = BlockSettingsTest.class.getResourceAsStream("/config.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyCustomBlockHasAProfileAndKnownDrops() throws IOException {
        YamlConfiguration config = defaults();
        List<MappingEntry> entries = MappingEntryTest.bundled();
        for (MappingEntry entry : entries) {
            assertNotNull(config.getConfigurationSection("blocks." + entry.id()), "no config for " + entry.id());
        }
        BlockSettings settings = BlockSettings.load(config, LOG);
        for (MappingEntry entry : entries) {
            String item = settings.profile(entry.id()).drops().item();
            if (item != null && item.startsWith("the_sift:")) {
                String id = item.substring("the_sift:".length());
                boolean known = MATERIALS.contains(id) || entries.stream().anyMatch(e -> e.id().equals(id));
                assertTrue(known, entry.id() + " drops unknown " + item);
            }
        }
    }

    @Test
    void parsesOreProfile() throws IOException {
        BlockSettings settings = BlockSettings.load(defaults(), LOG);
        BlockProfile ore = settings.profile("siftslate_siftite_ore");
        assertEquals(4.5, ore.hardness());
        assertEquals(ToolType.PICKAXE, ore.tool());
        assertEquals(ToolTier.DIAMOND, ore.minTier());
        assertEquals(DropSpec.Kind.ORE, ore.drops().kind());
        assertEquals("the_sift:siftite_nugget", ore.drops().item());
        assertEquals(3, ore.xpMin());
        assertEquals(7, ore.xpMax());
        assertTrue(settings.profile("reinforced_siftslate").unbreakable());
        assertEquals(List.of(0.1, 0.14285715, 0.25, 1.0), settings.profile("sift_rubble").drops().chances());
    }

    @Test
    void supportExpandsTheSiftList() throws IOException {
        BlockSettings settings = BlockSettings.load(defaults(), LOG);
        BlockSettings.Support stalks = settings.support("siftslate_stalks");
        assertFalse(stalks.above());
        assertTrue(stalks.blocks().contains("the_sift:healthy_sculk"));
        assertTrue(stalks.blocks().contains("#minecraft:supports_vegetation"));
        assertTrue(settings.support("siftslate_hanging_roots").above());
        assertTrue(settings.placeOnlyInSift());
        assertEquals(BlockSettings.Fallback.DENY, settings.fallback());
    }

    @Test
    void unknownValuesFallBackInsteadOfFailing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("custom-blocks.outside-sift-fallback", "explode");
        config.set("blocks.x.tool", "spoon");
        BlockSettings settings = BlockSettings.load(config, LOG);
        assertEquals(BlockSettings.Fallback.DENY, settings.fallback());
        assertEquals(ToolType.NONE, settings.profile("x").tool());
    }

    /** Reproduces the plugin start with an old config.yml: the defaults only reach the file, not memory. */
    @Test
    void configWithoutBlocksSectionStillGetsTheDefaultsAfterReload() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("resource-pack:\n  url: x\n");
        config.setDefaults(defaults());
        config.options().copyDefaults(true);
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertTrue(BlockSettings.load(reloaded, LOG).profile("reinforced_siftslate").unbreakable());
    }
}
