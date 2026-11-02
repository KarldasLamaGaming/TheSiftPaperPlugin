package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The soul canyon markers of the original's SiftLandmarkTracker: entrances, midpoints, clusters (centre of
 * the soul blocks), every soul block, and entrances still waiting for their echo golem. Recorded from
 * worldgen threads and commands, kept in the Sift world's persistent data (one long array per kind,
 * positions packed like vanilla BlockPos), written on the main thread. Soul blocks that players place or
 * break in the Sift are recorded and forgotten too, like SoulBlock#onPlace in the original.
 */
public final class SoulCanyonLandmarks implements Listener {

    public enum Kind {
        ENTRANCE("soul_canyon_entrances"),
        MIDPOINT("soul_canyon_midpoints"),
        CLUSTER("soul_canyon_clusters"),
        SOUL_BLOCK("soul_canyon_soul_blocks"),
        GOLEM_PENDING("soul_canyon_golems_pending");

        final NamespacedKey key;

        Kind(String name) {
            this.key = new NamespacedKey("the_sift", name);
        }
    }

    public record Pos(int x, int y, int z) {

        public double distanceSquared(double px, double py, double pz) {
            double dx = x + 0.5 - px;
            double dy = y - py;
            double dz = z + 0.5 - pz;
            return dx * dx + dy * dy + dz * dz;
        }

        public double horizontalDistanceSquared(double px, double pz) {
            double dx = x + 0.5 - px;
            double dz = z + 0.5 - pz;
            return dx * dx + dz * dz;
        }
    }

    /** A canyon as found by {@link #nearestCanyon}: its cluster with the nearest entrance and midpoint. */
    public record Canyon(Pos cluster, Pos entrance, Pos midpoint, int souls) {
    }

    public static final String SOUL_BLOCK_ID = "soul_block";

    private final Map<Kind, Set<Long>> markers = new EnumMap<>(Kind.class);
    /** Cluster → {entrance, midpoint} of the same canyon (since alpha.19; older canyons fall back to the nearest). */
    private final Map<Long, long[]> links = new ConcurrentHashMap<>();
    private static final NamespacedKey LINKS = new NamespacedKey("the_sift", "soul_canyon_links");
    private final CustomBlockRegistry registry;
    private volatile boolean dirty;

    public SoulCanyonLandmarks(CustomBlockRegistry registry) {
        this.registry = registry;
        for (Kind kind : Kind.values()) {
            markers.put(kind, ConcurrentHashMap.newKeySet());
        }
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    public static Pos unpack(long packed) {
        return new Pos((int) (packed >> 38), (int) (packed << 52 >> 52), (int) (packed << 26 >> 38));
    }

    /** Thread-safe. */
    public void record(Kind kind, int x, int y, int z) {
        if (markers.get(kind).add(pack(x, y, z))) {
            dirty = true;
        }
    }

    public void forget(Kind kind, int x, int y, int z) {
        if (markers.get(kind).remove(pack(x, y, z))) {
            dirty = true;
        }
    }

    /** Remembers which entrance and midpoint belong to a canyon's cluster. */
    public void link(int clusterX, int clusterY, int clusterZ, int[] entrance, int[] midpoint) {
        links.put(pack(clusterX, clusterY, clusterZ), new long[] {pack(entrance[0], entrance[1], entrance[2]),
                pack(midpoint[0], midpoint[1], midpoint[2])});
        dirty = true;
    }

    /** The canyon's own entrance, else the nearest within 96 blocks (like the original). */
    public Pos entranceOf(Pos cluster) {
        long[] link = links.get(pack(cluster.x(), cluster.y(), cluster.z()));
        return link != null ? unpack(link[0]) : nearest(Kind.ENTRANCE, cluster.x() + 0.5, cluster.y(), cluster.z() + 0.5, 96);
    }

    /** The canyon's own midpoint, else the nearest within 72 blocks (like the original). */
    public Pos midpointOf(Pos cluster) {
        long[] link = links.get(pack(cluster.x(), cluster.y(), cluster.z()));
        return link != null ? unpack(link[1]) : nearest(Kind.MIDPOINT, cluster.x() + 0.5, cluster.y(), cluster.z() + 0.5, 72);
    }

    public int count(Kind kind) {
        return markers.get(kind).size();
    }

    public List<Pos> all(Kind kind) {
        List<Pos> list = new ArrayList<>();
        for (long packed : markers.get(kind)) {
            list.add(unpack(packed));
        }
        return list;
    }

    /** Markers of a kind within {@code horizontal} blocks (circle) and {@code vertical} blocks of a point. */
    public List<Pos> near(Kind kind, double x, double y, double z, double horizontal, double vertical) {
        List<Pos> list = new ArrayList<>();
        double h2 = horizontal * horizontal;
        for (long packed : markers.get(kind)) {
            Pos pos = unpack(packed);
            if (pos.horizontalDistanceSquared(x, z) <= h2 && Math.abs(pos.y() - y) <= vertical) {
                list.add(pos);
            }
        }
        return list;
    }

    public Pos nearest(Kind kind, double x, double y, double z, double maxDistance) {
        Pos best = null;
        double bestDistance = maxDistance * maxDistance;
        for (long packed : markers.get(kind)) {
            Pos pos = unpack(packed);
            double d = pos.distanceSquared(x, y, z);
            if (d <= bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        return best;
    }

    /** The canyon whose cluster is nearest (horizontally) within {@code maxDistance}, with its markers. */
    public Canyon nearestCanyon(double x, double z, double maxDistance) {
        Pos best = null;
        double bestDistance = maxDistance * maxDistance;
        for (long packed : markers.get(Kind.CLUSTER)) {
            Pos pos = unpack(packed);
            double d = pos.horizontalDistanceSquared(x, z);
            if (d <= bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        if (best == null) {
            return null;
        }
        Pos entrance = entranceOf(best);
        Pos midpoint = midpointOf(best);
        int souls = near(Kind.SOUL_BLOCK, best.x() + 0.5, best.y(), best.z() + 0.5, 38, 28).size();
        return new Canyon(best, entrance, midpoint, souls);
    }

    public void load(World world) {
        var pdc = world.getPersistentDataContainer();
        for (Kind kind : Kind.values()) {
            long[] stored = pdc.get(kind.key, PersistentDataType.LONG_ARRAY);
            if (stored != null) {
                for (long packed : stored) {
                    markers.get(kind).add(packed);
                }
            }
        }
        long[] stored = pdc.get(LINKS, PersistentDataType.LONG_ARRAY);
        if (stored != null) {
            for (int i = 0; i + 2 < stored.length; i += 3) {
                links.put(stored[i], new long[] {stored[i + 1], stored[i + 2]});
            }
        }
    }

    /** Main thread only. */
    public void save(World world) {
        if (!dirty) {
            return;
        }
        dirty = false;
        var pdc = world.getPersistentDataContainer();
        for (Kind kind : Kind.values()) {
            pdc.set(kind.key, PersistentDataType.LONG_ARRAY, markers.get(kind).stream().mapToLong(Long::longValue).toArray());
        }
        long[] flat = new long[links.size() * 3];
        int i = 0;
        for (var entry : links.entrySet()) {
            flat[i++] = entry.getKey();
            flat[i++] = entry.getValue()[0];
            flat[i++] = entry.getValue()[1];
        }
        pdc.set(LINKS, PersistentDataType.LONG_ARRAY, flat);
    }

    private boolean isSoulBlock(Block block) {
        CustomBlock custom = registry.at(block);
        return custom != null && SOUL_BLOCK_ID.equals(custom.id());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (SiftWorlds.isSift(block.getWorld()) && isSoulBlock(block)) {
            record(Kind.SOUL_BLOCK, block.getX(), block.getY(), block.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (SiftWorlds.isSift(block.getWorld())) {
            forget(Kind.SOUL_BLOCK, block.getX(), block.getY(), block.getZ());
        }
    }
}
