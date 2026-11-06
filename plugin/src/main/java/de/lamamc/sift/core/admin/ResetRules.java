package de.lamamc.sift.core.admin;

/**
 * Player reset for the go-live (/sift admin reset-players): a server-wide reset number goes up with each
 * confirmed reset; every player whose own number is lower is reset when seen (online at once, offline
 * players at their next login), so nobody is reset twice and nobody is missed.
 */
public final class ResetRules {

    private ResetRules() {
    }

    /** Does a player with this reset number still need the current server reset? */
    public static boolean due(long playerReset, long serverReset) {
        return serverReset > 0 && playerReset < serverReset;
    }

    /** A pending confirmation started at {@code requestedAt} is still valid at {@code now}. */
    public static boolean confirmable(long requestedAt, long now, long windowMillis) {
        return requestedAt > 0 && now >= requestedAt && now - requestedAt <= windowMillis;
    }
}
