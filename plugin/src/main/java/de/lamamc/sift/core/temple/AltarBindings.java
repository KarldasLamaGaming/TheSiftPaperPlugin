package de.lamamc.sift.core.temple;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "One altar per player" (alpha.42f, phase4.temples.altar-one-per-player): a player may wake the boss at the altar of one
 * temple only. The first time a player wakes it, the player is bound to that temple; after a death or a called-off fight the
 * same temple may be woken again. When its boss is defeated the place is used up: no altar anywhere any more. Fellow
 * fighters are never bound. Admin commands and the bypass permission do not bind anyone. Without Bukkit, with tests.
 */
public final class AltarBindings {

    /** Where a player stands: bound to a temple, and whether its boss fell. */
    public record Binding(String temple, boolean defeated) {

        String serialize(UUID player) {
            return player + " " + temple + " " + (defeated ? "defeated" : "bound");
        }
    }

    /** May the player wake the boss here? */
    public enum Verdict {
        /** Yes; a free player is bound with the start. */
        ALLOW,
        /** Yes, without any binding (rule off or bypass permission). */
        FREE,
        /** No: bound to another temple whose boss still lives. */
        OTHER_TEMPLE,
        /** No: the boss of the player's temple is defeated. */
        DEFEATED
    }

    private final Map<UUID, Binding> bindings = new LinkedHashMap<>();

    public Verdict check(UUID player, String temple, boolean enabled, boolean bypass) {
        if (!enabled || bypass) {
            return Verdict.FREE;
        }
        Binding b = bindings.get(player);
        if (b == null) {
            return Verdict.ALLOW;
        }
        if (b.defeated()) {
            return Verdict.DEFEATED;
        }
        return b.temple().equals(temple) ? Verdict.ALLOW : Verdict.OTHER_TEMPLE;
    }

    /** The player woke the boss of this temple (only after {@link Verdict#ALLOW}); true if the binding is new. */
    public boolean woke(UUID player, String temple) {
        Binding before = bindings.get(player);
        if (before != null) {
            return false;
        }
        bindings.put(player, new Binding(temple, false));
        return true;
    }

    /** The boss of this temple is defeated: everyone bound to it has used up the place. Returns how many. */
    public int defeated(String temple) {
        int n = 0;
        for (Map.Entry<UUID, Binding> e : bindings.entrySet()) {
            if (e.getValue().temple().equals(temple) && !e.getValue().defeated()) {
                e.setValue(new Binding(temple, true));
                n++;
            }
        }
        return n;
    }

    public Binding get(UUID player) {
        return bindings.get(player);
    }

    /** Admin: lifts a player's binding; true if there was one. */
    public boolean reset(UUID player) {
        return bindings.remove(player) != null;
    }

    /** Player reset for the go-live: all bindings go. Returns how many. */
    public int clear() {
        int n = bindings.size();
        bindings.clear();
        return n;
    }

    public int size() {
        return bindings.size();
    }

    public String serialize() {
        StringBuilder out = new StringBuilder();
        bindings.forEach((id, b) -> out.append(b.serialize(id)).append('\n'));
        return out.toString();
    }

    /** Reads what {@link #serialize()} wrote; broken lines are skipped. */
    public static AltarBindings parse(String text) {
        AltarBindings out = new AltarBindings();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length != 3) {
                continue;
            }
            try {
                out.bindings.put(UUID.fromString(p[0]), new Binding(p[1], p[2].equals("defeated")));
            } catch (IllegalArgumentException ignored) {
                // skip a broken line
            }
        }
        return out;
    }
}
