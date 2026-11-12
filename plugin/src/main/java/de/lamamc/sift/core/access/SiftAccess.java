package de.lamamc.sift.core.access;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Whether the Sift may be entered (alpha.20, config access.*): always, a number of days after the first dragon
 * kill, from a fixed date, or only by command. A state set by command (open or closed) wins over the schedule
 * until it is reset.
 */
public final class SiftAccess {

    public enum Mode {
        ALWAYS, AFTER_DRAGON, FIXED_DATE, MANUAL;

        public static Mode parse(String text) {
            if (text == null) {
                return ALWAYS;
            }
            return switch (text.trim().toLowerCase(Locale.ROOT)) {
                case "after-dragon" -> AFTER_DRAGON;
                case "fixed-date" -> FIXED_DATE;
                case "manual" -> MANUAL;
                default -> ALWAYS;
            };
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public enum Override { NONE, OPEN, CLOSED }

    /** @param date opening time for FIXED_DATE (epoch ms), or null when missing or invalid */
    public record Settings(Mode mode, long delayMillis, Long date) {
    }

    /** @param opensAt when it opens (epoch ms) if it is closed and the time is known, else null */
    public record State(boolean open, Long opensAt, String reason) {
    }

    private SiftAccess() {
    }

    public static State evaluate(Settings settings, long now, Long dragonKill, Override override) {
        if (override == Override.OPEN) {
            return new State(true, null, "per Befehl geöffnet");
        }
        if (override == Override.CLOSED) {
            return new State(false, null, "per Befehl geschlossen");
        }
        return switch (settings.mode()) {
            case ALWAYS -> new State(true, null, "Modus always");
            case MANUAL -> new State(false, null, "Modus manual: nur per Befehl");
            case FIXED_DATE -> settings.date() == null ? new State(false, null, "Modus fixed-date ohne gültiges access.date")
                    : now >= settings.date() ? new State(true, null, "Datum erreicht")
                    : new State(false, settings.date(), "wartet auf das Datum");
            case AFTER_DRAGON -> {
                if (dragonKill == null) {
                    yield new State(false, null, "wartet auf den ersten Drachenkill");
                }
                long opens = dragonKill + settings.delayMillis();
                yield now >= opens ? new State(true, null, "Wartezeit nach dem Drachenkill vorbei")
                        : new State(false, opens, "Wartezeit nach dem Drachenkill");
            }
        };
    }

    /** A dragon killed in this world counts: any End world when the list is empty, otherwise only listed ones. */
    public static boolean countsDragon(String world, boolean endWorld, List<String> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return endWorld;
        }
        return worlds.stream().anyMatch(w -> w.equalsIgnoreCase(world));
    }

    /** "3 T 4 Std. 5 Min." (at least minutes; seconds below a minute). */
    public static String remaining(long millis) {
        return remaining(millis, de.lamamc.sift.core.lang.Lang.DE);
    }

    /** Time left in this language: "3 T 4 Std. 5 Min." / "3 d 4 h 5 min"; under a minute in seconds (alpha.42c). */
    public static String remaining(long millis, de.lamamc.sift.core.lang.Lang lang) {
        boolean german = lang == de.lamamc.sift.core.lang.Lang.DE;
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return seconds + " s";
        }
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        StringBuilder text = new StringBuilder();
        if (days > 0) {
            text.append(days).append(german ? " T " : " d ");
        }
        if (days > 0 || hours > 0) {
            text.append(hours).append(german ? " Std. " : " h ");
        }
        return text.append(minutes).append(german ? " Min." : " min").toString();
    }

    /**
     * An ISO date or date-time ("2026-12-01", "2026-12-01T18:00", "2026-12-01T18:00+01:00") in epoch ms; without an
     * offset the given zone applies. Null for empty or invalid text.
     */
    public static Long parseDate(String text, ZoneId zone) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        try {
            return OffsetDateTime.parse(t).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // no offset
        }
        try {
            return LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // no time
        }
        try {
            return LocalDate.parse(t).atStartOfDay(zone).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String format(long epochMillis, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).withNano(0).toString().replace('T', ' ');
    }
}
