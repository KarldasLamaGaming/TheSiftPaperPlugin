package de.lamamc.sift.mob.singer;

import de.lamamc.sift.core.mob.SingerRules;
import de.lamamc.sift.core.portal.PortalState;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.portal.SiftPortal;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * SingerSummoner of the original: when a goat horn has been blown, three seconds later the Singer rises in
 * the nearest sleeping portal within 40 blocks (only one per portal), facing its nearest console.
 */
public final class SingerSummoner implements Listener {

    private final Plugin plugin;
    private final MobManager mobs;
    private final SingerKind kind;

    public SingerSummoner(Plugin plugin, MobManager mobs, SingerKind kind) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.kind = kind;
    }

    /** The horn's cooldown starts when it is blown. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHorn(PlayerItemCooldownEvent event) {
        if (event.getType() != Material.GOAT_HORN || !kind.enabled()) {
            return;
        }
        Location origin = event.getPlayer().getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> summon(origin), kind.config().getInt("summon.delay-ticks", 60));
    }

    /** Calls the Singer to the nearest sleeping portal; returns it or null. */
    public SiftMob summon(Location origin) {
        World world = origin.getWorld();
        double radius = kind.config().getDouble("summon.radius", 40);
        SiftPortal best = null;
        double bestDistance = radius * radius;
        for (SiftPortal portal : kind.portals().registry().all(world)) {
            if (portal.state() != PortalState.LOCKED || portal.kind() == SiftPortal.Kind.SIFT_EXIT) {
                continue;
            }
            double[] c = portal.frame().bottomCenter();
            double d = origin.distanceSquared(new Location(world, c[0], c[1], c[2]));
            if (d <= bestDistance) {
                best = portal;
                bestDistance = d;
            }
        }
        if (best == null || hasSinger(world, best)) {
            return null;
        }
        double[] c = best.frame().bottomCenter();
        double[] nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int[] console : best.consoles()) {
            double dx = console[0] + 0.5 - c[0];
            double dy = console[1] + 0.5 - c[1];
            double dz = console[2] + 0.5 - c[2];
            double d = dx * dx + dy * dy + dz * dz;
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = new double[] {console[0] + 0.5, console[1] + 0.5, console[2] + 0.5};
            }
        }
        Location at = new Location(world, c[0], c[1], c[2], SingerRules.cardinalYaw(c, nearest), 0);
        String portalId = best.id();
        var base = mobs.spawn(kind, at, false, mob -> {
            mob.getPersistentDataContainer().set(Singer.MODE, PersistentDataType.STRING, "summoned");
            mob.getPersistentDataContainer().set(Singer.PORTAL, PersistentDataType.STRING, portalId);
        });
        return base == null ? null : mobs.mob(base);
    }

    private boolean hasSinger(World world, SiftPortal portal) {
        for (SiftMob mob : mobs.mobs()) {
            if (mob.kind() == kind && mob.base().getWorld().equals(world)
                    && portal.id().equals(mob.base().getPersistentDataContainer().get(Singer.PORTAL, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    /** Zombies and illagers go for wandering traders; never for the Singer on one. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && SingerKind.ID.equals(MobManager.kindOf(event.getTarget()))) {
            event.setCancelled(true);
        }
    }
}
