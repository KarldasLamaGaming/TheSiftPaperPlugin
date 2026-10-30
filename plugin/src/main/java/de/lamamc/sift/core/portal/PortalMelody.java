package de.lamamc.sift.core.portal;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * The tune a console must play (SonorousConsoles of the original): the last notes played on one console
 * group, compared with the opening sequence and its reverse. Notes are the 8 sonorous sounds, 1 to 8.
 */
public final class PortalMelody {

    /** The sequence of the original. */
    public static final int[] DEFAULT_SEQUENCE = {1, 3, 7, 6, 5, 2, 4, 8};

    public enum Match { NONE, OPENING, CLOSING }

    private final int[] opening;
    private final int[] closing;
    private final Deque<Integer> played = new ArrayDeque<>();

    public PortalMelody(int[] sequence) {
        if (sequence.length == 0) {
            throw new IllegalArgumentException("empty melody");
        }
        for (int note : sequence) {
            if (note < 1 || note > 8) {
                throw new IllegalArgumentException("melody notes are 1..8, got " + note);
            }
        }
        this.opening = sequence.clone();
        this.closing = reverse(sequence);
    }

    /** Records a note; a match clears the memory, so the next tune starts from scratch. */
    public Match play(int note) {
        played.addLast(note);
        while (played.size() > opening.length) {
            played.removeFirst();
        }
        Match match = matches(opening) ? Match.OPENING : matches(closing) ? Match.CLOSING : Match.NONE;
        if (match != Match.NONE) {
            played.clear();
        }
        return match;
    }

    public void reset() {
        played.clear();
    }

    public int length() {
        return opening.length;
    }

    private boolean matches(int[] target) {
        if (played.size() != target.length) {
            return false;
        }
        int i = 0;
        for (int note : played) {
            if (note != target[i++]) {
                return false;
            }
        }
        return true;
    }

    private static int[] reverse(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[source.length - 1 - i];
        }
        return result;
    }

    @Override
    public String toString() {
        return Arrays.toString(opening);
    }
}
