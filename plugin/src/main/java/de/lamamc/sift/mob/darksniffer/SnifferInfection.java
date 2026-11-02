package de.lamamc.sift.mob.darksniffer;

import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.SiftWorlds;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * SnifferInfectionMixin of the original, for ordinary sniffers: one that starts digging on sculk is infected
 * and becomes a Dark Sniffer after 600 ticks (health kept); an infected sniffer digs up nothing. Ordinary
 * sniffers flee from Dark Sniffers within 20 blocks. Checked every {@code check-ticks} (config), in the Sift
 * only unless {@code infection.only-in-sift} is off.
 */
public final class SnifferInfection implements Listener {

    static final NamespacedKey INFECTION = new NamespacedKey("the_sift", "sculk_infection_ticks");

    private final MobManager mobs;
    private final DarkSnifferKind kind;
    private final int checkTicks;
    private final int conversionTicks;
    private final boolean onlyInSift;
    private final double fleeRadius;

    public SnifferInfection(MobManager mobs, DarkSnifferKind kind) {
        this.mobs = mobs;
        this.kind = kind;
        var config = DarkSnifferKind.section(kind.config(), "infection");
        this.checkTicks = Math.max(1, config.getInt("check-ticks", 10));
        this.conversionTicks = config.getInt("conversion-ticks", 600);
        this.onlyInSift = config.getBoolean("only-in-sift", true);
        this.fleeRadius = kind.config().getDouble("sniffers-flee-radius", 20);
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L, checkTicks);
    }

    private boolean enabled() {
        return kind.enabled() && kind.config().getBoolean("infection.enabled", true);
    }

    private void check() {
        for (World world : Bukkit.getWorlds()) {
            if (onlyInSift && !SiftWorlds.isSift(world)) {
                continue;
            }
            for (Sniffer sniffer : world.getEntitiesByClass(Sniffer.class)) {
                if (MobManager.kindOf(sniffer) != null) {
                    continue;
                }
                flee(sniffer);
                if (enabled()) {
                    infect(sniffer);
                }
            }
        }
    }

    private void infect(Sniffer sniffer) {
        var pdc = sniffer.getPersistentDataContainer();
        int ticks = pdc.getOrDefault(INFECTION, PersistentDataType.INTEGER, 0);
        if (ticks <= 0) {
            if (sniffer.getState() == Sniffer.State.DIGGING && digsSculk(sniffer)) {
                pdc.set(INFECTION, PersistentDataType.INTEGER, 1);
            }
            return;
        }
        ticks += checkTicks;
        pdc.set(INFECTION, PersistentDataType.INTEGER, ticks);
        Location at = sniffer.getLocation().add(0, 1.2, 0);
        sniffer.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 2, 0.6, 0.5, 0.6, 0.01);
        if (ticks >= conversionTicks) {
            convert(sniffer);
        }
    }

    /**
     * Infects the nearest ordinary sniffer within 16 blocks right away (/sift debug infect), as if it had dug on
     * sculk; returns it or null.
     */
    public Sniffer infectNearest(Location at) {
        Sniffer best = null;
        double bestDistance = 16 * 16;
        for (Sniffer sniffer : at.getWorld().getEntitiesByClass(Sniffer.class)) {
            double d = sniffer.getLocation().distanceSquared(at);
            if (MobManager.kindOf(sniffer) == null && d <= bestDistance) {
                best = sniffer;
                bestDistance = d;
            }
        }
        if (best != null && best.getPersistentDataContainer().getOrDefault(INFECTION, PersistentDataType.INTEGER, 0) <= 0) {
            best.getPersistentDataContainer().set(INFECTION, PersistentDataType.INTEGER, 1);
        }
        return best;
    }

    /** Seconds from infection to conversion (config infection.conversion-ticks). */
    public int conversionSeconds() {
        return Math.round(conversionTicks / 20f);
    }

    /** Only in the Sift (config infection.only-in-sift)? */
    public boolean onlyInSift() {
        return onlyInSift;
    }

    /** The block below the sniffer's nose (2.25 blocks ahead), like the original. */
    private static boolean digsSculk(Sniffer sniffer) {
        Location at = sniffer.getLocation();
        Vector forward = at.getDirection().setY(0);
        if (forward.lengthSquared() > 1.0E-6) {
            forward.normalize();
        }
        Location head = at.clone().add(forward.multiply(2.25)).add(0, 0.2, 0);
        return head.getBlock().getRelative(0, -1, 0).getType() == Material.SCULK;
    }

    private void convert(Sniffer sniffer) {
        Location at = sniffer.getLocation();
        double health = sniffer.getHealth();
        Mob dark = mobs.spawn(kind, at, false, null);
        if (dark == null) {
            return;
        }
        Bukkit.getLogger().info("[TheSift] Sniffer at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + " became a Dark Sniffer (sculk infection)");
        double max = dark.getAttribute(Attribute.MAX_HEALTH).getValue();
        dark.setHealth(Math.min(max, Math.max(1, health)));
        if (sniffer.customName() != null) {
            dark.customName(sniffer.customName());
        }
        at.getWorld().playSound(Sound.sound(Key.key("the_sift", "block.dark_sniffer.bloom"), Sound.Source.HOSTILE, 1.0f, 0.72f),
                at.getX(), at.getY(), at.getZ());
        sniffer.remove();
    }

    private void flee(Sniffer sniffer) {
        if (fleeRadius <= 0) {
            return;
        }
        SiftMob nearest = null;
        double best = fleeRadius * fleeRadius;
        for (Entity e : sniffer.getNearbyEntities(fleeRadius, fleeRadius, fleeRadius)) {
            SiftMob mob = mobs.mob(e);
            if (mob != null && mob.kind() == kind && !mob.dying()) {
                double d = e.getLocation().distanceSquared(sniffer.getLocation());
                if (d < best) {
                    best = d;
                    nearest = mob;
                }
            }
        }
        if (nearest == null) {
            return;
        }
        Vector away = sniffer.getLocation().toVector().subtract(nearest.base().getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(1, 0, 0);
        }
        Location target = sniffer.getLocation().add(away.normalize().multiply(16));
        sniffer.getPathfinder().moveTo(target, 1.25);
    }

    /** An infected sniffer digs up nothing (loot table infected_sniffer_digging of the original is empty). */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Sniffer sniffer && MobManager.kindOf(sniffer) == null
                && sniffer.getPersistentDataContainer().getOrDefault(INFECTION, PersistentDataType.INTEGER, 0) > 0) {
            event.setCancelled(true);
        }
    }
}
