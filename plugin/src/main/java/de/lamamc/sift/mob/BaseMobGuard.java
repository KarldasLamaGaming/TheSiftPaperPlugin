package de.lamamc.sift.mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

/**
 * Keeps the vanilla nature of the invisible base mobs from showing through: no transformation, no effects
 * from their attacks, no burning in daylight, no teleporting or block taking of their own, no vanilla
 * interactions (taming, feeding, breeding, leash, saddle, shears), no riding, no item pickup, and the
 * original's drops instead of the base mob's. Sounds are off on the base mob ({@code setSilent}); the
 * Sift mob plays its own.
 */
public final class BaseMobGuard implements Listener {

    private final MobManager mobs;
    /** Entities hit by a Sift mob in this tick (their attack effects are not the Sift mob's). */
    private final Map<UUID, Long> hitBySiftMob = new HashMap<>();

    public BaseMobGuard(MobManager mobs) {
        this.mobs = mobs;
    }

    private static boolean sift(Entity entity) {
        return MobManager.kindOf(entity) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        // daylight burning only; fire, lava and burning attacks still set them alight
        if (sift(event.getEntity()) && !(event instanceof EntityCombustByBlockEvent) && !(event instanceof EntityCombustByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        if (sift(event.getEntity())) {
            SiftMob mob = mobs.mob(event.getEntity());
            if (mob == null || !mob.kind().allowsTeleport()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        // the Sift mobs change blocks themselves (griefing settings); the base mob never does
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (sift(event.getMother()) || sift(event.getFather())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLove(EntityEnterLoveModeEvent event) {
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        // vanilla taming (bones); Sift mobs are tamed by their own code through the API
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        // no boats, minecarts or riding other mobs; their own displays are mounted through the API
        if (sift(event.getEntity()) || sift(event.getMount()) && !RigDisplays.isPart(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (sift(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!sift(clicked)) {
            return;
        }
        event.setCancelled(true);
        SiftMob mob = mobs.mob(clicked);
        if (mob != null && !mob.dying() && !(event instanceof PlayerInteractAtEntityEvent)) {
            mob.interact(event.getPlayer(), event.getHand());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        SiftMob mob = mobs.mob(event.getEntity());
        if (mob != null && !mob.dying() && event.getFinalDamage() > 0) {
            mobs.hurtFlash(mob);
            mob.hurt(event);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getEntity() instanceof LivingEntity victim) {
            SiftMob attacker = mobs.mob(byEntity.getDamager());
            if (attacker != null) {
                hitBySiftMob.put(victim.getUniqueId(), mobs.currentTick());
                attacker.attacked(victim);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.ATTACK) {
            return;
        }
        Long tick = hitBySiftMob.get(event.getEntity().getUniqueId());
        if (tick != null && tick >= mobs.currentTick() - 1) {
            event.setCancelled(true); // e.g. hunger from a husk's bite
        }
        if (hitBySiftMob.size() > 256) {
            hitBySiftMob.values().removeIf(t -> t < mobs.currentTick() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!sift(event.getEntity())) {
            return;
        }
        SiftMob mob = mobs.mob(event.getEntity());
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (mob != null) {
            boolean byPlayer = event.getEntity().getKiller() instanceof Player;
            if (byPlayer || !mob.kind().config().getBoolean("drops-need-player", false)) {
                event.getDrops().addAll(mob.kind().drops(mob));
            }
            event.setDroppedExp(mob.kind().experience());
            mob.deathTicks = 0;
            mob.died();
        }
    }
}
