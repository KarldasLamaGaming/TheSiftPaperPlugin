package de.lamamc.sift.core.portal;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps an entity that just arrived in a portal from going straight back (SiftTeleportManager and
 * PortalTransitGuard of the original): after a transfer it must step out of every portal surface first.
 */
public final class TransitGuard {

    private final Set<UUID> waitingForExit = new HashSet<>();

    /** Marks an entity that was just transferred (or logged in standing in a portal). */
    public void arrived(UUID id) {
        waitingForExit.add(id);
    }

    /** Called every check with whether the entity touches a portal; true = transfer it now. */
    public boolean shouldTransfer(UUID id, boolean touchingPortal) {
        if (waitingForExit.contains(id)) {
            if (!touchingPortal) {
                waitingForExit.remove(id);
            }
            return false;
        }
        return touchingPortal;
    }

    /** Releases every waiting entity that touches no portal any more (or is gone); returns them. */
    public Set<UUID> releaseAllExcept(Set<UUID> touching) {
        Set<UUID> released = new HashSet<>(waitingForExit);
        released.removeAll(touching);
        waitingForExit.removeAll(released);
        return released;
    }

    public boolean waiting(UUID id) {
        return waitingForExit.contains(id);
    }

    public void forget(UUID id) {
        waitingForExit.remove(id);
    }
}
