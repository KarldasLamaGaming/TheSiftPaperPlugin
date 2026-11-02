package de.lamamc.sift.core.mob;

import java.util.random.RandomGenerator;

/**
 * One warden's petting of Dark Sniffers (WardenDarkSnifferMixin of the original 1.1.1). A completely calm
 * warden looks for an available Dark Sniffer within {@code searchRange} (original: every 800–2400 ticks),
 * walks to it (giving up after {@code approachTimeout} ticks), and pets it for {@code duration} ticks within
 * {@code reach} blocks, with a heart every 20 ticks. Afterwards it waits (original: 2400–4800 ticks, after a
 * failed approach 600–1200, without a candidate 300–600).
 */
public final class WardenPetting {

    public enum Action { NONE, SEARCH, APPROACH, PET, HEART, DONE, FAILED }

    /**
     * Timings in ticks; each {@code int[]} is {minimum, maximum}: delay before the first search, pause after
     * petting, after a failed approach and after a search without a candidate.
     */
    public record Rules(double searchRange, double reach, int duration, int approachTimeout,
                        int[] searchDelay, int[] cooldown, int[] failedCooldown, int[] emptyCooldown) {

        /** The values of the original. */
        public static Rules defaults() {
            return new Rules(18, 3.5, 100, 240, new int[] {800, 2400}, new int[] {2400, 4800}, new int[] {600, 1200},
                    new int[] {300, 600});
        }
    }

    private final Rules rules;
    private int searchCooldown = -1;
    private boolean active;
    private int approachTicks;
    private int pettingTicks;

    public WardenPetting(Rules rules) {
        this.rules = rules;
    }

    public boolean active() {
        return active;
    }

    private static int delay(RandomGenerator random, int[] range) {
        return range[0] + random.nextInt(Math.max(0, range[1] - range[0]) + 1);
    }

    /** No candidate yet: counts down and says when to look for one (only while calm). */
    public Action idle(boolean calm, RandomGenerator random) {
        if (searchCooldown < 0) {
            searchCooldown = delay(random, rules.searchDelay());
        }
        return searchCooldown-- <= 0 && calm ? Action.SEARCH : Action.NONE;
    }

    /** Result of a search: a candidate starts the approach, none waits a while. */
    public void searched(boolean found, RandomGenerator random) {
        if (found) {
            active = true;
            approachTicks = 0;
            pettingTicks = 0;
        } else {
            searchCooldown = delay(random, rules.emptyCooldown());
        }
    }

    /**
     * One tick with a candidate; {@code distanceSquared} to it, {@code valid} = it is still there, in range
     * and the warden still calm.
     */
    public Action tick(boolean valid, double distanceSquared, RandomGenerator random) {
        if (!active) {
            return Action.NONE;
        }
        if (!valid || distanceSquared > rules.searchRange() * rules.searchRange()) {
            return stop(false, random);
        }
        if (distanceSquared <= rules.reach() * rules.reach()) {
            int tick = pettingTicks++;
            if (pettingTicks >= rules.duration()) {
                return stop(true, random);
            }
            return tick % 20 == 8 ? Action.HEART : Action.PET;
        }
        if (++approachTicks > rules.approachTimeout()) {
            return stop(false, random);
        }
        return Action.APPROACH;
    }

    private Action stop(boolean completed, RandomGenerator random) {
        active = false;
        pettingTicks = 0;
        approachTicks = 0;
        searchCooldown = delay(random, completed ? rules.cooldown() : rules.failedCooldown());
        return completed ? Action.DONE : Action.FAILED;
    }

    int searchCooldown() {
        return searchCooldown;
    }
}
