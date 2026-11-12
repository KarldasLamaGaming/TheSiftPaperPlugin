package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CharoiteWispRulesTest {

    private static final CharoiteWispRules R = CharoiteWispRules.defaults();

    @Test
    void hoversAtItsDistanceAboveTheTarget() {
        double[] d = R.desired(new double[] {3, 70, 0}, new double[] {0, 64, 0}, 0);
        assertEquals(10, d[0], 1e-9);
        assertEquals(67, d[1], 1e-9);
        assertEquals(0, d[2], 1e-9);
        double[] circled = R.desired(new double[] {3, 70, 0}, new double[] {0, 64, 0}, 90);
        assertEquals(0, circled[0], 1e-9);
        assertEquals(10, circled[2], 1e-9);
        double[] on = R.desired(new double[] {0, 64, 0}, new double[] {0, 64, 0}, 0);
        assertEquals(10, Math.hypot(on[0], on[2]), 1e-9, "on top of the target: any side");
        assertTrue(R.tooClose(6 * 6));
        assertFalse(R.tooClose(8 * 8));
    }

    @Test
    void shootsOnlyReadyInRangeAndInSight() {
        assertTrue(R.canShoot(0, 15 * 15, true));
        assertFalse(R.canShoot(3, 15 * 15, true));
        assertFalse(R.canShoot(0, 20 * 20, true));
        assertFalse(R.canShoot(0, 5 * 5, false));
    }

    @Test
    void shardsTurnSlowlyTowardsTheTarget() {
        double[] d = R.steer(new double[] {1, 0, 0}, new double[] {0, 0, 1});
        double turned = Math.toDegrees(Math.acos(d[0]));
        assertEquals(4, turned, 1e-6, "at most 4 degrees per tick");
        assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
        double[] near = R.steer(new double[] {1, 0, 0}, new double[] {1, 0.02, 0});
        assertEquals(0.02, near[1] / near[0], 1e-6, "small corrections at once");
    }

    @Test
    void blinkOffsetsLieAtTheBlinkDistance() {
        var offsets = R.blinkOffsets(new SplittableRandom(5), 8);
        assertEquals(8, offsets.size());
        for (double[] o : offsets) {
            assertEquals(4, Math.hypot(o[0], o[2]), 1e-9);
            assertTrue(o[1] >= -0.5 && o[1] <= 1.5);
        }
    }
}
