package de.lamamc.sift.core.portal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortalLogicTest {

    // ---- melody -----------------------------------------------------------------------------------

    private static PortalMelody.Match playAll(PortalMelody melody, int... notes) {
        PortalMelody.Match last = PortalMelody.Match.NONE;
        for (int note : notes) {
            last = melody.play(note);
        }
        return last;
    }

    @Test
    void theOriginalTuneOpensAndItsReverseCloses() {
        PortalMelody melody = new PortalMelody(PortalMelody.DEFAULT_SEQUENCE);
        assertEquals(PortalMelody.Match.OPENING, playAll(melody, 1, 3, 7, 6, 5, 2, 4, 8));
        assertEquals(PortalMelody.Match.CLOSING, playAll(melody, 8, 4, 2, 5, 6, 7, 3, 1));
    }

    @Test
    void wrongNotesBeforeTheTuneDoNotMatterButInsideTheyDo() {
        PortalMelody melody = new PortalMelody(PortalMelody.DEFAULT_SEQUENCE);
        assertEquals(PortalMelody.Match.OPENING, playAll(melody, 2, 2, 8, 1, 3, 7, 6, 5, 2, 4, 8)); // only the last 8 count
        assertEquals(PortalMelody.Match.NONE, playAll(melody, 1, 3, 7, 6, 5, 2, 4, 7));
        assertEquals(PortalMelody.Match.NONE, playAll(melody, 1, 3, 7, 6, 4, 5, 2, 8));
        assertEquals(PortalMelody.Match.NONE, playAll(melody, 3, 7, 6, 5, 2, 4, 8)); // one note short
    }

    @Test
    void aMatchStartsTheNextTuneFromScratch() {
        PortalMelody melody = new PortalMelody(PortalMelody.DEFAULT_SEQUENCE);
        assertEquals(PortalMelody.Match.OPENING, playAll(melody, 1, 3, 7, 6, 5, 2, 4, 8));
        assertEquals(PortalMelody.Match.NONE, melody.play(8)); // must not reuse the notes of the last tune
        assertThrows(IllegalArgumentException.class, () -> new PortalMelody(new int[] {1, 9}));
        assertThrows(IllegalArgumentException.class, () -> new PortalMelody(new int[0]));
    }

    // ---- frame -----------------------------------------------------------------------------------

    /** A frame of {@code width × height} along x at y 64, plus a few stray frame blocks. */
    private static PortalFrame.Grid grid(Set<String> frame, Set<String> blocked) {
        return new PortalFrame.Grid() {
            @Override
            public boolean isFrame(int x, int y, int z) {
                return frame.contains(x + "," + y + "," + z);
            }

            @Override
            public boolean isOpen(int x, int y, int z) {
                return !isFrame(x, y, z) && !blocked.contains(x + "," + y + "," + z);
            }
        };
    }

    private static Set<String> rectangle(int x0, int y0, int z0, int width, int height, boolean alongX) {
        Set<String> blocks = new HashSet<>();
        for (int w = 0; w < width; w++) {
            for (int h = 0; h < height; h++) {
                if (w == 0 || w == width - 1 || h == 0 || h == height - 1) {
                    blocks.add((alongX ? x0 + w : x0) + "," + (y0 + h) + "," + (alongX ? z0 : z0 + w));
                }
            }
        }
        return blocks;
    }

    @Test
    void scannerFindsTheFrameFromTheBottomAndTheSides() {
        // like the original: it walks down and to the low side first, so it starts from the bottom row or a
        // side column; callers try every frame block around the console
        Set<String> frame = rectangle(10, 64, 5, 7, 9, true);
        for (String block : frame) {
            String[] p = block.split(",");
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            Optional<PortalFrame> found = PortalFrame.scan(grid(frame, Set.of()), x, y, Integer.parseInt(p[2]));
            if (y == 64 || x == 10 || x == 16) {
                assertEquals(Optional.of(new PortalFrame(PortalFrame.Axis.X, 10, 64, 5, 7, 9)), found, block);
            }
        }
    }

    @Test
    void scannerWorksAlongZAndRejectsBrokenFrames() {
        Set<String> frame = rectangle(0, 70, -3, 4, 5, false);
        assertEquals(new PortalFrame(PortalFrame.Axis.Z, 0, 70, -3, 4, 5),
                PortalFrame.scan(grid(frame, Set.of()), 0, 70, -3).orElseThrow());
        assertFalse(PortalFrame.scan(grid(frame, Set.of("0,72,-2")), 0, 70, -3).isPresent()); // blocked interior
        Set<String> broken = new HashSet<>(frame);
        broken.remove("0,74,-1"); // gap in the top
        assertFalse(PortalFrame.scan(grid(broken, Set.of()), 0, 70, -3).isPresent());
        assertFalse(PortalFrame.scan(grid(rectangle(0, 0, 0, 2, 5, true), Set.of()), 0, 0, 0).isPresent()); // too narrow
        assertFalse(PortalFrame.scan(grid(rectangle(0, 0, 0, 30, 5, true), Set.of()), 0, 0, 0).isPresent()); // too wide
    }

    @Test
    void surfaceFillsFromTheEdgesToTheCentre() {
        PortalFrame frame = new PortalFrame(PortalFrame.Axis.X, 0, 0, 0, 7, 9);
        List<int[]> order = frame.interiorEdgesFirst();
        assertEquals(35, order.size());
        assertEquals(frame.interiorSize(), order.size());
        int[] last = order.getLast();
        assertArrayEquals(new int[] {3, 4, 0}, last); // centre of a 5 × 7 interior
        order.forEach(p -> assertTrue(frame.containsInterior(p[0], p[1], p[2])));
        assertFalse(frame.containsInterior(0, 4, 0));
        assertTrue(frame.containsFrame(0, 4, 0));
        assertFalse(frame.containsInterior(3, 4, 1));
        assertArrayEquals(new double[] {3.5, 1.0, 0.5}, frame.bottomCenter());
        assertEquals(frame, PortalFrame.parse(frame.serialize()));
    }

    // ---- state, transit, return point ------------------------------------------------------------

    @Test
    void portalsOpenOnlyWhenUnlockedAndStayOpen() {
        assertEquals(PortalState.LOCKED, PortalState.LOCKED.onTune(PortalMelody.Match.OPENING));
        assertEquals(PortalState.UNLOCKED, PortalState.LOCKED.unlock());
        assertEquals(PortalState.OPEN, PortalState.UNLOCKED.onTune(PortalMelody.Match.OPENING));
        assertEquals(PortalState.UNLOCKED, PortalState.UNLOCKED.onTune(PortalMelody.Match.CLOSING));
        assertEquals(PortalState.OPEN, PortalState.OPEN.onTune(PortalMelody.Match.CLOSING)); // permanently open
        assertEquals(PortalState.OPEN, PortalState.OPEN.unlock());
        assertEquals(PortalState.UNLOCKED, PortalState.OPEN.deactivate());
        assertEquals(PortalState.OPEN, PortalState.LOCKED.activate());
    }

    @Test
    void arrivalsMustStepOutBeforeTravellingAgain() {
        TransitGuard guard = new TransitGuard();
        UUID id = UUID.randomUUID();
        assertFalse(guard.shouldTransfer(id, false));
        assertTrue(guard.shouldTransfer(id, true));
        guard.arrived(id);
        assertFalse(guard.shouldTransfer(id, true)); // still standing in the arrival portal
        assertFalse(guard.shouldTransfer(id, true));
        assertFalse(guard.shouldTransfer(id, false)); // stepped out
        assertTrue(guard.shouldTransfer(id, true)); // walks in again
        UUID other = UUID.randomUUID();
        guard.arrived(id);
        guard.arrived(other);
        assertEquals(Set.of(other), guard.releaseAllExcept(Set.of(id))); // other stepped out (or left the world)
        assertTrue(guard.waiting(id));
        assertFalse(guard.waiting(other));
    }

    @Test
    void returnPointsSurviveStorage() {
        ReturnPoint point = new ReturnPoint("minecraft:overworld", -120.5, 64.0, 33.25, 90.0f, -10.5f);
        assertEquals(point, ReturnPoint.parse(point.serialize()));
        assertNull(ReturnPoint.parse(null));
        assertNull(ReturnPoint.parse("minecraft:overworld 1 2"));
        assertNull(ReturnPoint.parse("w a b c d e"));
    }
}
