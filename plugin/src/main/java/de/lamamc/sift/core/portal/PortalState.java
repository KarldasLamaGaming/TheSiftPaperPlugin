package de.lamamc.sift.core.portal;

/**
 * Life of a structure portal: the matching key wakes the consoles and opens the portal in one go (alpha.37), and it
 * stays open (only an admin closes or locks it). UNLOCKED remains for portals woken before alpha.37 (the tune used to
 * open them) and for an ignition that was cut short: the key opens them without being used up.
 */
public enum PortalState {
    /** Consoles in horn mode, asleep. */
    LOCKED,
    /** Consoles awake (note mode), surface still closed. */
    UNLOCKED,
    /** Surface open, entities travel. */
    OPEN;

    public PortalState unlock() {
        return this == LOCKED ? UNLOCKED : this;
    }

    /** Admin command: open at once. */
    public PortalState activate() {
        return OPEN;
    }

    /** Admin command: close, consoles stay awake. */
    public PortalState deactivate() {
        return this == OPEN ? UNLOCKED : this;
    }

    /** Admin command /sift portal lock: back to sleep (a key is needed again). */
    public PortalState lock() {
        return LOCKED;
    }
}
