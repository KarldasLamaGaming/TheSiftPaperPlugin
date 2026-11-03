package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortalTunesTest {

    private static boolean permutation(int[] notes) {
        int[] sorted = notes.clone();
        Arrays.sort(sorted);
        return Arrays.equals(sorted, new int[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    @Test
    void eightNotesUseEverySoundOnce() {
        Random random = new Random(1);
        for (int i = 0; i < 50; i++) {
            assertTrue(permutation(PortalTunes.tune(random, 8)));
        }
    }

    @Test
    void longerTunesRepeatShorterOnesCut() {
        int[] twelve = PortalTunes.tune(new Random(2), 12);
        assertEquals(12, twelve.length);
        assertTrue(permutation(Arrays.copyOf(twelve, 8)));
        assertTrue(PortalTunes.valid(twelve));
        int[] four = PortalTunes.tune(new Random(3), 4);
        assertEquals(4, four.length);
        assertEquals(4, Arrays.stream(four).distinct().count());
        assertEquals(1, PortalTunes.tune(new Random(4), 0).length);
    }

    @Test
    void tunesDiffer() {
        Random random = new Random(5);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(Arrays.toString(PortalTunes.tune(random, 8)));
        }
        assertTrue(seen.size() > 95, "random tunes, " + seen.size() + " different of 100");
    }

    @Test
    void validation() {
        assertTrue(PortalTunes.valid(new int[] {1, 8}));
        assertFalse(PortalTunes.valid(new int[] {0, 3}));
        assertFalse(PortalTunes.valid(new int[] {9}));
        assertFalse(PortalTunes.valid(new int[0]));
        assertFalse(PortalTunes.valid(null));
    }
}
