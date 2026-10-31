package de.lamamc.sift.mob.blub;

import de.lamamc.sift.mob.MobManager;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/** canAttack/wantsToAttack of the original: Blubs never target other Blubs, owned pets, their owner or creative players. */
public final class BlubTargets implements Listener {

    static boolean canAttack(Wolf blub, LivingEntity target) {
        if (BlubKind.ID.equals(MobManager.kindOf(target))) {
            return false;
        }
        if (target instanceof Tameable pet && pet.getOwnerUniqueId() != null) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                return false;
            }
            return !player.getUniqueId().equals(blub.getOwnerUniqueId());
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Wolf wolf && BlubKind.ID.equals(MobManager.kindOf(wolf))
                && event.getTarget() != null && !canAttack(wolf, event.getTarget())) {
            event.setCancelled(true);
        }
    }
}
