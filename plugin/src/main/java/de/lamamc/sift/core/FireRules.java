package de.lamamc.sift.core;

import java.util.Random;

/**
 * Vanilla fire (FireBlock#tick) for blocks the server does not know as flammable: whether a flammable
 * neighbour burns away (checkBurnOut) and whether fire spreads to an empty spot (the spread loop), with
 * the same numbers. Burn and ignite odds are the block's flammability values.
 */
public final class FireRules {

    /** What happens to a neighbour of the fire this tick. */
    public enum BurnOut { NOTHING, FIRE, AIR }

    private FireRules() {
    }

    /** Vanilla fire tick delay: 30–39 game ticks. */
    public static int tickDelay(Random random) {
        return 30 + random.nextInt(10);
    }

    /**
     * @param chance 300 for horizontal neighbours, 250 above and below
     * @param burnOdds the neighbour's burn odds (0 = not flammable)
     * @param age age of the fire (0–15)
     * @param rain whether rain falls on the neighbour
     */
    public static BurnOut burnOut(Random random, int chance, int burnOdds, int age, boolean rain) {
        if (burnOdds <= 0 || random.nextInt(chance) >= burnOdds) {
            return BurnOut.NOTHING;
        }
        return random.nextInt(age + 10) < 5 && !rain ? BurnOut.FIRE : BurnOut.AIR;
    }

    /** New fire age after burning or spreading: age + 0 or 1. */
    public static int nextAge(Random random, int age) {
        return Math.min(15, age + random.nextInt(5) / 4);
    }

    /**
     * Whether fire spreads to an empty spot {@code dy} blocks above the fire's level.
     *
     * @param igniteOdds highest ignite odds of the spot's neighbours
     * @param difficulty 0 peaceful … 3 hard
     */
    public static boolean spreads(Random random, int igniteOdds, int age, int dy, int difficulty, boolean rain) {
        if (igniteOdds <= 0) {
            return false;
        }
        int rate = 100;
        if (dy > 1) {
            rate += (dy - 1) * 100;
        }
        int adjusted = (igniteOdds + 40 + difficulty * 7) / (age + 30);
        return adjusted > 0 && random.nextInt(rate) <= adjusted && !rain;
    }
}
