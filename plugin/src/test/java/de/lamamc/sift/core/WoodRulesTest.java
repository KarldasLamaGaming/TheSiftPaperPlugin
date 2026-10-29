package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WoodRulesTest {

    @Test
    void axisFollowsTheClickedFace() {
        assertEquals("y", WoodRules.axisFor("UP"));
        assertEquals("y", WoodRules.axisFor("DOWN"));
        assertEquals("x", WoodRules.axisFor("EAST"));
        assertEquals("x", WoodRules.axisFor("WEST"));
        assertEquals("z", WoodRules.axisFor("NORTH"));
        assertEquals("z", WoodRules.axisFor("SOUTH"));
        assertEquals("y", WoodRules.axisFor("SELF"));
    }

    @Test
    void onlyLogsAndWoodCanBeStripped() {
        assertEquals("stripped_overgrown_willow_log", WoodRules.strippedOf("overgrown_willow_log"));
        assertEquals("stripped_overgrown_willow_wood", WoodRules.strippedOf("overgrown_willow_wood"));
        assertNull(WoodRules.strippedOf("stripped_overgrown_willow_log"));
        assertNull(WoodRules.strippedOf("overgrown_willow_planks"));
    }
}
