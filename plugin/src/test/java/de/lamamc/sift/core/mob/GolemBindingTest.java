package de.lamamc.sift.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.mob.GolemBinding.Result;
import org.junit.jupiter.api.Test;

class GolemBindingTest {

    @Test
    void echoShardBindsAnUnbondedGolemInEveryState() {
        // searching or sitting (no soul) and carrying: always bound
        assertEquals(Result.BIND, GolemBinding.decide(true, false, false, false));
        assertEquals(Result.BIND, GolemBinding.decide(true, false, false, true));
        assertTrue(GolemBinding.consumesShard(Result.BIND, false));
        assertFalse(GolemBinding.consumesShard(Result.BIND, true), "creative keeps the shard");
    }

    @Test
    void echoShardOnABondedGolemDoesNothing() {
        assertEquals(Result.ALREADY_BONDED, GolemBinding.decide(true, true, false, true));
        assertEquals(Result.ALREADY_BONDED, GolemBinding.decide(true, true, true, false));
        assertFalse(GolemBinding.consumesShard(Result.ALREADY_BONDED, false));
    }

    @Test
    void otherClicksDropTheSoulOnlyForTheRightPlayer() {
        assertEquals(Result.RELEASE_SOUL, GolemBinding.decide(false, false, false, true));
        assertEquals(Result.RELEASE_SOUL, GolemBinding.decide(false, true, true, true));
        assertEquals(Result.NONE, GolemBinding.decide(false, true, false, true), "someone else's golem");
        assertEquals(Result.NONE, GolemBinding.decide(false, false, false, false), "nothing to drop");
    }

    @Test
    void clickFilterLetsEachClickThroughOnce() {
        ClickFilter filter = new ClickFilter();
        // Paper 26.3: one event per hand; older builds: two events for the same click
        assertTrue(filter.accept(10, "p", 1, false));
        assertFalse(filter.accept(10, "p", 1, false), "same hand twice in a tick");
        // main hand did nothing (no shard there): the off hand gets its chance
        assertTrue(filter.accept(10, "p", 1, true));
        // next tick everything is open again
        assertTrue(filter.accept(11, "p", 1, false));
        // the main hand bound the golem: the client's off-hand click of the same tick must not drop the soul
        filter.handled(11, "p", 1);
        assertFalse(filter.accept(11, "p", 1, true));
        // other players and other mobs are independent
        assertTrue(filter.accept(11, "q", 1, true));
        assertTrue(filter.accept(11, "p", 2, true));
    }
}
