package de.lamamc.sift.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.block.MappingEntry;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemRegistryTest {

    private static ItemRegistry bundled() {
        return ItemRegistry.loadFromJar(MappingEntry.bundled());
    }

    @Test
    void everyCustomBlockIsAnItemOnce() {
        ItemRegistry registry = bundled();
        for (MappingEntry entry : MappingEntry.bundled()) {
            if (!entry.id().equals("sculkflower_crop")) { // a block without item, see items.yml
                assertNotNull(registry.get(entry.id()), entry.id());
            }
        }
        long distinct = registry.all().stream().map(ItemDefinition::id).distinct().count();
        assertEquals(registry.all().size(), distinct);
    }

    @Test
    void blocksAreSortedIntoCategories() {
        ItemRegistry registry = bundled();
        assertEquals(ItemCategory.BLOCKS, registry.get("siftslate").category());
        assertEquals(ItemCategory.ORES, registry.get("siftslate_siftite_ore").category());
        assertEquals(ItemCategory.PLANTS_WOOD, registry.get("overgrown_fronds").category());
        assertEquals(ItemCategory.PLANTS_WOOD, registry.get("overgrown_willow_sapling").category());
        assertEquals(ItemCategory.BLOCKS, registry.get("ichor_glass").category());
        assertEquals("minecraft:string", registry.get("overgrown_fronds").baseItem());
        assertEquals("minecraft:glass", registry.get("ichor_glass").baseItem());
        assertEquals("minecraft:mushroom_stem", registry.get("the_sift:siftslate").baseItem());
        assertEquals(5, registry.inCategory(ItemCategory.ORES).size());
    }

    @Test
    void itemsYamlAddsMaterials() {
        ItemRegistry registry = bundled();
        assertEquals(ItemCategory.MATERIALS, registry.get("charoite").category());
        assertEquals("minecraft:snowball", registry.get("ichor_snowball").baseItem());
        registry.all().forEach(def -> assertTrue(def.baseItem().startsWith("minecraft:"), def.toString()));
    }

    @Test
    void anItemCannotShadowABlock() {
        List<MappingEntry> blocks = List.of(MappingEntry.parse("the_sift:siftslate", "minecraft:mushroom_stem[down=false]"));
        assertThrows(IllegalArgumentException.class, () -> new ItemRegistry(blocks,
                new StringReader("items:\n  siftslate: {category: misc, base: \"minecraft:paper\"}\n")));
    }

    @Test
    void everySiftItemIncludingBlockItemsIsProtected() throws java.io.IOException {
        ItemRegistry registry = bundled();
        java.nio.file.Path tag = java.nio.file.Path.of(System.getProperty("sift.packSrc", "../pack-src"),
                "item-data", "tags", "item", "siftite_items.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(tag), "pack-src not generated");
        java.util.Set<String> expected = new java.util.TreeSet<>();
        com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(tag)).getAsJsonObject()
                .getAsJsonArray("values").forEach(v -> expected.add(v.getAsString().substring("the_sift:".length())));
        // Siftite (original tag) plus every Sift material, item of items.yml and block item
        registry.all().stream().map(ItemDefinition::id).forEach(expected::add);
        assertEquals(expected, registry.protectedIds());
        assertEquals(registry.all().size(), registry.protectedIds().size());
        for (String material : List.of("charoite", "ichor_shard", "siftite_nugget", "sculkflower_seeds", "ichor_snowball")) {
            assertTrue(registry.get(material).protectedItem(), material);
        }
        for (String block : List.of("siftslate", "ichor_glass", "overgrown_fronds", "overgrown_willow_planks", "siftslate_siftite_ore")) {
            assertTrue(registry.get(block).protectedItem(), block);
        }
        assertEquals(ItemKind.SIFTITE_TOOL, registry.get("siftite_spear").kind());
        assertEquals(ItemKind.SIFTITE_ARMOR, registry.get("siftite_boots").kind());
        assertEquals("minecraft:netherite_boots", registry.get("siftite_boots").baseItem());
        assertEquals(ItemKind.PLAIN, registry.get("charoite").kind());
    }

    /** Every food, drink and disc of items.yml has its values in the bundled config.yml. */
    @Test
    void configHasValuesForEveryItemKind() throws java.io.IOException {
        ItemSettings settings;
        try (var in = ItemRegistryTest.class.getResourceAsStream("/config.yml")) {
            settings = ItemSettings.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getConfigurationSection("items"));
        }
        for (ItemDefinition def : bundled().all()) {
            switch (def.kind()) {
                case FOOD -> assertNotNull(settings.food().get(def.id()), def.id());
                case DRINK -> assertNotNull(settings.drinks().get(def.id()), def.id());
                case MUSIC_DISC -> assertNotNull(settings.musicDiscs().get(def.id()), def.id());
                default -> {
                }
            }
        }
        assertEquals(de.lamamc.sift.core.SiftiteStats.defaults(), settings.siftite());
        assertEquals(new ItemSettings.Food(8, 12.8f), settings.food().get("cooked_sifter_meat"));
        assertEquals(java.util.List.of(new ItemSettings.Effect("regeneration", 60, 0)),
                settings.drinks().get("ichor_bottle").effects());
    }

    @Test
    void everyItemHasAnItemModelInTheResourcePack() {
        java.nio.file.Path items = java.nio.file.Path.of(System.getProperty("sift.packSrc", "../pack-src"),
                "resourcepack", "assets", "the_sift", "items");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(items), "pack-src not generated");
        for (ItemDefinition def : bundled().all()) {
            assertTrue(java.nio.file.Files.isRegularFile(items.resolve(def.id() + ".json")), "no item model for " + def.id());
        }
        assertEquals(null, bundled().get("sculkflower_crop"));
    }
}
