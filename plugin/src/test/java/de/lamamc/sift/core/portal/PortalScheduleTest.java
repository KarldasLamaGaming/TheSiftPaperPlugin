package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.access.SiftAccess;
import de.lamamc.sift.core.portal.PortalSchedule.Group;
import de.lamamc.sift.core.portal.PortalSchedule.Override;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalScheduleTest {

    private static final long DAY = 86_400_000L;
    private static final PortalSchedule.Settings KARL = new PortalSchedule.Settings(true,
            Map.of(Group.END, 0.0, Group.ANCIENT_CITY, 0.0, Group.NETHER, 7.0, Group.OVERWORLD, 14.0));

    @Test
    void groupsOpenDaysAfterTheSift() {
        long opened = 1_000 * DAY;
        assertTrue(PortalSchedule.status(KARL, Group.END, opened, opened, Override.NONE).unlocked());
        assertTrue(PortalSchedule.status(KARL, Group.ANCIENT_CITY, opened, opened, Override.NONE).unlocked());
        var nether = PortalSchedule.status(KARL, Group.NETHER, opened + 6 * DAY, opened, Override.NONE);
        assertFalse(nether.unlocked());
        assertEquals(opened + 7 * DAY, nether.at());
        assertTrue(PortalSchedule.status(KARL, Group.NETHER, opened + 7 * DAY, opened, Override.NONE).unlocked());
        assertFalse(PortalSchedule.status(KARL, Group.OVERWORLD, opened + 13 * DAY, opened, Override.NONE).unlocked());
        assertTrue(PortalSchedule.status(KARL, Group.OVERWORLD, opened + 14 * DAY, opened, Override.NONE).unlocked());
    }

    @Test
    void beforeTheSiftOpensEverythingIsSealedAndOverridesWin() {
        var none = PortalSchedule.status(KARL, Group.END, 0, null, Override.NONE);
        assertFalse(none.unlocked());
        assertNull(none.at());
        assertTrue(PortalSchedule.status(KARL, Group.OVERWORLD, 0, null, Override.UNLOCKED).unlocked());
        assertFalse(PortalSchedule.status(KARL, Group.END, 99 * DAY, 0L, Override.LOCKED).unlocked());
        assertTrue(PortalSchedule.status(new PortalSchedule.Settings(false, Map.of()), Group.OVERWORLD, 0, null, Override.NONE).unlocked());
        assertTrue(PortalSchedule.status(KARL, null, 0, null, Override.NONE).unlocked(), "main portal (no group) is never sealed");
    }

    @Test
    void portalsFallIntoTheirGroups() {
        assertEquals(Group.ANCIENT_CITY, Group.of(true, false, "NORMAL"));
        assertEquals(Group.OVERWORLD, Group.of(false, false, "NORMAL"));
        assertEquals(Group.NETHER, Group.of(false, false, "NETHER"));
        assertEquals(Group.END, Group.of(false, false, "THE_END"));
        assertNull(Group.of(false, true, "CUSTOM"), "Sift exit");
        assertEquals(Group.ANCIENT_CITY, Group.of("ancient-city"));
        assertNull(Group.of("sift_exit"));
    }

    @Test
    void dayZeroComesFromTheAccessModeUnlessAnAdminSetIt() {
        var fixed = new SiftAccess.Settings(SiftAccess.Mode.FIXED_DATE, 0, 50 * DAY);
        var dragon = new SiftAccess.Settings(SiftAccess.Mode.AFTER_DRAGON, 7 * DAY, null);
        var always = new SiftAccess.Settings(SiftAccess.Mode.ALWAYS, 0, null);
        assertEquals(50 * DAY, PortalSchedule.openedAt(fixed, null, 3L, null));
        assertEquals(17 * DAY, PortalSchedule.openedAt(dragon, 10 * DAY, 3L, null));
        assertEquals(3L, PortalSchedule.openedAt(always, null, 3L, null));
        assertNull(PortalSchedule.openedAt(always, null, null, null));
        assertEquals(9L, PortalSchedule.openedAt(fixed, null, 3L, 9L));
    }
}
