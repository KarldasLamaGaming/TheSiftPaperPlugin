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
            assertEquals(block, now.get(e.getValue().getAsString()), "state of " + e.getKey() + " changed");
        }
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
