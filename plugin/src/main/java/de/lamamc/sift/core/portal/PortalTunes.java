package de.lamamc.sift.core.portal;

import java.util.random.RandomGenerator;

/**
 * Own tune per portal (alpha.20): rolled once with a random generator (the plugin uses SecureRandom) and kept in
 * the portal register. Nothing about the world (seed, coordinates) goes into it, so it cannot be worked out from
 * the public code. The first 8 notes are a permutation of the 8 sounds; longer tunes continue with further
 * permutations, shorter ones take the first notes.
 */
public final class PortalTunes {

    public static final int SOUNDS = 8;

    private PortalTunes() {
    }

    public static int[] tune(RandomGenerator random, int length) {
        int n = Math.max(1, length);
        int[] tune = new int[n];
        int[] block = new int[SOUNDS];
        for (int i = 0; i < n; i++) {
            if (i % SOUNDS == 0) {
                shuffle(block, random);
            }
            tune[i] = block[i % SOUNDS];
        }
        return tune;
    }

    /** Fisher–Yates over the sounds 1..8. */
    private static void shuffle(int[] block, RandomGenerator random) {
        for (int i = 0; i < SOUNDS; i++) {
            block[i] = i + 1;
        }
        for (int i = SOUNDS - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = block[i];
            block[i] = block[j];
            block[j] = t;
        }
    }

    /** Whether a stored tune is usable (notes 1..8, not empty). */
    public static boolean valid(int[] tune) {
        if (tune == null || tune.length == 0) {
            return false;
        }
        for (int note : tune) {
            if (note < 1 || note > SOUNDS) {
                return false;
            }
        }
        return true;
    }
}
