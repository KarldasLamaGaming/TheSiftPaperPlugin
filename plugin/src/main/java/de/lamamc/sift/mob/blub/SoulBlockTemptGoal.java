package de.lamamc.sift.mob.blub;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import de.lamamc.sift.item.SiftItems;
import java.util.EnumSet;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;

/** Vanilla TemptGoal with the Sift soul block: follows a player holding one, pauses after losing them. */
final class SoulBlockTemptGoal implements Goal<Wolf> {

    static final GoalKey<Wolf> KEY = GoalKey.of(Wolf.class, new NamespacedKey("the_sift", "blub_tempt"));
    static final String SOUL_BLOCK = "soul_block";

    private final Wolf wolf;
    private final double speed;
    private final int cooldown;
    private int calmDown;
    private Player player;

    SoulBlockTemptGoal(Wolf wolf, double speed, int cooldown) {
        this.wolf = wolf;
        this.speed = speed;
        this.cooldown = cooldown;
    }

    static boolean isSoulBlock(ItemStack stack) {
        return SOUL_BLOCK.equals(SiftItems.idOf(stack));
    }

    private static boolean tempting(Player player) {
        return isSoulBlock(player.getInventory().getItemInMainHand()) || isSoulBlock(player.getInventory().getItemInOffHand());
    }

    boolean active() {
        return player != null;
    }

    @Override
    public boolean shouldActivate() {
        if (calmDown > 0) {
            calmDown--;
            return false;
        }
        player = nearestTempting();
        return player != null;
    }

    private Player nearestTempting() {
        var range = wolf.getAttribute(Attribute.TEMPT_RANGE);
        double r = range == null ? 10 : range.getValue();
        Player best = null;
        double bestDistance = r * r;
        for (Player candidate : wolf.getWorld().getPlayers()) {
            if (candidate.getGameMode() == GameMode.SPECTATOR || !tempting(candidate)) {
                continue;
            }
            double d = candidate.getLocation().distanceSquared(wolf.getLocation());
            if (d <= bestDistance) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    @Override
    public boolean shouldStayActive() {
        return player != null && player.isValid() && player.getWorld().equals(wolf.getWorld()) && tempting(player)
                && player.getLocation().distanceSquared(wolf.getLocation()) <= 400;
    }

    @Override
    public void tick() {
        wolf.lookAt(player);
        if (wolf.getLocation().distanceSquared(player.getLocation()) < 6.25) {
            wolf.getPathfinder().stopPathfinding();
        } else {
            wolf.getPathfinder().moveTo(player, speed);
        }
    }

    @Override
    public void stop() {
        player = null;
        wolf.getPathfinder().stopPathfinding();
        calmDown = cooldown;
    }

    @Override
    public GoalKey<Wolf> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}
