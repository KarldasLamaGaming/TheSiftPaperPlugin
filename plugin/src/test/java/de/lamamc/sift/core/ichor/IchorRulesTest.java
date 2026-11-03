package de.lamamc.sift.core.ichor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IchorRulesTest {

    @Test
    void lavaMakesSiftStone() {
        assertEquals("cobbled_siftslate", IchorRules.lavaResult("cobblestone"), "flowing lava next to ichor");
        assertEquals("siftslate", IchorRules.lavaResult("stone"), "lava flowing onto ichor");
        assertNull(IchorRules.lavaResult("obsidian"), "a lava source stays obsidian");
        assertNull(IchorRules.lavaResult("basalt"));
    }

    @Test
    void dragOverAnInterval() {
        assertEquals(0.92, IchorRules.drag(0.92, 1), 1e-9);
        assertEquals(0.92 * 0.92, IchorRules.drag(0.92, 2), 1e-9);
        assertEquals(0.92, IchorRules.drag(0.92, 0), 1e-9);
    }

    @Test
    void regenerationOutlastsTheInterval() {
        assertEquals(101, IchorRules.regenerationDuration(101, 10));
        assertEquals(201, IchorRules.regenerationDuration(101, 200));
    }
}
