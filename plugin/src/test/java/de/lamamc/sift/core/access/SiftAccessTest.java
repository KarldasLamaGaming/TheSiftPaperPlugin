package de.lamamc.sift.core.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.access.SiftAccess.Mode;
import de.lamamc.sift.core.access.SiftAccess.Override;
import de.lamamc.sift.core.access.SiftAccess.Settings;
import de.lamamc.sift.core.access.SiftAccess.State;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiftAccessTest {

    private static final long DAY = 86_400_000L;
    private static final Settings AFTER_DRAGON = new Settings(Mode.AFTER_DRAGON, 7 * DAY, null);

    @Test
    void alwaysAndManual() {
        assertTrue(SiftAccess.evaluate(new Settings(Mode.ALWAYS, 0, null), 0, null, Override.NONE).open());
        assertFalse(SiftAccess.evaluate(new Settings(Mode.MANUAL, 0, null), 0, null, Override.NONE).open());
    }

    @Test
    void afterDragonOpensAfterTheDelay() {
        long kill = 1_000 * DAY;
        State waiting = SiftAccess.evaluate(AFTER_DRAGON, kill + 6 * DAY, kill, Override.NONE);
        assertFalse(waiting.open());
        assertEquals(kill + 7 * DAY, waiting.opensAt());
        assertTrue(SiftAccess.evaluate(AFTER_DRAGON, kill + 7 * DAY, kill, Override.NONE).open(), "exactly at the time");
        // no known kill: stays closed, opening time unknown
        State unknown = SiftAccess.evaluate(AFTER_DRAGON, kill, null, Override.NONE);
        assertFalse(unknown.open());
        assertNull(unknown.opensAt());
    }

    @Test
    void fixedDate() {
        Settings settings = new Settings(Mode.FIXED_DATE, 0, 5 * DAY);
        assertFalse(SiftAccess.evaluate(settings, 4 * DAY, null, Override.NONE).open());
        assertEquals(5 * DAY, SiftAccess.evaluate(settings, 4 * DAY, null, Override.NONE).opensAt());
        assertTrue(SiftAccess.evaluate(settings, 5 * DAY, null, Override.NONE).open());
        assertFalse(SiftAccess.evaluate(new Settings(Mode.FIXED_DATE, 0, null), 9 * DAY, null, Override.NONE).open(), "no date");
    }

    @Test
    void commandStateWins() {
        assertTrue(SiftAccess.evaluate(new Settings(Mode.MANUAL, 0, null), 0, null, Override.OPEN).open());
        assertFalse(SiftAccess.evaluate(new Settings(Mode.ALWAYS, 0, null), 0, null, Override.CLOSED).open());
        assertFalse(SiftAccess.evaluate(AFTER_DRAGON, 100 * DAY, 0L, Override.CLOSED).open());
    }

    @Test
    void whichDragonsCount() {
        assertTrue(SiftAccess.countsDragon("world_the_end", true, List.of()));
        assertFalse(SiftAccess.countsDragon("world", false, List.of()), "not an End world");
        assertTrue(SiftAccess.countsDragon("event_end", true, List.of("event_end")));
        assertFalse(SiftAccess.countsDragon("world_the_end", true, List.of("event_end")), "not listed");
        assertTrue(SiftAccess.countsDragon("Event_End", true, List.of("event_end")), "case-insensitive");
    }

    @Test
    void modesAndTexts() {
        assertEquals(Mode.AFTER_DRAGON, Mode.parse("after-dragon"));
        assertEquals(Mode.FIXED_DATE, Mode.parse(" Fixed-Date "));
        assertEquals(Mode.ALWAYS, Mode.parse("nonsense"));
        assertEquals("after-dragon", Mode.AFTER_DRAGON.key());
        assertEquals("3 T 4 Std. 5 Min.", SiftAccess.remaining(3 * DAY + 4 * 3_600_000L + 5 * 60_000L));
        assertEquals("45 s", SiftAccess.remaining(45_000));
        assertEquals("2 Std. 0 Min.", SiftAccess.remaining(2 * 3_600_000L));
        ZoneId utc = ZoneId.of("UTC");
        assertEquals(0L, SiftAccess.parseDate("1970-01-01", utc));
        assertEquals(18 * 3_600_000L, SiftAccess.parseDate("1970-01-01T18:00", utc));
        assertEquals(17 * 3_600_000L, SiftAccess.parseDate("1970-01-01T18:00+01:00", utc));
        assertNull(SiftAccess.parseDate("morgen", utc));
        assertNull(SiftAccess.parseDate("", utc));
        assertEquals("1970-01-01 18:00", SiftAccess.format(18 * 3_600_000L, utc));
    }
}
