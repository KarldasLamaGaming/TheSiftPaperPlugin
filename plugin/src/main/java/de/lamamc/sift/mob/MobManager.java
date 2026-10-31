package de.lamamc.sift.mob;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Registry and driver of all Sift mobs. A base mob is a Sift mob through its PDC entry {@code the_sift:mob};
 * whenever such an entity enters a world (spawn, chunk load, portal trip) it gets its behaviour and display
 * rig, and loses them when it leaves. The displays are not saved, so nothing is left behind after a crash.
 */
public final class MobManager implements Listener {

    public static final NamespacedKey MOB_KEY = new NamespacedKey("the_sift", "mob");
    public static final NamespacedKey RIG_KEY = new NamespacedKey("the_sift", "rig");

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, MobKind> kinds = new LinkedHashMap<>();
    private final Map<UUID, SiftMob> mobs = new HashMap<>();
    private final RigDisplays.RenderSettings render;
    private final int perWorld;
    private final int perPlayer;
    private final int playerRadius;
    private final Stats stats = new Stats();
    private long tick;

    /** Counters for /sift debug mobs. */
    public static final class Stats {
        public long spawnedDisplays;
        public long updates;
        public long transformations;
        long windowStart;
        long windowTransformations;
        public double transformationsPerSecond;
        public long errors;
    }

    public MobManager(Plugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        ConfigurationSection renderConfig = section(config, "render");
        this.render = new RigDisplays.RenderSettings(
                Math.max(1, renderConfig.getInt("update-ticks", 2)),
                Math.max(0, renderConfig.getInt("interpolation-ticks", 3)),
                (float) renderConfig.getDouble("view-range", 48),
                color(renderConfig.getString("hurt-color", "#FF8080")),
                Math.max(0, renderConfig.getInt("hurt-ticks", 10)),
                renderConfig.getBoolean("only-when-watched", true));
        ConfigurationSection limits = section(config, "limits");
        this.perWorld = limits.getInt("per-world", 150);
        this.perPlayer = limits.getInt("per-player", 8);
        this.playerRadius = limits.getInt("player-radius", 64);
    }

    private static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config == null ? null : config.getConfigurationSection(path);
        return section != null ? section : new org.bukkit.configuration.MemoryConfiguration();
    }

    private static Color color(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (RuntimeException e) {
            return Color.fromRGB(0xFF8080);
        }
    }

    public void register(MobKind kind) {
        kinds.put(kind.id(), kind);
    }

    public Map<String, MobKind> kinds() {
        return kinds;
    }

    public MobKind kind(String id) {
        return kinds.get(id);
    }

    public Collection<SiftMob> mobs() {
        return mobs.values();
    }

    public SiftMob mob(Entity entity) {
        return entity == null ? null : mobs.get(entity.getUniqueId());
    }

    /** Kind id of a Sift base mob (also before it is attached), or null. */
    public static String kindOf(Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(MOB_KEY, PersistentDataType.STRING);
    }

    public Stats stats() {
        return stats;
    }

    RigDisplays.RenderSettings renderSettings() {
        return render;
    }

    public int perPlayer() {
        return perPlayer;
    }

    public int playerRadius() {
        return playerRadius;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        // after a reload the worlds are already full of entities
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (RigDisplays.isPart(entity)) {
                    entity.remove();
                } else if (kindOf(entity) != null && entity instanceof Mob base) {
                    attach(base);
                }
            }
        }
    }

    public void stop() {
        mobs.values().forEach(mob -> {
            mob.detached();
            mob.rig.remove();
        });
        mobs.clear();
    }

    /**
     * Spawns a Sift mob. {@code natural} = by the spawner (may despawn when far away, counts towards the
     * limits checked by the caller). Returns null when the world limit is reached.
     */
    public Mob spawn(MobKind kind, Location location, boolean natural, Consumer<Mob> setup) {
        if (count(location.getWorld()) >= perWorld) {
            return null;
        }
        return location.getWorld().spawn(location, kind.baseClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, base -> {
            base.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.STRING, kind.id());
            base.setRemoveWhenFarAway(natural);
            hide(base);
            kind.prepare(base, true);
            if (setup != null) {
                setup.accept(base);
            }
        });
    }

    /** What every base mob needs, whatever its kind. */
    private static void hide(Mob base) {
        base.setInvisible(true);
        base.setSilent(true);
        base.setCanPickupItems(false);
    }

    public int count(World world) {
        int count = 0;
        for (SiftMob mob : mobs.values()) {
            if (mob.base.getWorld().equals(world)) {
                count++;
            }
        }
        return count;
    }

    /** Sift mobs of a kind (null = all) within {@code radius} blocks of a location. */
    public int countNear(Location location, double radius, String kind) {
        double r2 = radius * radius;
        int count = 0;
        for (SiftMob mob : mobs.values()) {
            if ((kind == null || mob.kind.id().equals(kind)) && mob.base.getWorld().equals(location.getWorld())
                    && mob.base.getLocation().distanceSquared(location) <= r2) {
                count++;
            }
        }
        return count;
    }

    public int worldLimit() {
        return perWorld;
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (RigDisplays.isPart(entity) && !mobs.containsKey(rigOwner(entity))) {
            // a display without its mob (should not happen, they are not saved): remove it next tick
            Bukkit.getScheduler().runTask(plugin, entity::remove);
            return;
        }
        if (kindOf(entity) != null && entity instanceof Mob base) {
            // no entities may be added while the world adds this one
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (base.isValid() && !mobs.containsKey(base.getUniqueId())) {
                    attach(base);
                }
            });
        }
    }

    private static UUID rigOwner(Entity display) {
        try {
            return UUID.fromString(display.getPersistentDataContainer().get(RIG_KEY, PersistentDataType.STRING));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        SiftMob mob = mobs.remove(event.getEntity().getUniqueId());
        if (mob != null) {
            mob.detached();
            mob.rig.remove();
        }
    }

    private void attach(Mob base) {
        MobKind kind = kinds.get(kindOf(base));
        if (kind == null || !kind.enabled()) {
            return; // unknown or disabled kind: leave the entity alone (an update may bring the kind back)
        }
        hide(base);
        kind.prepare(base, false);
        SiftMob mob = kind.create(base, this);
        mobs.put(base.getUniqueId(), mob);
        mob.rig.spawn(render);
    }

    private void tick() {
        tick++;
        boolean render = tick % this.render.updateTicks() == 0;
        for (SiftMob mob : new ArrayList<>(mobs.values())) {
            try {
                if (!mob.base.isValid() && !mob.base.isDead()) {
                    mobs.remove(mob.base.getUniqueId());
                    mob.rig.remove();
                    continue;
                }
                mob.serverTick();
                if (mob.deathTicks < 0 && !mob.rig.intact()) {
                    mob.rig.spawn(this.render);
                } else if (render) {
                    stats.updates++;
                    int changed = mob.rig.update(this.render);
                    stats.transformations += changed;
                    stats.windowTransformations += changed;
                }
            } catch (RuntimeException e) {
                if (++stats.errors <= 5) {
                    logger.log(Level.WARNING, "Sift mob " + mob.kind.id() + " failed", e);
                }
            }
        }
        if (tick - stats.windowStart >= 100) {
            stats.transformationsPerSecond = stats.windowTransformations * 20.0 / (tick - stats.windowStart);
            stats.windowStart = tick;
            stats.windowTransformations = 0;
        }
    }

    long currentTick() {
        return tick;
    }

    void hurtFlash(SiftMob mob) {
        mob.hurtTicks = render.hurtTicks();
    }

    /** Nearest player within {@code radius} blocks, or null. */
    public static Player nearestPlayer(Location location, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(location);
            if (d <= bestDistance) {
                best = player;
                bestDistance = d;
            }
        }
        return best;
    }
}
