package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class KeyLootTest {

    @Test
    void keysAppearOnlyInTheirChestsAtTheirChance() {
        KeyLoot loot = new KeyLoot();
        loot.add("minecraft:chests/ancient_city", "echo_resonator", 0.08);
        loot.add("minecraft:chests/bastion_treasure", "soul_ember_core", 0.5);
        loot.add("minecraft:chests/end_city_treasure", "void_resonator", 0);
        Random random = new Random(3);
        int found = 0;
        for (int i = 0; i < 20000; i++) {
            List<String> items = loot.roll("minecraft:chests/ancient_city", random);
            found += items.size();
            items.forEach(item -> assertEquals("echo_resonator", item));
        }
        assertEquals(0.08, found / 20000.0, 0.01);
        assertTrue(loot.roll("minecraft:chests/simple_dungeon", random).isEmpty());
        assertTrue(loot.roll("minecraft:chests/end_city_treasure", random).isEmpty()); // chance 0 = off
    }
}
