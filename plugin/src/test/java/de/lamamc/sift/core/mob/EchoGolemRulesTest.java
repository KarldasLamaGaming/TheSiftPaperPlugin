package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EchoGolemRulesTest {

    private final EchoGolemRules rules = EchoGolemRules.defaults();

    private static EchoGolemMission.Step run(EchoGolemMission mission, int ticks, boolean soul, boolean bonded, boolean owner) {
        EchoGolemMission.Step step = EchoGolemMission.Step.NONE;
        for (int i = 0; i < ticks; i++) {
            step = mission.tick(soul, bonded, owner);
        }
        return step;
    }

    @Test
    void pickupTakesTheSoulAtTick18AndCarriesFrom34() {
        EchoGolemMission mission = new EchoGolemMission(rules);
        mission.set(EchoGolemMission.State.PICKUP);
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 17, false, false, false));
        assertEquals(EchoGolemMission.Step.TAKE_SOUL, mission.tick(false, false, false));
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 15, true, false, false));
        assertEquals(EchoGolemMission.Step.START_CARRY, mission.tick(true, false, false));
        mission.startCarry(false);
        assertEquals(EchoGolemMission.State.CARRY, mission.state());
        assertEquals(EchoGolemMission.Phase.EXIT_CANYON, mission.phase());
        mission.startCarry(true);
        assertEquals(EchoGolemMission.Phase.DELIVER, mission.phase());
    }

    @Test
    void bondedBowGivesAt26AndEndsAt48() {
        EchoGolemMission mission = new EchoGolemMission(rules);
        mission.set(EchoGolemMission.State.BOW);
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 25, true, true, true));
        assertEquals(EchoGolemMission.Step.GIVE_TO_OWNER, mission.tick(true, true, true));
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 21, false, true, true));
        assertEquals(EchoGolemMission.Step.END, mission.tick(false, true, true));
    }

    @Test
    void bondedBowResumesCarryWhenThePlayerWalksAway() {
        EchoGolemMission mission = new EchoGolemMission(rules);
        mission.set(EchoGolemMission.State.BOW);
        assertEquals(EchoGolemMission.Step.RESUME_CARRY, run(mission, 5, true, true, false));
    }

    @Test
    void unansweredBowToASingerTimesOut() {
        EchoGolemMission mission = new EchoGolemMission(rules);
        mission.set(EchoGolemMission.State.BOW);
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 99, true, false, false));
        assertEquals(EchoGolemMission.Step.RESUME_CARRY, mission.tick(true, false, false));
        mission.set(EchoGolemMission.State.BOW);
        assertEquals(EchoGolemMission.Step.END, run(mission, 100, false, false, false));
    }

    @Test
    void sittingLasts600Ticks() {
        EchoGolemMission mission = new EchoGolemMission(rules);
        mission.set(EchoGolemMission.State.SIT);
        assertEquals(EchoGolemMission.Step.NONE, run(mission, 599, false, false, false));
        assertEquals(EchoGolemMission.Step.END, mission.tick(false, false, false));
        mission.resumeSearch();
        assertEquals(EchoGolemMission.State.SEARCH, mission.state());
        assertEquals(EchoGolemMission.Phase.EXPLORE, mission.phase());
    }

    @Test
    void surfaceTest() {
        assertTrue(EchoGolemRules.onSurface(70, true, 0, 0, false));
        assertFalse(EchoGolemRules.onSurface(70, false, 0, 0, false));
        // deep in the canyon: below the entrance
        assertFalse(EchoGolemRules.onSurface(80, true, 90, 5, true));
        // near the entrance without sky (overhang) still counts
        assertTrue(EchoGolemRules.onSurface(89, false, 90, 5, true));
        assertFalse(EchoGolemRules.onSurface(89, false, 90, 20, true));
    }

    @Test
    void failureMemoryGrowsAndForgets() {
        SoulFailureMemory memory = new SoulFailureMemory();
        memory.fail(1L, 0, 100);
        assertTrue(memory.rejected(1L, 99));
        assertFalse(memory.rejected(1L, 100));
        memory.fail(1L, 100, 100);
        assertEquals(2, memory.failures(1L));
        assertTrue(memory.rejected(1L, 499));
        memory.fail(1L, 500, 100);
        assertTrue(memory.rejected(1L, 500 + 2399));
        // the minimum penalty wins over a shorter step
        memory.fail(2L, 0, 200);
        assertTrue(memory.rejected(2L, 199));
        // forgotten 12000 ticks after the retry time
        assertFalse(memory.rejected(1L, 500 + 2400 + 12000));
        assertEquals(0, memory.failures(1L));
    }

    @Test
    void lightMovesOnlyOnBlockChangeAndOnlyIntoAirAndOnlyOurs() {
        long[] a = {1, 70, 1};
        assertEquals(GolemLightRules.Plan.NOTHING, GolemLightRules.plan(a, new long[] {1, 70, 1}));
        assertEquals(new GolemLightRules.Plan(true, true), GolemLightRules.plan(a, new long[] {2, 70, 1}));
        assertEquals(new GolemLightRules.Plan(true, false), GolemLightRules.plan(a, null));
        assertEquals(new GolemLightRules.Plan(false, true), GolemLightRules.plan(null, a));
        assertEquals(GolemLightRules.Plan.NOTHING, GolemLightRules.plan(null, null));
        assertTrue(GolemLightRules.canPlaceInto(true));
        assertFalse(GolemLightRules.canPlaceInto(false));
        assertTrue(GolemLightRules.mayRemove(true, 14, 14, true));
        assertFalse(GolemLightRules.mayRemove(true, 15, 14, true), "someone else's light block");
        assertFalse(GolemLightRules.mayRemove(true, 14, 14, false), "not registered");
        assertFalse(GolemLightRules.mayRemove(false, 14, 14, true), "replaced by another block");
        assertEquals(72, GolemLightRules.headBlockY(70.0, 1.0));
    }
}
