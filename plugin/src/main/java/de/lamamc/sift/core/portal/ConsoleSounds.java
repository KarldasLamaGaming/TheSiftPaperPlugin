package de.lamamc.sift.core.portal;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The 8 sounds of a console and how a note block holds them: sound 1 to 8 is note 0 to 7 (other notes count
 * modulo 8, like the original). Right-click steps 1 -> 2 -> ... -> 8 -> 1.
 */
public final class ConsoleSounds {

    public static final int COUNT = 8;

    private ConsoleSounds() {
    }

    /** Sound 1..8 of a note block's note (0..24). */
    public static int soundOf(int note) {
        return Math.floorMod(note, COUNT) + 1;
    }

    /** The sound after this one (8 wraps to 1). */
    public static int next(int sound) {
        return sound % COUNT + 1;
    }

    /** The note that holds this sound. */
    public static int noteOf(int sound) {
        return sound - 1;
    }

    /** "1-3-7-6-5-2-4-8" */
    public static String text(int[] melody) {
        return Arrays.stream(melody).mapToObj(String::valueOf).collect(Collectors.joining("-"));
    }
}
