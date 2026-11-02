package de.lamamc.sift.mob.darksniffer;

import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.gen.DarkSnifferSites;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

/**
 * Spawns the Dark Sniffers that new surface sculk regions planned (SiftDeferredSnifferSpawner of the original):
 * at most 4 per tick, once all candidate chunks are loaded, on a sculk floor with two free blocks above;
 * plans that cannot be served yet are retried up to 80 times.
 */
public final class DarkSnifferSpawns {

    private static final int PER_TICK = 4;
    private static final int MAX_RETRIES = 80;

    private final MobManager mobs;
    private final DarkSnifferKind kind;
    private long spawned;

    public DarkSnifferSpawns(MobManager mobs, DarkSnifferKind kind) {
        this.mobs = mobs;
        this.kind = kind;
    }

    public long spawned() {
        return spawned;
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
    }

    private void tick() {
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (DarkSnifferSites.QUEUE.isEmpty()) {
            return;
        }
        if (sift == null || !kind.enabled() || !kind.config().getBoolean("spawn.enabled", true)) {
            DarkSnifferSites.QUEUE.clear();
            return;
        }
        int available = DarkSnifferSites.QUEUE.size();
        int done = 0;
        List<DarkSnifferSites.Plan> retry = new ArrayList<>();
        for (int i = 0; i < available && done < PER_TICK; i++) {
            DarkSnifferSites.Plan plan = DarkSnifferSites.QUEUE.poll();
            if (plan == null) {
                break;
            }
            if (!loaded(sift, plan) || !spawn(sift, plan)) {
                if (plan.attempts() + 1 <= MAX_RETRIES) {
                    retry.add(new DarkSnifferSites.Plan(plan.floors(), plan.seed(), plan.attempts() + 1));
                }
            } else {
                done++;
            }
        }
        DarkSnifferSites.QUEUE.addAll(retry);
    }

    private static boolean loaded(World world, DarkSnifferSites.Plan plan) {
        for (int[] f : plan.floors()) {
            if (!world.isChunkLoaded(f[0] >> 4, f[2] >> 4)) {
                return false;
            }
        }
        return true;
    }

    private boolean spawn(World world, DarkSnifferSites.Plan plan) {
        Random random = new Random(plan.seed());
        List<int[]> floors = plan.floors();
        int start = random.nextInt(floors.size());
        for (int offset = 0; offset < floors.size(); offset++) {
            int[] f = floors.get((start + offset) % floors.size());
            Block floor = world.getBlockAt(f[0], f[1], f[2]);
            Block feet = floor.getRelative(0, 1, 0);
            if (floor.getType() == Material.SCULK && feet.getType().isAir() && feet.getRelative(0, 1, 0).getType().isAir()) {
                Location at = feet.getLocation().add(0.5, 0, 0.5);
                at.setYaw(random.nextFloat() * 360f);
                if (mobs.spawn(kind, at, false, null) != null) {
                    spawned++;
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
