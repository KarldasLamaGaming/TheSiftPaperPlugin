package de.lamamc.sift.core.portal;

/**
 * What a portal key does on a console (alpha.37): the matching key opens the portal in one go (the consoles wake
 * and the ignition plays), a wrong key only glitches. Since alpha.37 there is no tune to play any more.
 */
public final class KeyActivation {

    private KeyActivation() {
    }

    public enum Result {
        /** portals.keys.enabled: false, only admins open portals. */
        DISABLED,
        /** The console belongs to no registered portal. */
        NO_PORTAL,
        /** The portal is open already (portals stay open). */
        ALREADY_OPEN,
        /** Another dimension's (or the Ancient City's) key. */
        WRONG_KEY,
        /** Wake the consoles and ignite. */
        OPEN
    }

    /**
     * @param held   Sift id of the key in the hand
     * @param needed the key this portal takes (null: none configured)
     */
    public static Result decide(boolean keysEnabled, boolean portalFound, PortalState state, String held, String needed) {
        if (!keysEnabled) {
            return Result.DISABLED;
        }
        if (!portalFound) {
            return Result.NO_PORTAL;
        }
        if (state == PortalState.OPEN) {
            return Result.ALREADY_OPEN;
        }
        return held != null && held.equals(needed) ? Result.OPEN : Result.WRONG_KEY;
    }

    /**
     * Whether opening uses the key up: only a sleeping portal (one already woken before alpha.37, or whose ignition was
     * cut short, opens again without), never in creative, and only with portals.keys.consume.
     */
    public static boolean consumes(boolean consumeSetting, boolean creative, PortalState state) {
        return consumeSetting && !creative && state == PortalState.LOCKED;
    }
}
