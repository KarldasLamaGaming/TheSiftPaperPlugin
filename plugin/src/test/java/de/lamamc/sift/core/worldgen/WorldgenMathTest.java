package de.lamamc.sift.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.worldgen.TemplateTransform.Mirror;
import de.lamamc.sift.core.worldgen.TemplateTransform.Rotation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldgenMathTest {

    private static final long SEED = 123456789L;

    @Test
    void exactlyOneAbandonedPortalChunkPerCell() {
        for (int cellX = -2; cellX <= 2; cellX++) {
            for (int cellZ = -2; cellZ <= 2; cellZ++) {
                int selected = 0;
                for (int dx = 0; dx < 32; dx++) {
                    for (int dz = 0; dz < 32; dz++) {
                        if (LandmarkPlacement.isSelectedChunk(SEED, cellX * 32 + dx, cellZ * 32 + dz, 32,
                                LandmarkPlacement.ABANDONED_PORTAL_SALT)) {
                            selected++;
                        }
                    }
                }
                assertEquals(1, selected, "cell " + cellX + "/" + cellZ);
            }
        }
        assertNotEquals(LandmarkPlacement.candidateChunk(SEED, 0, 0, 32, LandmarkPlacement.ABANDONED_PORTAL_SALT, 0),
                LandmarkPlacement.candidateChunk(SEED + 1, 0, 0, 32, LandmarkPlacement.ABANDONED_PORTAL_SALT, 0));
    }

    @Test
    void sculkRegionsAreDeterministicAndSizedLikeTheOriginal() {
        for (int cell = 0; cell < 20; cell++) {
            SculkRegions.Region region = SculkRegions.create(SEED, cell, -cell);
            SculkRegions.Region again = SculkRegions.create(SEED, cell, -cell);
            assertEquals(region.centerX(), again.centerX());
            assertTrue(region.majorRadius() >= 25 && region.majorRadius() <= 60, "radius " + region.majorRadius());
            assertTrue(region.blobs().length >= 6 && region.blobs().length <= 11);
            int cx = (int) Math.floor(region.centerX());
            int cz = (int) Math.floor(region.centerZ());
            assertTrue(region.contains(cx, cz), "first blob sits at the center");
            assertFalse(region.isVeinFringe(cx, cz) && !region.contains(cx, cz));
            int depth = region.sculkDepth(cx, cz);
            assertTrue(depth >= 6 && depth <= 9);
            // the chunk containing the center sees this region
            List<SculkRegions.Region> touching = SculkRegions.regionsTouching(SEED, Math.floorDiv(cx, 16) * 16, Math.floorDiv(cz, 16) * 16);
            assertTrue(touching.stream().anyMatch(r -> r.seed() == region.seed()));
        }
    }

    @Test
    void sculkDecorationsAreRare() {
        SculkRegions.Region region = SculkRegions.create(SEED, 3, 4);
        int columns = 0;
        int special = 0;
        for (int x = (int) region.centerX() - 60; x <= region.centerX() + 60; x++) {
            for (int z = (int) region.centerZ() - 60; z <= region.centerZ() + 60; z++) {
                if (region.contains(x, z)) {
                    columns++;
                    SculkRegions.Decoration d = region.decoration(x, z);
                    if (d == SculkRegions.Decoration.CATALYST || d == SculkRegions.Decoration.SHRIEKER
                            || d == SculkRegions.Decoration.SENSOR) {
                        special++;
                    }
                }
            }
        }
        assertTrue(columns > 300, "columns " + columns);
        double share = (double) special / columns;
        assertTrue(share > 0.005 && share < 0.05, "catalyst/shrieker/sensor share " + share); // 2.1 % expected
    }

    @Test
    void templateTransformMatchesVanilla() {
        TemplateTransform cw = new TemplateTransform(Mirror.NONE, Rotation.CLOCKWISE_90, 0, 0);
        assertArrayEquals(new int[] {0, 0, 1}, cw.apply(1, 0, 0)); // east becomes south
        assertEquals("south", cw.direction("east"));
        assertEquals("z", cw.axis("x"));
        assertEquals("y", cw.axis("y"));
        TemplateTransform pivoted = new TemplateTransform(Mirror.NONE, Rotation.CLOCKWISE_180, 3, 6);
        assertArrayEquals(new int[] {3, 2, 6}, pivoted.apply(3, 2, 6)); // the pivot stays
        TemplateTransform mirror = new TemplateTransform(Mirror.LEFT_RIGHT, Rotation.NONE, 0, 0);
        assertArrayEquals(new int[] {1, 0, -2}, mirror.apply(1, 0, 2));
        assertEquals("south", mirror.direction("north"));
        assertEquals("x", mirror.axis("x"));
        assertEquals(Map.of("axis", "x"), cw.properties(Map.of("axis", "z")));
        TemplateTransform ccw = new TemplateTransform(Mirror.NONE, Rotation.COUNTERCLOCKWISE_90, 0, 0);
        int[] there = cw.apply(5, 1, 7);
        int[] back = ccw.apply(there[0], there[1], there[2]);
        assertArrayEquals(new int[] {5, 1, 7}, back);
    }

    @Test
    void mainPortalPicksTheFlattestSiteNearTheCenter() {
        MainPortalPlan.Site flat = MainPortalPlan.findSite((x, z) -> 80);
        assertEquals(0, flat.anchorX());
        assertEquals(0, flat.anchorZ());
        assertEquals(80, flat.groundY());
        assertEquals(0.0, flat.roughness(), 1e-9);
        // steep slope except for a plateau off-center: the site lies on the plateau, its footprint is flat
        MainPortalPlan.Heights heights = (x, z) -> Math.abs(x - 60) <= 30 && Math.abs(z - 60) <= 50 ? 100 : 100 + Math.abs(x - 60) * 2;
        MainPortalPlan.Site plateau = MainPortalPlan.findSite(heights);
        assertEquals(0, MainPortalPlan.sample(heights, plateau.anchorX(), plateau.anchorZ()).range(), plateau.toString());
        assertEquals(100, plateau.groundY());
        assertEquals(169 * 17, MainPortalPlan.sampleColumns().size());
    }

    @Test
    void mainPortalTerrainShapes() {
        assertEquals(3, MainPortalPlan.groundProfile(3, 19));
        assertEquals(-1, MainPortalPlan.groundProfile(0, 0));
        assertTrue(MainPortalPlan.isBaseColumn(4, 20));
        assertEquals(70, MainPortalPlan.foundationTargetY(4, 20, SEED, 70));
        assertEquals(Integer.MIN_VALUE, MainPortalPlan.foundationTargetY(-40, 20, SEED, 70));
        int near = MainPortalPlan.foundationTargetY(-3, 20, SEED, 70);
        assertTrue(near < 70 && near > 55, "foundation slopes down: " + near);
        assertEquals(0.0, MainPortalPlan.approachMask(4, 20));
        assertTrue(MainPortalPlan.envelopeRelativeY(4, -2, SEED) > 5, "cliffs at the ends of the portal");
        assertTrue(MainPortalPlan.envelopeRelativeY(-2, 20, SEED) < 5, "low forecourt in front of the opening");
        assertTrue(MainPortalPlan.envelopeRelativeY(-20, 20, SEED) > MainPortalPlan.envelopeRelativeY(-2, 20, SEED));
        assertTrue(MainPortalPlan.envelopeRelativeY(-70, 20, SEED) <= 0, "flat far away");
        assertEquals(5.0, MainPortalPlan.distanceOutsideRectangle(-3, 2, 0, 8, 6, 45), 1e-9);
    }

    @Test
    void abandonedPortalNeedsModestRelief() {
        assertTrue(AbandonedPortalPlan.terrainFits((x, z) -> 70, 0, 0, Rotation.NONE));
        assertFalse(AbandonedPortalPlan.terrainFits((x, z) -> 70 + z, 0, 0, Rotation.NONE)); // 32 blocks along z
        assertTrue(AbandonedPortalPlan.terrainFits((x, z) -> 70 + z / 2, 0, 0, Rotation.CLOCKWISE_90)); // 4 blocks across
        assertEquals(63, AbandonedPortalPlan.foundationTargetY(0, 0, 0, true, 64, 7L));
        assertTrue(AbandonedPortalPlan.foundationTargetY(5, 0, 5, false, 64, 7L) < 63);
        assertEquals(Integer.MIN_VALUE, AbandonedPortalPlan.foundationTargetY(0, 0, 30, false, 64, 7L));
    }
}
