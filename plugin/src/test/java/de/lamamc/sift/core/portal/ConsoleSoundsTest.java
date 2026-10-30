package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConsoleSoundsTest {

    @Test
    void rightClickStepsThroughTheEightSoundsInOrder() {
        int sound = 1;
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            order.append(sound).append(' ');
            sound = ConsoleSounds.next(sound);
        }
        assertEquals("1 2 3 4 5 6 7 8 1 ", order.toString());
    }

    @Test
    void notesHoldTheSounds() {
        for (int sound = 1; sound <= 8; sound++) {
            assertEquals(sound, ConsoleSounds.soundOf(ConsoleSounds.noteOf(sound)));
        }
        assertEquals(1, ConsoleSounds.soundOf(8)); // vanilla notes above 7 (tuned elsewhere) wrap like in the original
        assertEquals(3, ConsoleSounds.soundOf(18));
        assertEquals("1-3-7-6-5-2-4-8", ConsoleSounds.text(PortalMelody.DEFAULT_SEQUENCE));
    }
}
