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

    /** The numbers of alpha.42b (round numbers for the fight rules below); the defaults since alpha.42f are tested apart. */
    private static final Settings S = new Settings(600, 0.5, 4, 0.6, 0.25, 12, 6, 0.05);
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void defaultsSinceAlpha42fScaleHarderWithMorePlayers() {
        Settings d = Settings.defaults();
        double[] expected = {700, 1225, 1750, 2275, 2800, 2800, 2800, 2800}; // +75 % each, at most x4 (5 players)
        for (int players = 1; players <= 8; players++) {
            assertEquals(expected[players - 1], d.maxHealth(players), 1e-9, players + " players");
        }
        GolemBoss.Scaling s = GolemBoss.Scaling.defaults();
        double[] damage = {1.0, 1.1, 1.2, 1.3, 1.4, 1.4, 1.4, 1.4}; // +10 % each, at most +40 %
        int[] helpers = {0, 0, 1, 1, 2, 2, 2, 2}; // +1 per two further players, at most +2
        double[] interval = {1.0, 0.95, 0.9, 0.85, 0.8, 0.8, 0.8, 0.8}; // 5 % more often each, at most 20 %
        for (int players = 1; players <= 8; players++) {
            assertEquals(damage[players - 1], s.damageFactor(players), 1e-9, "damage, " + players + " players");
            assertEquals(helpers[players - 1], s.extraHelpers(players), "helpers, " + players + " players");
            assertEquals(interval[players - 1], s.intervalFactor(players), 1e-9, "interval, " + players + " players");
        }
        assertEquals(1.0, s.damageFactor(0), 1e-9, "nobody counted as one");
        assertEquals(1.0, GolemBoss.Scaling.none().damageFactor(8), 1e-9);
        // solo: 10 % more than alpha.42b; three players: 20 % on top and a fifth more often
        var a = GolemBoss.Attacks.defaults();
        assertEquals(15.4, a.meleeDamage(), 1e-9);
        var three = a.scaled(s.damageFactor(3), s.intervalFactor(3));
        assertEquals(15.4 * 1.2, three.meleeDamage(), 1e-9);
        assertEquals(11 * 1.2, three.sonicDamage(), 1e-9);
        assertEquals(216, three.slamInterval(Phase.SOUND));
        assertEquals(27, three.meleeCooldown());
        assertEquals(20, three.slamWindup(), "wind-ups stay");
    }

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
        assertEquals(8.8, a.slamDamageAt(0), 1e-9);
        assertEquals(4.4, a.slamDamageAt(6), 1e-9);
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
