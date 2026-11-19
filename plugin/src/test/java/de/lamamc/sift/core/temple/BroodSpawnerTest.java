package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.temple.BroodSpawner.Phase;
import de.lamamc.sift.core.temple.BroodSpawner.State;
import java.util.List;
import org.junit.jupiter.api.Test;

class BroodSpawnerTest {

    private static final BroodSpawner.Zone ZONE = new BroodSpawner.Zone(3, 2, 1, 4, List.of("sifter"));
    private static final BroodSpawner.Timing TIMING = new BroodSpawner.Timing(5_000, 60_000);

    @Test
    void wakesWhenAPlayerComesAndScalesWithPlayers() {
        assertEquals(0, BroodSpawner.tick(State.FRESH, ZONE, TIMING, 0, 0, 0).spawn());
        var one = BroodSpawner.tick(State.FRESH, ZONE, TIMING, 1, 0, 0);
        assertEquals(Phase.ACTIVE, one.state().phase());
        assertEquals(1, one.state().wave());
        assertEquals(2, one.spawn());
        assertEquals(4, BroodSpawner.tick(State.FRESH, ZONE, TIMING, 3, 0, 0).spawn());
        assertEquals(5, BroodSpawner.tick(State.FRESH, ZONE, TIMING, 9, 0, 0).spawn(), "scaled up to 4 players");
    }

    @Test
    void wavesFollowAfterTheDelayAndThenItIsExhausted() {
        State s = BroodSpawner.tick(State.FRESH, ZONE, TIMING, 1, 0, 0).state();
        assertEquals(0, BroodSpawner.tick(s, ZONE, TIMING, 1, 2, 1000).spawn(), "wave still alive");
        var cleared = BroodSpawner.tick(s, ZONE, TIMING, 1, 0, 2000);
        assertEquals(0, cleared.spawn());
        assertEquals(7000, cleared.state().readyAt());
        assertEquals(0, BroodSpawner.tick(cleared.state(), ZONE, TIMING, 1, 0, 6000).spawn(), "delay not over");
        var wave2 = BroodSpawner.tick(cleared.state(), ZONE, TIMING, 1, 0, 7000);
        assertEquals(2, wave2.state().wave());
        assertEquals(2, wave2.spawn());
        State w3 = BroodSpawner.tick(BroodSpawner.tick(wave2.state(), ZONE, TIMING, 1, 0, 8000).state(), ZONE, TIMING, 1, 0, 13_000).state();
        assertEquals(3, w3.wave());
        var done = BroodSpawner.tick(w3, ZONE, TIMING, 1, 0, 20_000);
        assertEquals(Phase.EXHAUSTED, done.state().phase());
        assertEquals(80_000, done.state().until());
        assertTrue(done.state().done());
        assertEquals(Phase.EXHAUSTED, BroodSpawner.tick(done.state(), ZONE, TIMING, 1, 0, 79_999).state().phase());
        var again = BroodSpawner.tick(done.state(), ZONE, TIMING, 1, 0, 80_000);
        assertEquals(Phase.ACTIVE, again.state().phase(), "cooldown over, a player is near: wakes again");
        assertEquals(2, again.spawn());
    }

    @Test
    void cooldownZeroMeansForGoodAndOffStaysOff() {
        var forGood = new BroodSpawner.Timing(0, 0);
        BroodSpawner.Zone oneWave = new BroodSpawner.Zone(1, 1, 0, 1, List.of("sifter"));
        State s = BroodSpawner.tick(State.FRESH, oneWave, forGood, 1, 0, 0).state();
        State done = BroodSpawner.tick(s, oneWave, forGood, 1, 0, 10).state();
        assertEquals(Long.MAX_VALUE, done.until());
        assertEquals(Phase.EXHAUSTED, BroodSpawner.tick(done, oneWave, forGood, 1, 0, Long.MAX_VALUE - 1).state().phase());
        State off = new State(Phase.OFF, 0, 0, 0);
        assertEquals(off, BroodSpawner.tick(off, oneWave, forGood, 5, 0, 0).state());
        assertTrue(off.done());
    }

    @Test
    void leavingResetsTheWavesAndStateSurvivesSerialization() {
        State s = BroodSpawner.tick(State.FRESH, ZONE, TIMING, 1, 0, 0).state();
        assertEquals(Phase.ACTIVE, BroodSpawner.tick(s, ZONE, TIMING, 0, 2, 100).state().phase(), "mobs still there");
        assertEquals(State.FRESH, BroodSpawner.tick(s, ZONE, TIMING, 0, 0, 100).state(), "all gone and nobody near");
        State exhausted = new State(Phase.EXHAUSTED, 3, 0, Long.MAX_VALUE);
        assertEquals(exhausted, State.parse(exhausted.serialize()));
        assertEquals(State.FRESH, State.parse("nonsense"));
    }

    @Test
    void broodExperienceIsFixedPerKindAndOnlyForPlayerKills() {
        java.util.Map<String, Integer> perKind = java.util.Map.of("sifter", 5, "dark_sniffer", 10, "charoite_wisp", 8);
        org.junit.jupiter.api.Assertions.assertEquals(5, BroodSpawner.experience("sifter", 7, true, perKind, 0));
        org.junit.jupiter.api.Assertions.assertEquals(10, BroodSpawner.experience("dark_sniffer", 2, true, perKind, 0));
        org.junit.jupiter.api.Assertions.assertEquals(8, BroodSpawner.experience("charoite_wisp", 0, true, perKind, 0));
        org.junit.jupiter.api.Assertions.assertEquals(0, BroodSpawner.experience("sifter", 7, false, perKind, 1), "no player: none");
        org.junit.jupiter.api.Assertions.assertEquals(0, BroodSpawner.experience("blub", 3, true, perKind, 0), "not listed, factor 0");
        org.junit.jupiter.api.Assertions.assertEquals(1, BroodSpawner.experience("blub", 3, true, perKind, 0.5), "factor for the rest");
    }
}
