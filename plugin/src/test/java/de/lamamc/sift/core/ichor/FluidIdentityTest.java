package de.lamamc.sift.core.ichor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FluidIdentityTest {

    @Test
    void theDefaultDependsOnTheWorldAndAMarkFlipsIt() {
        assertTrue(FluidIdentity.isIchor(true, false), "water in the Sift is ichor");
        assertFalse(FluidIdentity.isIchor(false, false), "water elsewhere is water");
        assertFalse(FluidIdentity.isIchor(true, true), "marked: plain water poured into the Sift");
        assertTrue(FluidIdentity.isIchor(false, true), "marked: ichor poured into the overworld");
    }

    @Test
    void onlyExceptionsGetAMark() {
        assertFalse(FluidIdentity.markFor(true, true));
        assertTrue(FluidIdentity.markFor(true, false));
        assertTrue(FluidIdentity.markFor(false, true));
        assertFalse(FluidIdentity.markFor(false, false));
    }

    @Test
    void aBucketEmptiedIntoExistingWaterChangesNothing() {
        assertNull(FluidIdentity.onEmpty(true, false, true));
        assertEquals(Boolean.TRUE, FluidIdentity.onEmpty(true, false, false), "water bucket in the Sift");
        assertEquals(Boolean.FALSE, FluidIdentity.onEmpty(true, true, false), "ichor bucket in the Sift: default");
        assertEquals(Boolean.TRUE, FluidIdentity.onEmpty(false, true, false), "ichor bucket in the overworld");
    }

    @Test
    void cauldronsDoNotMix() {
        assertFalse(FluidIdentity.mixes(0, false, true), "empty cauldron takes anything");
        assertTrue(FluidIdentity.mixes(2, false, true));
        assertFalse(FluidIdentity.mixes(2, true, true));
    }

    @Test
    void positionsPackAndUnpack() {
        for (int[] p : new int[][] {{0, -64, 0}, {15, 319, 15}, {7, 0, 9}, {3, -2048, 12}, {12, 2047, 1}}) {
            int packed = FluidIdentity.pack(p[0], p[1], p[2]);
            assertEquals(p[0], FluidIdentity.localX(packed));
            assertEquals(p[1], FluidIdentity.y(packed));
            assertEquals(p[2], FluidIdentity.localZ(packed));
        }
        assertEquals(FluidIdentity.pack(1, 70, 2), FluidIdentity.pack(17, 70, -14), "world coordinates map into the chunk");
    }
}
