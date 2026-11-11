package de.lamamc.sift.core.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who gets a boss item (phase 4, alpha.39, config phase4.boss-items): the warden drops the Herz des Wächters and the
 * wither the Kern des Withers when a player kills them; the ender dragon gives every player who damaged it a
 * Drachenschuppe (also after a respawn, the crystals are the price). Against farms: player kills only, a chance and
 * a cooldown per player.
 */
public final class BossDrops {

    private BossDrops() {
    }

    /** One boss item (warden or wither). */
    public record Drop(boolean enabled, String item, double chance, int amount, long cooldownMillis) {
    }

    /** One dragon item for each damaging player. */
    public record DragonDrop(boolean enabled, String item, int amount, double minDamageShare) {
    }

    /**
     * How many items a warden or wither leaves.
     *
     * @param killedByPlayer   the killer (or the shooter of the killing projectile) is a player
     * @param lastDropMillis   when this player last got this item (null: never)
     * @param roll             random number in [0, 1)
     */
    public static int amount(Drop drop, boolean killedByPlayer, long nowMillis, Long lastDropMillis, double roll) {
        if (!drop.enabled() || !killedByPlayer || drop.amount() <= 0) {
            return 0;
        }
        if (drop.cooldownMillis() > 0 && lastDropMillis != null && nowMillis - lastDropMillis < drop.cooldownMillis()) {
            return 0;
        }
        return roll < drop.chance() ? drop.amount() : 0;
    }

    /** The players who get a dragon item: everyone with at least the minimum share of the players' damage. */
    public static List<UUID> dragonRecipients(DragonDrop drop, Map<UUID, Double> damageByPlayer) {
        List<UUID> recipients = new ArrayList<>();
        if (!drop.enabled() || drop.amount() <= 0) {
            return recipients;
        }
        double total = damageByPlayer.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return recipients;
        }
        damageByPlayer.forEach((player, damage) -> {
            if (damage > 0 && damage / total >= drop.minDamageShare()) {
                recipients.add(player);
            }
        });
        return recipients;
    }
}
