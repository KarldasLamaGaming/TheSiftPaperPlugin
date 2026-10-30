package de.lamamc.sift.core.portal;

/**
 * Life of a structure portal (hybrid of the plan and the original): the key wakes the consoles, the tune
 * opens the frame, and it stays open (only an admin closes it).
 */
public enum PortalState {
    /** Consoles in horn mode: notes only glitch. */
    LOCKED,
    /** Consoles in note mode: the tune opens the portal. */
    UNLOCKED,
    /** Surface open, entities travel. */
    OPEN;

    public PortalState unlock() {
        return this == LOCKED ? UNLOCKED : this;
    }

    /** The tune opens an unlocked portal; a locked console cannot play it, an open portal stays open. */
    public PortalState onTune(PortalMelody.Match match) {
        return this == UNLOCKED && match == PortalMelody.Match.OPENING ? OPEN : this;
    }

    /** Admin command: open at once. */
    public PortalState activate() {
        return OPEN;
    }

    /** Admin command: close, consoles stay awake. */
    public PortalState deactivate() {
        return this == OPEN ? UNLOCKED : this;
    }
}
