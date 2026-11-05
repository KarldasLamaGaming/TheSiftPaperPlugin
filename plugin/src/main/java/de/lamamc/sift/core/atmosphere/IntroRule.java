package de.lamamc.sift.core.atmosphere;

/**
 * When the intro sound of the Sift plays (alpha.29): once per player, on the first stay in the Sift. The
 * original plays it when the client's loading screen is done; without a client mod the plugin waits until
 * the player has been in the Sift with ground below (8 blocks) for {@code delayTicks}.
 */
public final class IntroRule {

    private IntroRule() {
    }

    /** Ticks counted so far after one more check {@code step} ticks later; reset when not in the Sift or above the void. */
    public static int count(int counted, boolean inSift, boolean groundBelow, int step) {
        return inSift && groundBelow ? counted + step : 0;
    }

    public static boolean play(boolean alreadyPlayed, int counted, int delayTicks) {
        return !alreadyPlayed && counted >= delayTicks;
    }
}
