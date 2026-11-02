package de.lamamc.sift.mob.darksniffer;

import de.lamamc.sift.core.mob.WardenPetting;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.plugin.Plugin;

/**
 * Wardens and Dark Sniffers are friends (WardenDarkSnifferMixin of the original): a warden never targets a
 * Dark Sniffer, never gets angry at one and its attacks (sonic boom included) do not hurt it. A completely
 * calm warden now and then walks to a Dark Sniffer and pets it for 5 seconds (hearts), which keeps the
 * sniffer still. Switches: mobs.dark_sniffer.wardens-friendly, wardens-pet.
 */
public final class WardenFriends implements Listener {

    private final MobManager mobs;
    private final DarkSnifferKind kind;
    private final WardenPetting.Rules rules;
    private final Map<Warden, WardenPetting> petting = new WeakHashMap<>();
    private final Map<UUID, DarkSniffer> partner = new java.util.HashMap<>();

    public WardenFriends(MobManager mobs, DarkSnifferKind kind) {
        this.mobs = mobs;
        this.kind = kind;
        this.rules = rules(DarkSnifferKind.section(kind.config(), "warden-petting"));
    }

    /** Petting values from mobs.dark_sniffer.warden-petting; missing ones keep the original's. */
    static WardenPetting.Rules rules(org.bukkit.configuration.ConfigurationSection config) {
        WardenPetting.Rules d = WardenPetting.Rules.defaults();
        return new WardenPetting.Rules(config.getDouble("search-range", d.searchRange()), config.getDouble("reach", d.reach()),
                config.getInt("duration", d.duration()), config.getInt("approach-timeout", d.approachTimeout()),
                range(config, "search-delay", d.searchDelay()), range(config, "cooldown", d.cooldown()),
                range(config, "failed-cooldown", d.failedCooldown()), range(config, "no-candidate-cooldown", d.emptyCooldown()));
    }

    private static int[] range(org.bukkit.configuration.ConfigurationSection config, String key, int[] fallback) {
        java.util.List<Integer> values = config.getIntegerList(key);
        if (values.size() != 2) {
            return fallback;
        }
        int min = Math.max(0, Math.min(values.get(0), values.get(1)));
        return new int[] {min, Math.max(min, Math.max(values.get(0), values.get(1)))};
    }

    private boolean friendly() {
        return kind.enabled() && kind.config().getBoolean("wardens-friendly", true);
    }

    private boolean isDarkSniffer(Entity entity) {
        return entity != null && DarkSnifferKind.ID.equals(MobManager.kindOf(entity));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (friendly() && event.getEntity() instanceof Warden && isDarkSniffer(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnger(WardenAngerChangeEvent event) {
        if (friendly() && isDarkSniffer(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity source = event.getDamageSource().getCausingEntity();
        if (friendly() && isDarkSniffer(event.getEntity()) && (event.getDamager() instanceof Warden || source instanceof Warden)) {
            event.setCancelled(true);
        }
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    private static boolean calm(Warden warden) {
        return warden.isValid() && !warden.isDead() && warden.getPose() == Pose.STANDING
                && warden.getAngerLevel() == Warden.AngerLevel.CALM && warden.getTarget() == null && warden.getHighestAnger() == 0;
    }

    private void tick() {
        if (!friendly() || !kind.config().getBoolean("wardens-pet", true)) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (World world : Bukkit.getWorlds()) {
            for (Warden warden : world.getEntitiesByClass(Warden.class)) {
                WardenPetting state = petting.computeIfAbsent(warden, w -> new WardenPetting(rules));
                if (!state.active()) {
                    if (state.idle(calm(warden), random) == WardenPetting.Action.SEARCH) {
                        DarkSniffer candidate = nearestAvailable(warden);
                        state.searched(candidate != null, random);
                        if (candidate != null) {
                            partner.put(warden.getUniqueId(), candidate);
                        }
                    }
                    continue;
                }
                DarkSniffer sniffer = partner.get(warden.getUniqueId());
                boolean valid = sniffer != null && !sniffer.dying() && sniffer.base().isValid()
                        && sniffer.base().getWorld().equals(warden.getWorld()) && calm(warden);
                double d2 = valid ? sniffer.base().getLocation().distanceSquared(warden.getLocation()) : Double.MAX_VALUE;
                WardenPetting.Action action = state.tick(valid, d2, random);
                switch (action) {
                    case APPROACH -> {
                        sniffer.petBy(warden, 12);
                        warden.getPathfinder().moveTo(sniffer.base(), 0.55);
                    }
                    case PET, HEART -> {
                        sniffer.petBy(warden, 12);
                        warden.getPathfinder().stopPathfinding();
                        warden.lookAt(sniffer.base(), 22f, 22f);
                        if (action == WardenPetting.Action.HEART) {
                            var at = sniffer.base().getLocation();
                            world.spawnParticle(Particle.HEART, at.getX(), at.getY() + sniffer.base().getHeight() + 0.35, at.getZ(),
                                    1, 0.18, 0.08, 0.18, 0);
                        }
                    }
                    case DONE, FAILED -> {
                        if (sniffer != null) {
                            sniffer.petBy(null, 0);
                        }
                        partner.remove(warden.getUniqueId());
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private DarkSniffer nearestAvailable(Warden warden) {
        DarkSniffer best = null;
        double bestDistance = rules.searchRange() * rules.searchRange();
        double r = rules.searchRange();
        for (Entity entity : warden.getNearbyEntities(r, r, r)) {
            SiftMob mob = mobs.mob(entity);
            if (mob instanceof DarkSniffer sniffer && sniffer.availableForPetting()) {
                double d = entity.getLocation().distanceSquared(warden.getLocation());
                if (d < bestDistance) {
                    bestDistance = d;
                    best = sniffer;
                }
            }
        }
        return best;
    }
}
