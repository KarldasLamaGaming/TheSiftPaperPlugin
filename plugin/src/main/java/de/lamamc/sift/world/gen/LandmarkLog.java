package de.lamamc.sift.world.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;

/**
 * What worldgen did with each abandoned portal candidate: built (with the chest position) or rejected
 * (with the reason and the position that decided it). Kept in the Sift world's persistent data, so it
 * survives restarts; written on the main thread, recorded from worldgen threads.
 */
public final class LandmarkLog {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "abandoned_portal_log");

    public enum Status { BUILT, REJECTED }

    /** {@code x y z}: chest of a built portal, or the block that decided a rejection. */
    public record Entry(int chunkX, int chunkZ, Status status, int x, int y, int z, String reason) {

        String serialize() {
            return chunkX + " " + chunkZ + " " + status + " " + x + " " + y + " " + z + " " + (reason == null ? "" : reason);
        }

        static Entry parse(String line) {
            String[] parts = line.split(" ", 7);
            if (parts.length < 6) {
                return null;
            }
            try {
                return new Entry(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Status.valueOf(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]),
                        parts.length == 7 && !parts[6].isEmpty() ? parts[6] : null);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public void record(Entry entry) {
        entries.put(key(entry.chunkX(), entry.chunkZ()), entry);
        dirty = true;
    }

    public Entry get(int chunkX, int chunkZ) {
        return entries.get(key(chunkX, chunkZ));
    }

    public List<Entry> all() {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort((a, b) -> a.status() != b.status() ? a.status().compareTo(b.status())
                : a.chunkX() != b.chunkX() ? Integer.compare(a.chunkX(), b.chunkX()) : Integer.compare(a.chunkZ(), b.chunkZ()));
        return list;
    }

    public void load(World world) {
        String stored = world.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (stored == null) {
            return;
        }
        for (String line : stored.split("\n")) {
            Entry entry = Entry.parse(line);
            if (entry != null) {
                entries.putIfAbsent(key(entry.chunkX(), entry.chunkZ()), entry);
            }
        }
    }

    /** Main thread only. */
    public void save(World world) {
        if (!dirty) {
            return;
        }
        dirty = false;
        StringBuilder text = new StringBuilder();
        for (Entry entry : all()) {
            text.append(entry.serialize()).append('\n');
        }
        world.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, text.toString());
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
