package de.lamamc.sift.core.mob;

import java.util.function.Function;

/**
 * The name a Sift mob's invisible base mob carries (custom name, never shown: invisible entities have no name
 * tag), so that death messages, /kill and the like say "Sifter" instead of "Pig". Names come from messages.yml
 * mobs.names.&lt;id&gt;; a name we did not set ourselves (no marker) is kept.
 */
public final class MobNames {

    private MobNames() {
    }

    /** Name for a kind id: the configured one, else the id itself. */
    public static String nameFor(String kindId, Function<String, String> configured) {
        String name = configured.apply(kindId);
        return name == null || name.isBlank() ? kindId : name;
    }

    /** Set our name when the mob has none, or when the one it has is ours (refresh after a rename in messages.yml). */
    public static boolean apply(boolean hasCustomName, boolean ourMarker) {
        return !hasCustomName || ourMarker;
    }
}
