package de.lamamc.sift.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MappingEntryTest {

    static List<MappingEntry> bundled() throws IOException {
        try (var in = MappingEntryTest.class.getResourceAsStream("/block-mapping.json")) {
            return MappingEntry.read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void parsesIdAndProperties() {
        MappingEntry entry = MappingEntry.parse("the_sift:siftslate_growth[snowy=true]",
                "minecraft:mushroom_stem[down=false,east=false]");
        assertEquals("siftslate_growth", entry.id());
        assertEquals(Map.of("snowy", "true"), entry.properties());
        assertEquals("minecraft:mushroom_stem[down=false,east=false]", entry.vanillaState());
        assertThrows(IllegalArgumentException.class, () -> MappingEntry.parse("minecraft:stone", "minecraft:stone"));
    }

    @Test
    void bundledMappingHasUniqueVanillaStates() throws IOException {
        List<MappingEntry> entries = bundled();
        Set<String> states = new HashSet<>();
        entries.forEach(e -> assertTrue(states.add(e.vanillaState()), "used twice: " + e.vanillaState()));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("siftslate")));
    }
}
