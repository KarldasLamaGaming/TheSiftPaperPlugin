package de.lamamc.sift.core.portal;

import de.lamamc.sift.core.access.SiftAccess;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Staggered unlocking of the portals into the Sift (alpha.42a.2, portals.unlock-schedule): each group of portals opens a
 * number of days after the Sift itself opened. Before that its portals are sealed: keys do not open them and open ones
 * let nobody in (players with thesift.access.bypass excepted). The main portal in the Sift (the way out) is never sealed.
 */
public final class PortalSchedule {

    public enum Group {
        END("end"), ANCIENT_CITY("ancient-city"), NETHER("nether"), OVERWORLD("overworld");

        private final String key;

        Group(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Group of(String key) {
            for (Group g : values()) {
                if (g.key.equals(key.toLowerCase(Locale.ROOT))) {
                    return g;
                }
            }
            return null;
        }

        /** The group of a portal: Ancient City portals by kind, shrines by the environment of their world; null = never sealed. */
        public static Group of(boolean ancientCity, boolean siftExit, String environment) {
            if (siftExit) {
                return null;
            }
            if (ancientCity) {
                return ANCIENT_CITY;
            }
            return switch (environment) {
                case "NETHER" -> NETHER;
                case "THE_END" -> END;
                case "NORMAL" -> OVERWORLD;
                default -> null;
            };
        }
    }

    /** An admin's word on one group, stronger than the schedule. */
    public enum Override { NONE, UNLOCKED, LOCKED }

    /** @param days group -> days after the opening of the Sift (missing = 0) */
    public record Settings(boolean enabled, Map<Group, Double> days) {

        public Settings {
            days = days.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(days));
        }

        public double days(Group g) {
            return Math.max(0, days.getOrDefault(g, 0.0));
        }
    }

    /** @param at when the group opens (epoch ms), null when not known yet or sealed by an admin */
    public record Status(boolean unlocked, Long at, String reason) {
    }

    private PortalSchedule() {
    }

    public static Status status(Settings settings, Group group, long now, Long openedAt, Override override) {
        if (!settings.enabled() || group == null) {
            return new Status(true, null, "keine Staffel");
        }
        if (override == Override.UNLOCKED) {
            return new Status(true, null, "per Befehl freigeschaltet");
        }
        if (override == Override.LOCKED) {
            return new Status(false, null, "per Befehl versiegelt");
        }
        if (openedAt == null) {
            return new Status(false, null, "die Sift ist noch nicht geöffnet");
        }
        long at = openedAt + Math.round(settings.days(group) * 86_400_000L);
        return now >= at ? new Status(true, at, "freigeschaltet") : new Status(false, at, "wartet auf das Datum");
    }

    /**
     * When the Sift opened, the day 0 of the schedule: set by an admin, else from the access mode (fixed-date: the date,
     * after-dragon: kill + delay), else the moment the plugin first saw the Sift open (always, manual, /sift open).
     */
    public static Long openedAt(SiftAccess.Settings access, Long dragonKill, Long firstSeenOpen, Long setByAdmin) {
        if (setByAdmin != null) {
            return setByAdmin;
        }
        if (access.mode() == SiftAccess.Mode.FIXED_DATE && access.date() != null) {
            return access.date();
        }
        if (access.mode() == SiftAccess.Mode.AFTER_DRAGON && dragonKill != null) {
            return dragonKill + access.delayMillis();
        }
        return firstSeenOpen;
    }
}
