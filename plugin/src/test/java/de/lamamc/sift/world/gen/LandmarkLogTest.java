package de.lamamc.sift.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class LandmarkLogTest {

    @Test
    void entriesSurviveTheStoredText() {
        LandmarkLog.Entry built = new LandmarkLog.Entry(-14, 6, LandmarkLog.Status.BUILT, -219, 90, 94, "abandoned_portal_overgrown_1");
        LandmarkLog.Entry rejected = new LandmarkLog.Entry(-27, 10, LandmarkLog.Status.REJECTED, -424, 95, 168,
                "keine der 4 Drehungen passt: Gelände zu steil (Höhenunterschied 29 > 18); Platz belegt durch the_sift:x");
        assertEquals(built, LandmarkLog.Entry.parse(built.serialize()));
        assertEquals(rejected, LandmarkLog.Entry.parse(rejected.serialize()));
        LandmarkLog.Entry noReason = new LandmarkLog.Entry(1, 2, LandmarkLog.Status.REJECTED, 3, 4, 5, null);
        assertEquals(noReason, LandmarkLog.Entry.parse(noReason.serialize()));
        assertNull(LandmarkLog.Entry.parse("garbage"));
        assertNull(LandmarkLog.Entry.parse("1 2 UNKNOWN 3 4 5 x"));
    }

    @Test
    void builtEntriesComeFirst() {
        LandmarkLog log = new LandmarkLog();
        log.record(new LandmarkLog.Entry(5, 5, LandmarkLog.Status.REJECTED, 0, 0, 0, "a"));
        log.record(new LandmarkLog.Entry(9, 9, LandmarkLog.Status.BUILT, 0, 0, 0, "b"));
        log.record(new LandmarkLog.Entry(-3, 0, LandmarkLog.Status.REJECTED, 0, 0, 0, "c"));
        assertEquals(List.of("b", "c", "a"), log.all().stream().map(LandmarkLog.Entry::reason).toList());
        assertEquals("b", log.get(9, 9).reason());
    }
}
