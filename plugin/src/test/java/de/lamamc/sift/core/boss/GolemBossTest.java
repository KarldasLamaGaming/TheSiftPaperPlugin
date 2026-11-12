package de.lamamc.sift.core.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.core.boss.GolemBoss.Fight;
import de.lamamc.sift.core.boss.GolemBoss.Hit;
import de.lamamc.sift.core.boss.GolemBoss.Phase;
import de.lamamc.sift.core.boss.GolemBoss.Settings;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GolemBossTest {

    private static final Settings S = Settings.defaults();
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void healthScalesWithPlayersUpToTheCap() {
        assertEquals(600, S.maxHealth(0), 1e-9);
        assertEquals(600, S.maxHealth(1), 1e-9);
        assertEquals(900, S.maxHealth(2), 1e-9);
        assertEquals(1200, S.maxHealth(3), 1e-9);
        assertEquals(1500, S.maxHealth(4), 1e-9);
        assertEquals(1500, S.maxHealth(9), 1e-9, "capped at 4 players (x2.5)");
    }

    @Test
    void armourPhaseEndsOnlyWithTheLastPlateAndProjectilesBounce() {
        Fight f = new Fight(S, 1);
        assertEquals(Phase.ARMOR, f.phase());
        assertEquals(12, f.plates());
        var arrow = f.hit(A, Hit.RANGED, 50);
        assertTrue(arrow.bounced());
        assertEquals(600, f.health(), 1e-9);
        for (int i = 0; i < 11; i++) {
            var r = f.hit(A, Hit.MELEE, 1);
            assertTrue(r.plateBroken());
            assertEquals(20, r.dealt(), 1e-9, "each plate costs 40 % / 12 of the health");
            assertEquals(Phase.ARMOR, f.phase());
        }
        var last = f.hit(A, Hit.MELEE, 1000);
        assertEquals(Phase.SOUND, last.to());
        assertTrue(last.changedPhase());
        assertEquals(360, f.health(), 1e-6, "60 %");
        assertTrue(f.takeSoulCall());
        assertFalse(f.takeSoulCall(), "only once");
    }

    @Test
    void soundPhaseTakesNormalDamageAndHelperPlatesAbsorbHits() {
        Fight f = armourBroken(1);
        f.hit(A, Hit.RANGED, 30);
        assertEquals(330, f.health(), 1e-6);
        assertTrue(f.addPlate());
        var absorbed = f.hit(B, Hit.MELEE, 30);
        assertTrue(absorbed.plateBroken());
        assertEquals(0, absorbed.dealt(), 1e-9);
        assertEquals(330, f.health(), 1e-6);
        assertTrue(f.addPlate());
        assertTrue(f.hit(B, Hit.RANGED, 30).bounced(), "a helper plate also stops arrows");
        for (int i = 0; i < 4; i++) {
            assertTrue(f.addPlate());
        }
        assertFalse(f.addPlate(), "at most 6 plates come back");
    }

    @Test
    void rageBelowAQuarterShattersPlatesAndDeathEndsIt() {
        Fight f = armourBroken(1);
        f.addPlate();
        f.addPlate();
        f.hit(A, Hit.MELEE, 1);
        f.hit(A, Hit.MELEE, 1);
        var r = f.hit(A, Hit.MELEE, 360 - 150 + 0.5);
        assertEquals(Phase.RAGE, r.to());
        assertEquals(0, f.plates());
        assertFalse(f.addPlate(), "no helpers in rage");
        var kill = f.hit(B, Hit.MELEE, 10_000);
        assertEquals(Phase.DEAD, kill.to());
        assertEquals(149.5, kill.dealt(), 1e-6, "only the health left counts");
        assertEquals(0, f.hit(B, Hit.MELEE, 10).dealt(), 1e-9);
    }

    @Test
    void lootGoesToDamageDealersAndThosePresentAtDeath() {
        Fight f = new Fight(S, 3); // 1200 health
        for (int i = 0; i < 12; i++) {
            f.hit(i < 10 ? A : B, Hit.MELEE, 1); // 40 each: A 400, B 80
        }
        f.hit(C, Hit.RANGED, 50); // C 50 of 1200 = 4.2 %, below 5 %
        f.hit(A, Hit.MELEE, 2000);
        assertEquals(Phase.DEAD, f.phase());
        assertTrue(f.share(B) > 0.05);
        assertEquals(Set.of(A, B), f.eligible(List.of()));
        assertEquals(Set.of(A, B, C), f.eligible(List.of(C)));
    }

    @Test
    void attackTimersDependOnThePhase() {
        var a = GolemBoss.Attacks.defaults();
        assertEquals(-1, a.sonicInterval(Phase.ARMOR));
        assertEquals(200, a.sonicInterval(Phase.SOUND));
        assertEquals(140, a.sonicInterval(Phase.RAGE));
        assertEquals(240, a.slamInterval(Phase.SOUND));
        assertEquals(120, a.slamInterval(Phase.RAGE));
        assertEquals(1.3, a.speed(Phase.RAGE), 1e-9);
        assertEquals(8, a.slamDamageAt(0), 1e-9);
        assertEquals(4, a.slamDamageAt(6), 1e-9);
        assertEquals(0, a.slamDamageAt(6.1), 1e-9);
    }

    @Test
    void noPlatesStartsInTheSoundPhase() {
        Fight f = new Fight(new Settings(600, 0, 1, 0.6, 0.25, 0, 0, 0.05), 1);
        assertEquals(Phase.SOUND, f.phase());
        assertEquals(360, f.health(), 1e-9);
        assertFalse(f.takeSoulCall(), "no helpers without plates to bring");
    }

    @Test
    void arenaIsACylinderAboveTheFloor() {
        var arena = new GolemBoss.Arena(0.5, -16, 0.5, 11, 10);
        assertTrue(arena.inside(5, -16, 5));
        assertTrue(arena.inside(0.5, -6, 11.4));
        assertFalse(arena.inside(0.5, -16, 12), "in a gate");
        assertFalse(arena.inside(0, -5, 0), "above the arena (keep, shaft)");
        assertFalse(arena.inside(0, -18, 0));
    }

    private static Fight armourBroken(int players) {
        Fight f = new Fight(S, players);
        for (int i = 0; i < 12; i++) {
            f.hit(A, Hit.MELEE, 1);
        }
        f.takeSoulCall();
        return f;
    }
}
