package de.lamamc.sift.world.portal;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import de.lamamc.sift.core.portal.ConsoleSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.AsyncStructureGenerateEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;

/**
 * The portal in the centre of every new Ancient City, like the original: the datapack replaces the city
 * centre templates with the original's (frame of reinforced deepslate, 8 consoles). The consoles stand in
 * the templates as a rotation-proof placeholder (lodestone); while the city generates they become sleeping
 * sonorous deepslate. Once all of them and the frame exist, the portal is registered (kind ANCIENT_CITY,
 * LOCKED) and note blocks with random sounds are put on the consoles, like at the shrines.
 */
public final class AncientCityPortals implements Listener {

    static final String PLACEHOLDER = "minecraft:lodestone";
    private static final NamespacedKey TRANSFORMER = new NamespacedKey("the_sift", "ancient_city_consoles");
    private static final int CONSOLES = 8;
    private static final int MAX_TRIES = 120;

    private final Plugin plugin;
    private final CustomBlockRegistry blocks;
    private final PortalService portals;
    private final int frameRadius;
    private final Logger logger;
    private final BlockData hornConsole;
    /**
     * Consoles placed so far per world (worldgen threads add, the main thread registers). The structure box the
     * event reports is not the same in every chunk, so consoles are grouped by distance instead.
     */
    private final Map<String, List<int[]>> placed = new ConcurrentHashMap<>();
    private final Map<String, Integer> tries = new ConcurrentHashMap<>();

    public AncientCityPortals(Plugin plugin, CustomBlockRegistry blocks, PortalService portals, int frameRadius, Logger logger) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.portals = portals;
        this.frameRadius = frameRadius;
        this.logger = logger;
        CustomBlock state = blocks.state(SonorousConsoles.BLOCK_ID, Map.of("mode", "horn"));
        this.hornConsole = state == null ? null : state.data();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::register, 100L, 40L);
    }

    @EventHandler
    public void onGenerate(AsyncStructureGenerateEvent event) {
        if (hornConsole == null || !Structure.ANCIENT_CITY.equals(event.getStructure())) {
            return;
        }
        String world = event.getWorld().getKey().toString();
        event.setBlockTransformer(TRANSFORMER, (region, x, y, z, current, state) -> {
            if (current.getType() != Material.LODESTONE) {
                return current;
            }
            placed.computeIfAbsent(world, k -> new CopyOnWriteArrayList<>()).add(new int[] {x, y, z});
            return hornConsole.createBlockState();
        });
    }

    /** Main thread: registers every city whose 8 consoles are placed and whose frame is generated. */
    private void register() {
        for (var entry : placed.entrySet()) {
            NamespacedKey worldKey = NamespacedKey.fromString(entry.getKey());
            World world = worldKey == null ? null : Bukkit.getWorld(worldKey);
            List<int[]> all = entry.getValue();
            if (world == null) {
                all.clear();
                continue;
            }
            List<int[]> left = new ArrayList<>(all);
            while (!left.isEmpty()) {
                int[] first = left.getFirst();
                List<int[]> city = new ArrayList<>();
                for (int[] c : left) {
                    if (Math.abs(c[0] - first[0]) <= 24 && Math.abs(c[1] - first[1]) <= 8 && Math.abs(c[2] - first[2]) <= 24) {
                        city.add(c);
                    }
                }
                left.removeAll(city);
                if (city.size() < CONSOLES) {
                    continue;
                }
                String key = entry.getKey() + " " + first[0] + " " + first[1] + " " + first[2];
                Optional<PortalFrame> frame = findFrame(world, city);
                if (frame.isEmpty()) {
                    if (tries.merge(key, 1, Integer::sum) > MAX_TRIES) {
                        logger.warning("Ancient City at " + first[0] + " " + first[2] + ": no portal frame found, consoles left unregistered");
                        all.removeAll(city);
                        tries.remove(key);
                    }
                    continue;
                }
                all.removeAll(city);
                tries.remove(key);
                registerCity(world, frame.get(), city);
            }
        }
    }

    private void registerCity(World world, PortalFrame f, List<int[]> consoles) {
        if (portals.registry().all(world).stream().anyMatch(p -> p.frame().equals(f))) {
            return;
        }
        for (int[] c : consoles) {
            Block above = world.getBlockAt(c[0], c[1] + 1, c[2]);
            // the city's decoration (candles, carpets, sculk veins) makes room for the console's note block
            if (above.getType().isAir() || above.isPassable() || org.bukkit.Tag.CANDLES.isTagged(above.getType())
                    || org.bukkit.Tag.WOOL_CARPETS.isTagged(above.getType())) {
                above.setType(Material.NOTE_BLOCK, false);
                NoteBlock note = (NoteBlock) above.getBlockData();
                note.setNote(new org.bukkit.Note(ConsoleSounds.noteOf(1 + ThreadLocalRandom.current().nextInt(ConsoleSounds.COUNT))));
                above.setBlockData(note, false);
            }
        }
        int[] c = consoles.getFirst();
        SiftPortal portal = new SiftPortal(portals.registry().newId(world, "city"), SiftPortal.Kind.ANCIENT_CITY, f,
                PortalState.LOCKED, List.copyOf(consoles));
        portals.registry().put(world, portal);
        logger.info("Ancient City portal " + portal.id() + " registered at " + c[0] + " " + c[1] + " " + c[2]);
    }

    private Optional<PortalFrame> findFrame(World world, List<int[]> consoles) {
        double cx = 0;
        double cy = 0;
        double cz = 0;
        for (int[] c : consoles) {
            cx += c[0];
            cy += c[1];
            cz += c[2];
        }
        int x0 = (int) Math.floor(cx / consoles.size());
        int y0 = (int) Math.floor(cy / consoles.size());
        int z0 = (int) Math.floor(cz / consoles.size());
        PortalFrame.Grid grid = new PortalFrame.Grid() {
            @Override
            public boolean isFrame(int x, int y, int z) {
                return world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType() == Material.REINFORCED_DEEPSLATE;
            }

            @Override
            public boolean isOpen(int x, int y, int z) {
                return world.isChunkLoaded(x >> 4, z >> 4) && PortalSurface.isOpenInterior(world.getBlockAt(x, y, z));
            }
        };
        PortalFrame best = null;
        double bestDistance = Double.MAX_VALUE;
        int r = frameRadius + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!world.isChunkLoaded((x0 + dx) >> 4, (z0 + dz) >> 4)) {
                    return Optional.empty(); // the centre is not generated completely yet
                }
                for (int dy = -4; dy <= 16; dy++) {
                    if (!grid.isFrame(x0 + dx, y0 + dy, z0 + dz)) {
                        continue;
                    }
                    Optional<PortalFrame> frame = PortalFrame.scan(grid, x0 + dx, y0 + dy, z0 + dz);
                    if (frame.isPresent()) {
                        double[] b = frame.get().bottomCenter();
                        double d = Math.pow(b[0] - x0, 2) + Math.pow(b[1] - y0, 2) + Math.pow(b[2] - z0, 2);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = frame.get();
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
