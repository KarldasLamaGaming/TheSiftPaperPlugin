package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Worlds generated with an earlier release contain the stand-in states of that release. Every state
 * assigned in 0.1.0-alpha.2 must still mean the same custom block, otherwise blocks in existing worlds
 * would turn into other blocks.
 */
class MappingStabilityTest {

    @Test
    void everyAlpha2StateStillMapsToTheSameBlock() throws Exception {
        JsonObject alpha2;
        try (var in = getClass().getResourceAsStream("/alpha2-repurposed.json")) {
            alpha2 = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Map<String, String> now = new HashMap<>(); // vanilla state -> custom block id
        BlockMapping.loadDefault().repurposed().values().forEach(entries -> entries.forEach(e ->
                now.put(e.vanilla().toString(), e.custom().id())));
        for (var e : alpha2.entrySet()) {
            String block = e.getKey().replaceFirst("\\[.*", "");
            if (MOVED_IN_41_2.contains(block)) {
                continue;
            }
            assertEquals(block, now.get(e.getValue().getAsString()), "state of " + e.getKey() + " changed");
        }
    }

    /**
     * The one deliberate exception (alpha.41.2): dry healthy sculk growth had a state that the wall of every natural red
     * mushroom cap has (west side only); it moved to a state no cap has. Only the Sift contains it, and the Sift is reset
     * before the go-live.
     */
    private static final java.util.Set<String> MOVED_IN_41_2 = java.util.Set.of("the_sift:dry_healthy_sculk_growth");

    @Test
    void noRepurposedStateLooksLikeANaturalMushroomCap() {
        BlockMapping mapping = BlockMapping.loadDefault();
        mapping.repurposed().values().forEach(entries -> entries.forEach(e -> {
            String state = e.vanilla().toString();
            if (!state.contains("mushroom_block[")) {
                return;
            }
            int open = 0;
            boolean north = state.contains("north=true");
            boolean east = state.contains("east=true");
            boolean south = state.contains("south=true");
            boolean west = state.contains("west=true");
            open = (north ? 1 : 0) + (east ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0);
            boolean adjacentPair = open == 2 && !(north && south) && !(east && west);
            boolean up = state.contains("up=true");
            boolean red = state.startsWith("minecraft:red");
            // natural caps (all down=false): red up=false with one side or two adjacent sides, red/brown up=true with
            // none, one side or two adjacent sides
            boolean natural = (up && (open <= 1 || adjacentPair)) || (red && !up && (open == 1 || adjacentPair));
            assertTrue(!natural, e.custom().id() + " uses a natural mushroom cap state: " + state);
        }));
    }

    @Test
    void stateIsResolvedForTheOldCustomStateToo() {
        BlockMapping mapping = BlockMapping.loadDefault();
        List<BlockMapping.Entry> glass = mapping.repurposed().get("the_sift:ichor_glass");
        assertEquals(2, glass.size());
        // placing and worldgen use the first (new) state
        assertEquals(glass.getFirst().vanilla(), mapping.resolve(BlockState.of("the_sift:ichor_glass")));
        assertEquals("minecraft:chorus_plant[down=false,east=true,north=true,south=true,up=true,west=true]",
                glass.getFirst().vanilla().toString());
        assertTrue(mapping.survivesRotation("the_sift:ichor_glass"));
        assertTrue(!mapping.survivesRotation("the_sift:overgrown_willow_log"));
    }
}
