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
    void exactlyOneAbandonedPortalChunkPerCellInTheMiddleThird() {
        for (int cellX = -2; cellX <= 2; cellX++) {
            for (int cellZ = -2; cellZ <= 2; cellZ++) {
                int selected = 0;
                for (int dx = 0; dx < 20; dx++) {
                    for (int dz = 0; dz < 20; dz++) {
                        if (LandmarkPlacement.isSelectedChunk(SEED, cellX * 20 + dx, cellZ * 20 + dz, 20,
                                LandmarkPlacement.ABANDONED_PORTAL_SALT)) {
                            selected++;
                            // inset 6, spread 8: offsets 6..13, so candidates are at least 12 chunks apart
                            assertTrue(dx >= 6 && dx <= 13 && dz >= 6 && dz <= 13, "offset " + dx + "/" + dz);
                        }
                    }
                }
                assertEquals(1, selected, "cell " + cellX + "/" + cellZ);
            }
        }
        assertNotEquals(LandmarkPlacement.candidateChunk(SEED, 0, 0, 20, LandmarkPlacement.ABANDONED_PORTAL_SALT, 0),
                LandmarkPlacement.candidateChunk(SEED + 1, 0, 0, 20, LandmarkPlacement.ABANDONED_PORTAL_SALT, 0));
    }

    @Test
    void abandonedPortalCandidatesMatchTheOriginal() {
        // chunks where The Sift 1.1.1 (Fabric) built abandoned portals with level-seed "lamakingdom"
        long seed = "lamakingdom".hashCode();
        int[][] built = {{-74, 106}, {-71, 28}, {-30, -27}, {-13, -87}};
        for (int[] chunk : built) {
            assertTrue(LandmarkPlacement.isSelectedChunk(seed, chunk[0], chunk[1], 20, LandmarkPlacement.ABANDONED_PORTAL_SALT),
                    chunk[0] + "/" + chunk[1]);
        }
    }

    @Test
    void soulCanyonCellsAreShiftedByHalfACell() {
        long salt = LandmarkPlacement.SOUL_CANYON_SALT;
        LandmarkPlacement.ChunkPos chunk = LandmarkPlacement.candidateChunk(SEED, 0, 0, 12, salt, 0);
        assertTrue(chunk.x() >= 6 + 4 && chunk.x() <= 6 + 7, "x " + chunk.x());
        assertTrue(LandmarkPlacement.isSelectedChunk(SEED, chunk.x(), chunk.z(), 12, salt));
        assertEquals(0, LandmarkPlacement.cellOf(chunk.x(), 12, salt));
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
    void abandonedPortalIsCentredAndSitsOnTheMedian() {
        assertEquals(-4, AbandonedPortalPlan.centredOrigin(0, 0, 8)); // 9 wide: x 0..8 → -4..4
        assertEquals(-20, AbandonedPortalPlan.centredOrigin(0, 0, 39)); // 40 long: -20..19
        assertEquals(8, AbandonedPortalPlan.centredOrigin(8, -4, 3));
        List<Integer> heights = List.of(60, 61, 61, 64, 78);
        assertEquals(18, AbandonedPortalPlan.relief(heights));
        assertEquals(62, AbandonedPortalPlan.groundY(heights));
        assertTrue(AbandonedPortalPlan.coreFits(70, 62));
        assertFalse(AbandonedPortalPlan.coreFits(70, 61));
    }

    @Test
    void foundationMovesTerrainAtMostEightBlocks() {
        assertEquals(70, AbandonedPortalPlan.foundationTop(true, 0, 12, 70, 50, 10)); // under the template: the floor
        assertEquals(58, AbandonedPortalPlan.foundationTop(false, 0, 12, 70, 50, 10)); // clamped to +8
        assertEquals(50, AbandonedPortalPlan.foundationTop(false, 12, 12, 70, 50, 10)); // edge: unchanged
        assertEquals(50, AbandonedPortalPlan.foundationTop(false, 1, 12, 70, 50, 0)); // at the generation bounds
        int half = AbandonedPortalPlan.foundationTop(false, 6, 12, 70, 66, 10);
        assertEquals(68, half); // smoothstep(0.5) = 0.5 of +4
        double edge = AbandonedPortalPlan.foundationEdge(3, 4, 7L);
        assertTrue(edge >= 12 - 1.35 && edge <= 12 + 1.35);
    }

    @Test
    void packedPositionsLikeVanilla() {
        assertEquals(0L, AbandonedPortalPlan.packPos(0, 0, 0));
        assertEquals(1L << 38, AbandonedPortalPlan.packPos(1, 0, 0));
        assertEquals(1L << 12, AbandonedPortalPlan.packPos(0, 0, 1));
        assertEquals(0xFFFL, AbandonedPortalPlan.packPos(0, -1, 0));
        assertEquals(-1L, AbandonedPortalPlan.packPos(-1, -1, -1));
    }

    @Test
    void portalChanceThinsCandidatesDeterministically() {
        int passed = 0;
        int total = 0;
        for (int x = -100; x < 100; x++) {
            for (int z = -100; z < 100; z++) {
                total++;
                if (AbandonedPortalPlan.passesChance(SEED, x, z, 0.25)) {
                    passed++;
                }
                assertEquals(AbandonedPortalPlan.passesChance(SEED, x, z, 0.25), AbandonedPortalPlan.passesChance(SEED, x, z, 0.25));
                assertTrue(AbandonedPortalPlan.passesChance(SEED, x, z, 1.0));
                assertFalse(AbandonedPortalPlan.passesChance(SEED, x, z, 0.0));
            }
        }
        assertEquals(0.25, passed / (double) total, 0.01);
        // a chunk that passes at 0.25 also passes at any higher chance
        for (int x = 0; x < 50; x++) {
            if (AbandonedPortalPlan.passesChance(SEED, x, 7, 0.25)) {
                assertTrue(AbandonedPortalPlan.passesChance(SEED, x, 7, 0.5));
            }
        }
    }

    @Test
    void bundledConfigUsesTheDefaultChance() throws java.io.IOException {
        try (var in = WorldgenMathTest.class.getResourceAsStream("/config.yml")) {
            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(AbandonedPortalPlan.DEFAULT_CHANCE, config.getDouble("worldgen.abandoned-portals.chance"));
            assertEquals(AbandonedPortalPlan.CELL_CHUNKS, config.getInt("worldgen.abandoned-portals.cell-chunks"));
        }
    }
}
