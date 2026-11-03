package de.lamamc.sift.world.portal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SiftPortalTest {

    private static final PortalFrame FRAME = new PortalFrame(PortalFrame.Axis.X, 10, 64, 20, 7, 8);

    @Test
    void ownTuneSurvivesSaving() {
        SiftPortal portal = new SiftPortal("shrine-1", SiftPortal.Kind.ENTRANCE, FRAME, PortalState.LOCKED,
                List.of(new int[] {1, 2, 3}), new int[] {4, 2, 7, 1, 8, 3, 6, 5});
        SiftPortal back = SiftPortal.parse(portal.serialize());
        assertNotNull(back);
        assertArrayEquals(portal.tune(), back.tune());
        assertEquals(PortalState.LOCKED, back.state());
        // a state change keeps the tune
        assertArrayEquals(portal.tune(), back.withState(PortalState.UNLOCKED).tune());
    }

    @Test
    void oldEntriesWithoutTuneStillLoad() {
        SiftPortal old = new SiftPortal("shrine-2", SiftPortal.Kind.ENTRANCE, FRAME, PortalState.UNLOCKED, List.of(new int[] {1, 2, 3}));
        String line = old.serialize();
        assertEquals(2, line.chars().filter(c -> c == '|').count(), "no tune segment: " + line);
        SiftPortal back = SiftPortal.parse(line);
        assertNotNull(back);
        assertNull(back.tune());
        // a broken tune segment is ignored, not fatal
        assertNull(SiftPortal.parse(line + " | tune 1-9-3").tune());
    }
}
