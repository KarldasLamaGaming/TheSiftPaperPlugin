package de.lamamc.sift.core.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decides which right-click events on a Sift mob reach it. Paper 26.3 reports a click on an entity as
 * PlayerInteractAtEntityEvent only; older builds also sent a PlayerInteractEntityEvent for the same click, and
 * both reach the same listener. The vanilla client also tries the off hand when it thinks the main hand did
 * nothing (it does not know the Sift mob), so after a main-hand click that did something, the off-hand click of
 * the same tick is dropped as well.
 */
public final class ClickFilter {

    private record Key(Object player, int entity) {
    }

    private record Seen(long tick, boolean mainHand, boolean offHand, boolean handled) {
    }

    private final Map<Key, Seen> seen = new HashMap<>();

    /** Whether this click (same tick, player, entity) should reach the mob. */
    public boolean accept(long tick, Object player, int entity, boolean offHand) {
        Key key = new Key(Objects.requireNonNull(player), entity);
        Seen before = seen.get(key);
        if (before == null || before.tick() != tick) {
            before = new Seen(tick, false, false, false);
        }
        if (before.handled() || (offHand ? before.offHand() : before.mainHand())) {
            return false;
        }
        seen.put(key, new Seen(tick, before.mainHand() || !offHand, before.offHand() || offHand, false));
        if (seen.size() > 512) {
            seen.values().removeIf(s -> s.tick() < tick - 1);
        }
        return true;
    }

    /** The mob did something with the accepted click: no further click of this tick gets through. */
    public void handled(long tick, Object player, int entity) {
        Key key = new Key(Objects.requireNonNull(player), entity);
        Seen before = seen.get(key);
        seen.put(key, new Seen(tick, before != null && before.mainHand(), before != null && before.offHand(), true));
    }
}
