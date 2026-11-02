package de.lamamc.sift.mob.singer;

import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.mob.echogolem.EchoGolem;
import de.lamamc.sift.mob.echogolem.EchoGolemKind;
import de.lamamc.sift.world.gen.SoulCanyonLandmarks;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/**
 * SoulExchangeManager of the original: a carrying, unbonded golem asks for a Singer. A soul-taking Singer within
 * 52 / 32 blocks is used; otherwise a soul Singer rises 7–12 blocks away (16 tries around the golem), on solid
 * ground with 5 free blocks, not within 24 blocks of a canyon with soul blocks.
 */
public final class SoulSingers implements EchoGolemKind.SingerLink {

    private final MobManager mobs;
    private final SingerKind kind;
    private final Supplier<SoulCanyonLandmarks> landmarks;

    public SoulSingers(MobManager mobs, SingerKind kind, Supplier<SoulCanyonLandmarks> landmarks) {
        this.mobs = mobs;
        this.kind = kind;
        this.landmarks = landmarks;
    }

    @Override
    public SiftMob request(EchoGolem golem) {
        if (!kind.enabled() || !kind.config().getBoolean("soul-event.enabled", true)) {
            return null;
        }
        Location at = golem.entity().getLocation();
        double search = kind.config().getDouble("soul-event.search", 52);
        Singer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SiftMob mob : mobs.mobs()) {
            if (mob instanceof Singer singer && singer.canTakeSoul() && mob.base().getWorld().equals(at.getWorld())) {
                Location s = mob.base().getLocation();
                if (Math.abs(s.getX() - at.getX()) <= search && Math.abs(s.getZ() - at.getZ()) <= search
                        && Math.abs(s.getY() - at.getY()) <= 32) {
                    double d = s.distanceSquared(at);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = singer;
                    }
                }
            }
        }
        return best != null ? best : spawnNear(golem);
    }

    @Override
    public void offer(SiftMob singer, EchoGolem golem) {
        if (singer instanceof Singer s) {
            s.offerSoulGolem(golem);
        }
    }

    private SiftMob spawnNear(EchoGolem golem) {
        World world = golem.entity().getWorld();
        Location at = golem.entity().getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 16; i++) {
            double angle = phase + i * Math.PI / 8;
            int radius = 7 + random.nextInt(6);
            int x = (int) Math.floor(at.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(at.getZ() + Math.sin(angle) * radius);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block floor = world.getBlockAt(x, y, z);
            if (!floor.getType().isOccluding() || nearCanyon(x + 0.5, y + 1, z + 0.5) || !free(floor, 5)) {
                continue;
            }
            Location spawn = new Location(world, x + 0.5, y + 1, z + 0.5,
                    (float) Math.toDegrees(Math.atan2(-(at.getX() - x - 0.5), at.getZ() - z - 0.5)), 0);
            String golemId = golem.entity().getUniqueId().toString();
            var base = mobs.spawn(kind, spawn, false, mob -> {
                mob.getPersistentDataContainer().set(Singer.MODE, PersistentDataType.STRING, "soul_event");
                mob.getPersistentDataContainer().set(Singer.SOUL_GOLEM, PersistentDataType.STRING, golemId);
            });
            return base == null ? null : mobs.mob(base);
        }
        return null;
    }

    private static boolean free(Block floor, int height) {
        for (int up = 1; up <= height; up++) {
            Block block = floor.getRelative(0, up, 0);
            if (!block.isPassable() || block.isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /** Within 24 blocks (horizontally) of a canyon whose souls are within 48 blocks. */
    private boolean nearCanyon(double x, double y, double z) {
        SoulCanyonLandmarks marks = landmarks.get();
        if (marks == null) {
            return false;
        }
        for (SoulCanyonLandmarks.Pos cluster : marks.near(SoulCanyonLandmarks.Kind.CLUSTER, x, y, z, 48, 48)) {
            if (cluster.horizontalDistanceSquared(x, z) <= 576) {
                return true;
            }
        }
        return false;
    }
}
