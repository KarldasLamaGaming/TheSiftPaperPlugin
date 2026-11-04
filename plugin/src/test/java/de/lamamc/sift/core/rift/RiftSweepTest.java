package de.lamamc.sift.core.rift;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiftSweepTest {

    @Test
    void openingAndClosingDuringThePassIsSafe() {
        List<String> rifts = new ArrayList<>(List.of("a", "b", "c"));
        List<String> visited = new ArrayList<>();
        assertDoesNotThrow(() -> RiftSweep.run(rifts, rift -> {
            visited.add(rift);
            if (rift.equals("a")) {
                rifts.add("counter"); // someone travels through a: its counter rift opens
            }
            if (rift.equals("b")) {
                rifts.remove("c"); // e.g. /sift rift close from a callback
            }
            return !rift.equals("b"); // b is finished
        }));
        assertEquals(List.of("a", "b", "c"), visited, "the snapshot is visited in full");
        assertEquals(List.of("a", "counter"), rifts, "b removed, the new counter rift stays for the next tick");
    }
}
