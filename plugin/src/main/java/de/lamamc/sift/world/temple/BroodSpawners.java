package de.lamamc.sift.world.temple;

import de.lamamc.sift.core.temple.BroodSpawner;
import de.lamamc.sift.core.temple.TempleLayout;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Runs the brood spawners of the temples (phase 4, alpha.42a, phase4.temples.spawners): once a second each spawner of a
 * loaded temple of version 3 counts the players around it and, by {@link BroodSpawner}, sends its waves. Brood mobs are
 * ordinary Sift mobs with a marker: no drops, no or little experience, at most max-alive per temple, removed when no
 * player has been near them for despawn-seconds, and removed again when their chunk loads later (they belong to a fight
 * that is over). The block shows the state: trial spawner with ominous (blue) flames while awake, dark when exhausted.
 * After the boss of a temple is defeated all its spawners are off.
 */
public final class BroodSpawners implements Listener {

    public static final NamespacedKey BROOD = new NamespacedKey("the_sift", "brood");

    /** @param zones zone 1..3 -> what it sends */
    public record Settings(double activationRadius, double despawnRadius, long despawnMillis, int maxAlivePerTemple,
                           BroodSpawner.Timing timing, Map<Integer, BroodSpawner.Zone> zones, double experienceFactor,
                           boolean drops) {
    }

    private final Plugin plugin;
    private final TempleRegistry temples;
    private final MobManager mobs;
    private final Settings settings;
    /** "templeId#index" -> living brood */
    private final Map<String, Set<UUID>> alive = new HashMap<>();
    /** brood mob -> last time a player was near it */
    private final Map<UUID, Long> lastSeen = new HashMap<>();
    /** Test aid (/sift temple spawners simulate): temple -> {players, until} counted as present at every spawner. */
    private final Map<String, long[]> simulated = new HashMap<>();

    public void simulate(TempleRegistry.Temple t, int players, long seconds) {
        simulated.put(t.id(), new long[] {players, System.currentTimeMillis() + seconds * 1000L});
    }

    private int simulated(String templeId, long now) {
        long[] s = simulated.get(templeId);
        return s != null && s[1] > now ? (int) s[0] : 0;
    }

    public BroodSpawners(Plugin plugin, TempleRegistry temples, MobManager mobs, Settings settings) {
        this.plugin = plugin;
        this.temples = temples;
        this.mobs = mobs;
        this.settings = settings;
        temples.onDefeat(this::defeated);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private static String key(TempleRegistry.Temple t, int index) {
        return t.id() + "#" + index;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (TempleRegistry.Temple t : temples.all()) {
            if (!temples.hasSpawners(t) || t.state() == TempleRegistry.State.DEFEATED) {
                continue;
            }
            Block centre = temples.altarBlock(t);
            World world = centre.getWorld();
            if (!world.isChunkLoaded(centre.getX() >> 4, centre.getZ() >> 4)) {
                continue;
            }
            int aliveInTemple = 0;
            List<TempleLayout.Spawner> plan = temples.spawners(t);
            int[] living = new int[plan.size()];
            boolean present = simulated(t.id(), now) > 0;
            for (int i = 0; i < plan.size(); i++) {
                living[i] = prune(key(t, i), now, present);
                aliveInTemple += living[i];
            }
            BroodSpawner.State[] states = temples.spawnerStates(t);
            for (int i = 0; i < plan.size(); i++) {
                Block block = temples.spawnerBlock(t, i);
                if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    continue;
                }
                BroodSpawner.Zone zone = settings.zones().get(plan.get(i).zone());
                if (zone == null) {
                    continue;
                }
                int players = playersNear(block.getLocation().add(0.5, 0.5, 0.5), settings.activationRadius()) + simulated(t.id(), now);
                BroodSpawner.Step step = BroodSpawner.tick(states[i], zone, settings.timing(), players, living[i], now);
                if (step.state().phase() == BroodSpawner.Phase.ACTIVE && states[i].phase() == BroodSpawner.Phase.WAITING) {
                    world.playSound(block.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_DETECT_PLAYER, 1f, 0.8f);
                }
                int spawn = Math.min(step.spawn(), Math.max(0, settings.maxAlivePerTemple() - aliveInTemple));
                int spawned = spawn > 0 ? spawn(t, i, block, zone, spawn) : 0;
                aliveInTemple += spawned;
                if (step.spawn() > 0 && spawned == 0) {
                    continue; // temple full (or no free spot): the wave waits, it is not skipped
                }
                if (step.state().phase() == BroodSpawner.Phase.EXHAUSTED && states[i].phase() != BroodSpawner.Phase.EXHAUSTED) {
                    world.playSound(block.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_CLOSE_SHUTTER, 1f, 0.8f);
                    world.spawnParticle(Particle.LARGE_SMOKE, block.getLocation().add(0.5, 1, 0.5), 20, 0.3, 0.3, 0.3, 0.01);
                }
                temples.spawnerState(t, i, step.state());
                show(block, step.state());
            }
        }
    }

    /** Living brood of a spawner after removing the dead and the abandoned ones. */
    private int prune(String key, long now, boolean present) {
        Set<UUID> set = alive.get(key);
        if (set == null) {
            return 0;
        }
        for (var it = set.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid() || e.isDead()) {
                it.remove();
                lastSeen.remove(id);
                continue;
            }
            if (present || playersNear(e.getLocation(), settings.despawnRadius()) > 0) {
                lastSeen.put(id, now);
            } else if (now - lastSeen.getOrDefault(id, now) > settings.despawnMillis()) {
                e.remove();
                it.remove();
                lastSeen.remove(id);
            }
        }
        return set.size();
    }

    private static int playersNear(Location at, double radius) {
        int n = 0;
        double r2 = radius * radius;
        for (Player p : at.getWorld().getPlayers()) {
            if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getLocation().distanceSquared(at) <= r2) {
                n++;
            }
        }
        return n;
    }

    private int spawn(TempleRegistry.Temple t, int index, Block block, BroodSpawner.Zone zone, int count) {
        int done = 0;
        World world = block.getWorld();
        for (int n = 0; n < count; n++) {
            String id = zone.mobs().get(ThreadLocalRandom.current().nextInt(zone.mobs().size()));
            MobKind kind = mobs.kind(id);
            Location at = kind == null ? null : spot(block, kind.spawnRoom());
            if (kind == null || at == null) {
                continue;
            }
            Mob mob = mobs.spawn(kind, at, false, m -> m.getPersistentDataContainer().set(BROOD, PersistentDataType.STRING, key(t, index)));
            if (mob == null) {
                break; // world limit of Sift mobs
            }
            alive.computeIfAbsent(key(t, index), k -> new HashSet<>()).add(mob.getUniqueId());
            lastSeen.put(mob.getUniqueId(), System.currentTimeMillis());
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 0.6, 0), 16, 0.3, 0.5, 0.3, 0.02);
            done++;
        }
        if (done > 0) {
            world.playSound(block.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1f, 0.7f);
        }
        return done;
    }

    /**
     * A free spot next to the spawner: a solid floor and above it {@code room} = {width, height} blocks free (alpha.42e: the
     * Dark Sniffer is 1.9 wide and suffocated when it appeared beside a wall).
     */
    private static Location spot(Block block, int[] room) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int half = room[0] / 2;
        for (int tries = 0; tries < 24; tries++) {
            Block b = block.getRelative(r.nextInt(-3, 4), r.nextInt(-1, 2), r.nextInt(-3, 4));
            if (b.getRelative(0, -1, 0).getType().isSolid() && free(b, half, room[1])) {
                return b.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    private static boolean free(Block feet, int half, int height) {
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    Block b = feet.getRelative(dx, dy, dz);
                    if (!b.isPassable() || b.isLiquid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Blue (ominous) flames while awake, dark when exhausted or off; only on an intact trial spawner. */
    private static void show(Block block, BroodSpawner.State state) {
        if (block.getType() != Material.TRIAL_SPAWNER) {
            return;
        }
        var data = (org.bukkit.block.data.type.TrialSpawner) block.getBlockData();
        boolean awake = !state.done();
        if (data.isOminous() != awake || data.getTrialSpawnerState() != org.bukkit.block.data.type.TrialSpawner.State.INACTIVE) {
            data.setOminous(awake);
            data.setTrialSpawnerState(org.bukkit.block.data.type.TrialSpawner.State.INACTIVE);
            block.setBlockData(data, false);
        }
    }

    /** Admin and defeat: removes the living brood of a temple. */
    public int clearBrood(TempleRegistry.Temple t) {
        int removed = 0;
        for (var e : new ArrayList<>(alive.entrySet())) {
            if (!e.getKey().startsWith(t.id() + "#")) {
                continue;
            }
            for (UUID id : e.getValue()) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                    removed++;
                }
                lastSeen.remove(id);
            }
            alive.remove(e.getKey());
        }
        return removed;
    }

    /** Living brood of a temple, for /sift temple spawners. */
    public int alive(TempleRegistry.Temple t) {
        return alive.entrySet().stream().filter(e -> e.getKey().startsWith(t.id() + "#")).mapToInt(e -> e.getValue().size()).sum();
    }

    private void defeated(TempleRegistry.Temple t) {
        clearBrood(t);
        for (int i = 0; i < temples.spawners().size(); i++) {
            Block block = temples.spawnerBlock(t, i);
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                show(block, new BroodSpawner.State(BroodSpawner.Phase.OFF, 0, 0, 0));
            }
        }
    }

    /** Shows the current state of all spawners of a temple at once (admin commands). */
    public void refresh(TempleRegistry.Temple t) {
        BroodSpawner.State[] states = temples.spawnerStates(t);
        for (int i = 0; i < states.length; i++) {
            Block block = temples.spawnerBlock(t, i);
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                show(block, states[i]);
            }
        }
    }

    /** Brood never drops loot and gives little or no experience, so a temple is no farm. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(BROOD, PersistentDataType.STRING)) {
            return;
        }
        if (!settings.drops()) {
            event.getDrops().clear();
        }
        event.setDroppedExp((int) Math.floor(event.getDroppedExp() * settings.experienceFactor()));
    }

    /** Brood left over from an earlier fight (chunk unloaded, restart) is removed when it loads again. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            String key = e.getPersistentDataContainer().get(BROOD, PersistentDataType.STRING);
            if (key != null && (alive.get(key) == null || !alive.get(key).contains(e.getUniqueId()))) {
                Bukkit.getScheduler().runTask(plugin, e::remove);
            }
        }
    }
}
