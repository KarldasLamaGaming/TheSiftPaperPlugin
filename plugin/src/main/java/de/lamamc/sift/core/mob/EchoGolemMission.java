package de.lamamc.sift.core.mob;

/**
 * What an Echo Golem is doing (MissionState and RoutePhase of the original) and the timed steps of picking up,
 * bowing and sitting. The plugin moves the golem; this class decides when a timed step happens.
 */
public final class EchoGolemMission {

    public enum State { SEARCH, PICKUP, CARRY, BOW, SIT }

    public enum Phase { EXPLORE, APPROACH_CANYON, ENTER_CANYON, DESCEND_CANYON, SEEK_SOUL, LEAVE_EMPTY_CANYON, EXIT_CANYON, DELIVER }

    /** What a timed step asks the plugin to do this tick. */
    public enum Step { NONE, TAKE_SOUL, START_CARRY, GIVE_TO_OWNER, END, RESUME_CARRY }

    private final EchoGolemRules rules;
    private State state = State.SEARCH;
    private Phase phase = Phase.EXPLORE;
    private int stateTicks;
    private int phaseTicks;

    public EchoGolemMission(EchoGolemRules rules) {
        this.rules = rules;
    }

    public State state() {
        return state;
    }

    public Phase phase() {
        return phase;
    }

    public int stateTicks() {
        return stateTicks;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public void set(State next) {
        state = next;
        stateTicks = 0;
    }

    public void set(Phase next) {
        if (phase != next) {
            phase = next;
            phaseTicks = 0;
        }
    }

    /** Restores a saved state (ticks start over). */
    public void restore(State savedState, Phase savedPhase) {
        state = savedState;
        phase = savedPhase;
        stateTicks = 0;
        phaseTicks = 0;
    }

    /** One tick; returns the timed step of the current state. */
    public Step tick(boolean hasSoul, boolean bonded, boolean ownerInReach) {
        stateTicks++;
        phaseTicks++;
        return switch (state) {
            case PICKUP -> stateTicks == rules.pickupTakeTick() ? Step.TAKE_SOUL
                    : stateTicks >= rules.pickupEndTick() ? Step.START_CARRY : Step.NONE;
            case BOW -> bow(hasSoul, bonded, ownerInReach);
            case SIT -> stateTicks >= rules.sitTicks() ? Step.END : Step.NONE;
            default -> Step.NONE;
        };
    }

    private Step bow(boolean hasSoul, boolean bonded, boolean ownerInReach) {
        if (!bonded) {
            // the Singer ends the bow; this is only the fallback when it never comes
            return stateTicks >= rules.bowTimeout() ? (hasSoul ? Step.RESUME_CARRY : Step.END) : Step.NONE;
        }
        if (hasSoul && !ownerInReach) {
            return Step.RESUME_CARRY;
        }
        if (hasSoul && stateTicks == rules.bowGiveTick()) {
            return Step.GIVE_TO_OWNER;
        }
        return stateTicks >= rules.bowEndTick() ? Step.END : Step.NONE;
    }

    /** Where carrying starts: on the surface it delivers right away, in the canyon it climbs out first. */
    public void startCarry(boolean onSurface) {
        set(State.CARRY);
        set(onSurface ? Phase.DELIVER : Phase.EXIT_CANYON);
    }

    /** Back to searching after a delivery, a lost soul or sitting. */
    public void resumeSearch() {
        set(State.SEARCH);
        set(Phase.EXPLORE);
    }
}
