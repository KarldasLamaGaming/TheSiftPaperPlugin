package de.lamamc.sift.world.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;

/** The portals of every world, kept in each world's persistent data (one line per portal). Main thread. */
public final class PortalRegistry {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "portals");

    private final Map<String, Map<String, SiftPortal>> byWorld = new ConcurrentHashMap<>();

    private Map<String, SiftPortal> portals(World world) {
        return byWorld.computeIfAbsent(world.getKey().toString(), key -> load(world));
    }

    private static Map<String, SiftPortal> load(World world) {
        Map<String, SiftPortal> portals = new ConcurrentHashMap<>();
        String stored = world.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (stored != null) {
            for (String line : stored.split("\n")) {
                SiftPortal portal = SiftPortal.parse(line);
                if (portal != null) {
                    portals.put(portal.id(), portal);
                }
            }
        }
        return portals;
    }

    private void save(World world) {
        StringBuilder text = new StringBuilder();
        portals(world).values().stream().sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(p -> text.append(p.serialize()).append('\n'));
        world.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, text.toString());
    }

    public List<SiftPortal> all(World world) {
        List<SiftPortal> list = new ArrayList<>(portals(world).values());
        list.sort((a, b) -> a.id().compareTo(b.id()));
        return list;
    }

    public Optional<SiftPortal> get(World world, String id) {
        return Optional.ofNullable(portals(world).get(id));
    }

    public void put(World world, SiftPortal portal) {
        portals(world).put(portal.id(), portal);
        save(world);
    }

    public void remove(World world, String id) {
        if (portals(world).remove(id) != null) {
            save(world);
        }
    }

    /** The portal whose surface contains this block. */
    public Optional<SiftPortal> atSurface(World world, int x, int y, int z) {
        return portals(world).values().stream().filter(p -> p.frame().containsInterior(x, y, z)).findFirst();
    }

    /** The portal whose border contains this block. */
    public Optional<SiftPortal> atFrame(World world, int x, int y, int z) {
        return portals(world).values().stream().filter(p -> p.frame().containsFrame(x, y, z)).findFirst();
    }

    /** The portal one of whose consoles is at this block. */
    public Optional<SiftPortal> withConsole(World world, int x, int y, int z) {
        return portals(world).values().stream()
                .filter(p -> p.consoles().stream().anyMatch(c -> c[0] == x && c[1] == y && c[2] == z))
                .findFirst();
    }

    /** A free id like "portal-3" for this world. */
    public String newId(World world, String prefix) {
        int i = 1;
        while (portals(world).containsKey(prefix + "-" + i)) {
            i++;
        }
        return prefix + "-" + i;
    }
}
