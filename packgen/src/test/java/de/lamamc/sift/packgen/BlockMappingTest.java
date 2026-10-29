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
        assertEquals("minecraft:mangrove_slab[type=top]",
                mapping.resolve(BlockState.of("the_sift:overgrown_willow_slab", Map.of("type", "top"))).toString());
        BlockMapping fixed = BlockMapping.load(new StringReader("""
                substitute:
                  leaves: minecraft:mangrove_leaves[persistent=true]
                """));
        assertEquals("minecraft:mangrove_leaves[persistent=true]",
                fixed.resolve(BlockState.of("the_sift:leaves", Map.of("persistent", "false"))).toString());
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

    @Test
    void fixedStatesAreTakenBeforeTheOthers() {
        String yaml = """
                pools:
                  tripwire:
                    properties: [powered, disarmed]
                repurposed:
                  tripwire:
                    - a
                    - b = powered=false,disarmed=false
                """;
        BlockMapping mapping = BlockMapping.load(new StringReader(yaml));
        assertEquals("minecraft:tripwire[disarmed=false,powered=false]", mapping.resolve(BlockState.of("the_sift:b")).toString());
        // "a" gets the next free state, not the one reserved for "b"
        assertEquals("minecraft:tripwire[disarmed=true,powered=false]", mapping.resolve(BlockState.of("the_sift:a")).toString());
    }

    @Test
    void fixedStateMustBeInThePoolAndFree() {
        String outside = """
                pools:
                  tripwire:
                    properties: [powered, disarmed]
                    filter: { powered: "true" }
                repurposed:
                  tripwire:
                    - a = powered=false,disarmed=false
                """;
        assertThrows(IllegalArgumentException.class, () -> BlockMapping.load(new StringReader(outside)));
        String twice = """
                pools:
                  tripwire:
                    properties: [powered, disarmed]
                repurposed:
                  tripwire:
                    - a = powered=false,disarmed=false
                    - b = powered=false,disarmed=false
                """;
        assertThrows(IllegalArgumentException.class, () -> BlockMapping.load(new StringReader(twice)));
    }

    @Test
    void logsHaveOneStatePerAxis() {
        BlockMapping mapping = BlockMapping.loadDefault();
        Set<BlockState> states = new HashSet<>();
        for (String axis : new String[] {"x", "y", "z"}) {
            BlockState state = mapping.resolve(BlockState.of("the_sift:overgrown_willow_log", Map.of("axis", axis)));
            assertEquals("minecraft:brown_mushroom_block", state.id());
            states.add(state);
        }
        assertEquals(3, states.size());
        assertFalse(mapping.isMapped("minecraft:mangrove_log"));
        assertTrue(mapping.isRepurposed("the_sift:overgrown_lotus"));
    }
}
