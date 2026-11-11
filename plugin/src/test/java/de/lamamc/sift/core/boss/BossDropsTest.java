package de.lamamc.sift.core.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BossDropsTest {

    private static final long HOUR = 3_600_000L;

    @Test
    void onlyPlayerKillsDrop() {
        BossDrops.Drop heart = new BossDrops.Drop(true, "warden_heart", 1.0, 1, 0);
        assertEquals(1, BossDrops.amount(heart, true, 0, null, 0.99));
        assertEquals(0, BossDrops.amount(heart, false, 0, null, 0.0), "killed by a mob, lava, a command");
        assertEquals(0, BossDrops.amount(new BossDrops.Drop(false, "warden_heart", 1.0, 1, 0), true, 0, null, 0.0), "off");
    }

    @Test
    void chanceAndCooldownAgainstFarms() {
        BossDrops.Drop core = new BossDrops.Drop(true, "wither_core", 0.5, 1, 24 * HOUR);
        assertEquals(1, BossDrops.amount(core, true, 0, null, 0.49));
        assertEquals(0, BossDrops.amount(core, true, 0, null, 0.5));
        assertEquals(0, BossDrops.amount(core, true, 10 * HOUR, 0L, 0.0), "within the cooldown");
        assertEquals(1, BossDrops.amount(core, true, 24 * HOUR, 0L, 0.0), "cooldown over");
    }

    @Test
    void everyDamagingPlayerGetsADragonScale() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        Map<UUID, Double> damage = Map.of(a, 180.0, b, 19.0, c, 1.0);
        assertEquals(3, BossDrops.dragonRecipients(new BossDrops.DragonDrop(true, "dragon_scale", 1, 0.0), damage).size());
        var share = BossDrops.dragonRecipients(new BossDrops.DragonDrop(true, "dragon_scale", 1, 0.05), damage);
        assertTrue(share.contains(a) && share.contains(b) && !share.contains(c), "1 of 200 is below 5 %");
        assertTrue(BossDrops.dragonRecipients(new BossDrops.DragonDrop(true, "dragon_scale", 1, 0.0), Map.of()).isEmpty(),
                "killed without player damage (command)");
    }
}
