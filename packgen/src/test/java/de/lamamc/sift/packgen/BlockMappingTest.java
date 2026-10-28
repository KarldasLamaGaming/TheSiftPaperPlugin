package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockMappingTest {

    @Test
    void defaultMappingGivesEveryRepurposedStateItsOwnVanillaState() {
        BlockMapping mapping = BlockMapping.loadDefault();
        Set<BlockState> used = new HashSet<>();
        mapping.repurposed().values().forEach(entries -> entries.forEach(e ->
                assertTrue(used.add(e.vanilla()), "state used twice: " + e.vanilla())));
        assertTrue(used.size() > 30);
    }

    @Test
    void vanillaStatesOfMushroomBlocksAreNeverUsed() {
        BlockMapping mapping = BlockMapping.loadDefault();
        BlockState placedStem = BlockState.parse(
                "mushroom_stem[up=true,down=true,north=true,east=true,south=true,west=true]", "minecraft");
        BlockState hugeStem = BlockState.parse(
                "mushroom_stem[up=false,down=false,north=true,east=true,south=true,west=true]", "minecraft");
        mapping.repurposed().values().forEach(entries -> entries.forEach(e -> {
            assertNotEquals(placedStem, e.vanilla());
            assertNotEquals(hugeStem, e.vanilla());
        }));
    }

    @Test
    void resolvePicksTheEntryMatchingTheProperties() {
        BlockMapping mapping = BlockMapping.loadDefault();
        BlockState dry = mapping.resolve(BlockState.of("the_sift:siftslate_growth", Map.of("snowy", "false")));
        BlockState snowy = mapping.resolve(BlockState.of("the_sift:siftslate_growth", Map.of("snowy", "true")));
        BlockState noProps = mapping.resolve(BlockState.of("the_sift:siftslate_growth"));
        assertNotEquals(dry, snowy);
        assertEquals(dry, noProps);
    }

    @Test
    void substitutesKeepPropertiesUnlessTheTargetFixesThem() {
        BlockMapping mapping = BlockMapping.loadDefault();
        assertEquals("minecraft:mangrove_log[axis=x]",
                mapping.resolve(BlockState.of("the_sift:overgrown_willow_log", Map.of("axis", "x"))).toString());
        assertEquals("minecraft:mangrove_leaves[persistent=true]",
                mapping.resolve(BlockState.of("the_sift:overgrown_willow_foliage", Map.of("persistent", "false")))
                        .toString());
    }

    @Test
    void appendingAnEntryKeepsEarlierAssignments() {
        String base = """
                pools:
                  tripwire:
                    properties: [powered, disarmed]
                repurposed:
                  tripwire:
                    - a
                    - b
                """;
        BlockMapping before = BlockMapping.load(new StringReader(base));
        BlockMapping after = BlockMapping.load(new StringReader(base + "    - c\n"));
        assertEquals(before.resolve(BlockState.of("the_sift:a")), after.resolve(BlockState.of("the_sift:a")));
        assertEquals(before.resolve(BlockState.of("the_sift:b")), after.resolve(BlockState.of("the_sift:b")));
        assertTrue(after.isMapped("the_sift:c"));
        assertFalse(before.isMapped("the_sift:c"));
    }

    @Test
    void blockCannotBeRepurposedAndSubstituted() {
        String yaml = """
                pools:
                  tripwire:
                    properties: [powered]
                repurposed:
                  tripwire: [a]
                substitute:
                  a: minecraft:stone
                """;
        assertThrows(IllegalArgumentException.class, () -> BlockMapping.load(new StringReader(yaml)));
    }

    @Test
    void parseHandlesNamespacesAndProperties() {
        BlockState state = BlockState.parse("foo[b=2,a=1]", "the_sift");
        assertEquals("the_sift:foo[a=1,b=2]", state.toString());
        assertThrows(IllegalArgumentException.class, () -> BlockState.parse("foo[a=1", "x"));
    }

    @Test
    void blocksPlacedInRotatedTemplatesGetRotationInvariantStates() {
        BlockMapping mapping = BlockMapping.loadDefault();
        assertTrue(mapping.survivesRotation("the_sift:siftslate_coal_ore"));
        assertTrue(mapping.survivesRotation("the_sift:siftslate"));
        assertFalse(mapping.survivesRotation("the_sift:siftslate_diamond_ore"));
    }
}
