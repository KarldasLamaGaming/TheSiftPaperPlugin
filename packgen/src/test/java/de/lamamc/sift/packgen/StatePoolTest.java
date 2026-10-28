package de.lamamc.sift.packgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatePoolTest {

    private static final List<String> MUSHROOM = List.of("up", "down", "north", "east", "south", "west");

    @Test
    void filterAndExcludeLimitThePool() {
        StatePool pool = new StatePool("minecraft:mushroom_stem", MUSHROOM, Map.of("down", "false"),
                List.of(Map.of("up", "false", "down", "false", "north", "true", "east", "true",
                        "south", "true", "west", "true")));
        assertEquals(31, pool.size());
        Set<BlockState> taken = new HashSet<>();
        while (pool.remaining() > 0) {
            BlockState state = pool.take();
            assertEquals("false", state.properties().get("down"));
            taken.add(state);
        }
        assertEquals(31, taken.size());
        assertThrows(IllegalStateException.class, pool::take);
    }

    @Test
    void orderIsStableWithFirstPropertySlowest() {
        StatePool pool = new StatePool("minecraft:mushroom_stem", MUSHROOM, Map.of(), List.of());
        assertEquals("minecraft:mushroom_stem[down=false,east=false,north=false,south=false,up=false,west=false]",
                pool.take().toString());
        assertEquals("minecraft:mushroom_stem[down=false,east=false,north=false,south=false,up=false,west=true]",
                pool.take().toString());
    }

    @Test
    void unknownFilterPropertyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatePool("minecraft:tripwire", List.of("powered"), Map.of("axis", "x"), List.of()));
    }
}
