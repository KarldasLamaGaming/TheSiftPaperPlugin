package de.lamamc.sift.mob.singer;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import de.lamamc.sift.core.mob.SingerRules;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.WanderingTrader;

/**
 * A staying Singer's walk (tickSoulRoaming of the original): goes for a dropped soul block within reach and
 * trades it, otherwise strolls every 3–6 s to a random spot within 12 blocks, but only within the leash
 * radius around where it appeared and only along paths with room for its height.
 */
final class SingerRoamGoal implements Goal<WanderingTrader> {

    static final GoalKey<WanderingTrader> KEY = GoalKey.of(WanderingTrader.class, new NamespacedKey("the_sift", "singer_roam"));

    private final WanderingTrader trader;
    private final SingerKind kind;
    private int cooldown;
    private int tick;

    SingerRoamGoal(WanderingTrader trader, SingerKind kind) {
        this.trader = trader;
        this.kind = kind;
    }

    private Singer singer() {
        return kind.manager() == null ? null : kind.manager().mob(trader) instanceof Singer s ? s : null;
    }

    @Override
    public boolean shouldActivate() {
        Singer singer = singer();
        return singer != null && singer.roaming();
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void tick() {
        Singer singer = singer();
        if (singer == null) {
            return;
        }
        tick++;
        Item payment = singer.pickupTarget();
        if (payment != null) {
            if (trader.getBoundingBox().expand(0.8).overlaps(payment.getBoundingBox())) {
                singer.pickUp(payment);
            } else if (!trader.getPathfinder().hasPath() || tick % 10 == 0) {
                move(payment.getLocation(), 1.0);
            }
            return;
        }
        Location home = singer.home();
        double leash = kind.config().getDouble("leash-radius", 8);
        if (horizontal(trader.getLocation(), home) > (leash + 2) * (leash + 2)) {
            move(home, 0.78); // pushed away: back home
            return;
        }
        if (--cooldown <= 0 || !trader.getPathfinder().hasPath()) {
            cooldown = 60 + ThreadLocalRandom.current().nextInt(61);
            for (int attempt = 0; attempt < 5; attempt++) {
                Location destination = randomDestination(home, leash);
                if (destination != null && move(destination, 0.78)) {
                    break;
                }
            }
        }
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private Location randomDestination(Location home, double leash) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location at = trader.getLocation();
        int x = at.getBlockX() + random.nextInt(-12, 13);
        int z = at.getBlockZ() + random.nextInt(-12, 13);
        Location candidate = new Location(at.getWorld(), x + 0.5, at.getY(), z + 0.5);
        if (horizontal(candidate, home) > leash * leash) {
            return null;
        }
        for (int dy = 6; dy >= -6; dy--) {
            Block feet = at.getWorld().getBlockAt(x, at.getBlockY() + dy, z);
            if (feet.getRelative(0, -1, 0).getType().isSolid() && feet.isPassable() && !feet.isLiquid()
                    && free(feet, height()) >= height()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }

    private int height() {
        return (int) Math.ceil(trader.getHeight());
    }

    /** Free blocks above (and including) this one, up to {@code limit}. */
    private static int free(Block feet, int limit) {
        int free = 0;
        while (free < limit && feet.getRelative(0, free, 0).isPassable() && !feet.getRelative(0, free, 0).isLiquid()) {
            free++;
        }
        return free;
    }

    /** Walks only along paths whose every point has room for the Singer's height. */
    private boolean move(Location target, double speed) {
        Pathfinder pathfinder = trader.getPathfinder();
        Pathfinder.PathResult path = pathfinder.findPath(target);
        if (path == null) {
            return false;
        }
        List<Location> points = path.getPoints();
        int[] room = new int[points.size()];
        World world = trader.getWorld();
        int height = height();
        for (int i = 0; i < room.length; i++) {
            Location p = points.get(i);
            room[i] = free(world.getBlockAt(p.getBlockX(), p.getBlockY(), p.getBlockZ()), height);
        }
        if (!SingerRules.headroomOk(room, height)) {
            return false;
        }
        return pathfinder.moveTo(path, speed);
    }

    @Override
    public GoalKey<WanderingTrader> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE);
    }
}
