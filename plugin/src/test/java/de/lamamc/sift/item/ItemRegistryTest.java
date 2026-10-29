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
            assertNotNull(registry.get(entry.id()), entry.id());
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
}
