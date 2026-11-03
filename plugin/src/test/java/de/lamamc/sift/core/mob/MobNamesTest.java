package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MobNamesTest {

    /** The names shipped in messages.yml mobs.names. */
    private static final Map<String, String> SHIPPED = Map.of("blub", "Blub", "sifter", "Sifter", "singer", "Singer",
            "dark_sniffer", "Dunkler Schnüffler", "echo_golem", "Echo Golem");

    @Test
    void everyKindGetsItsSiftName() {
        assertEquals("Sifter", MobNames.nameFor("sifter", SHIPPED::get));
        assertEquals("Blub", MobNames.nameFor("blub", SHIPPED::get));
        assertEquals("Singer", MobNames.nameFor("singer", SHIPPED::get));
        assertEquals("Dunkler Schnüffler", MobNames.nameFor("dark_sniffer", SHIPPED::get));
        assertEquals("Echo Golem", MobNames.nameFor("echo_golem", SHIPPED::get));
        // a kind without a configured name keeps its id (never the vanilla base name)
        assertEquals("new_mob", MobNames.nameFor("new_mob", SHIPPED::get));
        assertEquals("blub", MobNames.nameFor("blub", id -> " "));
    }

    @Test
    void namesFromElsewhereAreKept() {
        assertTrue(MobNames.apply(false, false), "no name yet");
        assertTrue(MobNames.apply(true, true), "our own name is refreshed");
        assertFalse(MobNames.apply(true, false), "a name a player gave stays");
    }

    @Test
    void shippedMessagesHaveAllNames() throws java.io.IOException {
        String messages = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/messages.yml"));
        for (var entry : SHIPPED.entrySet()) {
            assertTrue(messages.contains(entry.getKey() + ": \"" + entry.getValue() + "\""), entry.getKey());
        }
    }
}
