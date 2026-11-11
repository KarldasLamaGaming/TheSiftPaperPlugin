package de.lamamc.sift.world.temple;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.temple.TempleLayout;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The temples of the Sift (phase 4, alpha.41a): which are built and in which state, kept in the Sift's world data
 * ({@code the_sift:temples}). A temple's boss can be defeated once (per temple, not per player); until then its
 * blocks cannot be broken or blown up, its Sculk Enchanter is locked and the Seelen-Altar sleeps. The enchanter and the
 * sealed chambers stay protected after the victory as well.
 */
public final class TempleRegistry implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "temples");
    public static final String BYPASS = "thesift.temple.bypass";

    public enum State { SLEEPING, FIGHT, DEFEATED }

    /** A registered temple; {@code y} is the level players stand on. */
    public record Temple(String id, int x, int y, int z, State state, long defeatedAt) {

        Temple withState(State newState, long at) {
            return new Temple(id, x, y, z, newState, at);
        }

        String serialize() {
            return id + " " + x + " " + y + " " + z + " " + state + " " + defeatedAt;
        }

        static Temple parse(String line) {
            String[] p = line.trim().split(" ");
            try {
                return new Temple(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), State.valueOf(p[4]),
                        Long.parseLong(p[5]));
            } catch (RuntimeException e) {
                return null;
            }
        }

        boolean contains(int bx, int by, int bz) {
            return Math.abs(bx - x) <= TempleLayout.RADIUS && Math.abs(bz - z) <= TempleLayout.RADIUS
                    && by >= y - 1 - 24 && by < y + TempleLayout.CLEAR_HEIGHT;
        }

        boolean at(TempleLayout.Pos pos, int bx, int by, int bz) {
            return bx == x + pos.x() && by == y + pos.y() && bz == z + pos.z();
        }
    }

    /** Positions that are the same in every temple (decay changes only the walls and floor). */
    private static final TempleLayout.Layout MARKERS = TempleLayout.build(0);

    private final Plugin plugin;
    private final CustomBlockRegistry blocks;
    private final TempleBuilder builder;
    private final boolean protect;
    private final boolean enchanterRequiresDefeat;
    private final String altarMessage;
    private final Map<String, Temple> temples = new LinkedHashMap<>();
    private Consumer<Block> markEnchanter = b -> { };
    private World sift;

    public TempleRegistry(Plugin plugin, CustomBlockRegistry blocks, TempleBuilder builder, boolean protect,
                          boolean enchanterRequiresDefeat, String altarMessage) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.builder = builder;
        this.protect = protect;
        this.enchanterRequiresDefeat = enchanterRequiresDefeat;
        this.altarMessage = altarMessage;
    }

    public TempleBuilder builder() {
        return builder;
    }

    /** How the temple's enchanter becomes a Sculk Enchanter (particles, crystal) when its chunk is loaded. */
    public void onEnchanter(Consumer<Block> mark) {
        this.markEnchanter = mark;
    }

    public void start(World sift) {
        this.sift = sift;
        String stored = sift.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (stored != null) {
            for (String line : stored.split("\n")) {
                Temple t = Temple.parse(line);
                if (t != null) {
                    temples.put(t.id(), t);
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 20L, 20L);
    }

    /** Built temples from the worldgen threads into the registry. */
    private void drain() {
        boolean changed = false;
        TempleBuilder.Built b;
        while ((b = builder.built().poll()) != null) {
            TempleSites.Site site = b.site();
            if (!temples.containsKey(site.id())) {
                temples.put(site.id(), new Temple(site.id(), site.x(), b.floorY(), site.z(), State.SLEEPING, 0));
                plugin.getLogger().info("Temple " + site.id() + " registered at " + site.x() + " " + b.floorY() + " " + site.z());
                changed = true;
            }
            Temple t = temples.get(site.id());
            if (sift.isChunkLoaded(t.x() >> 4, t.z() >> 4)) {
                markEnchanter.accept(enchanterBlock(t));
            }
        }
        if (changed) {
            save();
        }
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        temples.values().forEach(t -> text.append(t.serialize()).append('\n'));
        sift.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, text.toString());
    }

    public List<Temple> all() {
        return new ArrayList<>(temples.values());
    }

    public Optional<Temple> get(String id) {
        return Optional.ofNullable(temples.get(id));
    }

    public Optional<Temple> at(Block block) {
        if (!SiftWorlds.isSift(block.getWorld())) {
            return Optional.empty();
        }
        return temples.values().stream().filter(t -> t.contains(block.getX(), block.getY(), block.getZ())).findFirst();
    }

    public Optional<Temple> nearest(double x, double z, boolean onlyOpen) {
        return temples.values().stream().filter(t -> !onlyOpen || t.state() != State.DEFEATED)
                .min(Comparator.comparingDouble(t -> (t.x() - x) * (t.x() - x) + (t.z() - z) * (t.z() - z)));
    }

    /**
     * The nearest site whose temple is not defeated (alpha.41b), built or not yet generated; null when all are
     * defeated. exclude: a temple id to skip (the temple a chest stands in), or null.
     */
    public TempleSites.Site nearestOpenSite(double x, double z, String exclude) {
        return TempleSites.nearest(builder.sites(), x, z, site -> !site.id().equals(exclude)
                && (temples.get(site.id()) == null || temples.get(site.id()).state() != State.DEFEATED));
    }

    /** The y of the temple floor at this site, or the fallback when it is not built yet. */
    public int floorY(TempleSites.Site site, int fallback) {
        Temple t = temples.get(site.id());
        return t == null ? fallback : t.y();
    }

    /** Admin: back to sleep (alpha.41a: the state only; the arena is restored with the boss in alpha.42). */
    public boolean reset(String id) {
        Temple t = temples.get(id);
        if (t == null) {
            return false;
        }
        temples.put(id, t.withState(State.SLEEPING, 0));
        save();
        return true;
    }

    /** For the boss (alpha.42) and tests: the boss of this temple is defeated. */
    public void defeat(String id) {
        Temple t = temples.get(id);
        if (t != null) {
            temples.put(id, t.withState(State.DEFEATED, System.currentTimeMillis()));
            save();
        }
    }

    public Block enchanterBlock(Temple t) {
        TempleLayout.Pos p = MARKERS.enchanter();
        return sift.getBlockAt(t.x() + p.x(), t.y() + p.y(), t.z() + p.z());
    }

    public Block altarBlock(Temple t) {
        TempleLayout.Pos p = MARKERS.altar();
        return sift.getBlockAt(t.x() + p.x(), t.y() + p.y(), t.z() + p.z());
    }

    /** Why this Sculk Enchanter cannot be used yet (a temple enchanter before the victory), or null. */
    public String lockReason(Block block) {
        if (!enchanterRequiresDefeat) {
            return null;
        }
        Optional<Temple> t = at(block);
        if (t.isEmpty() || !t.get().at(MARKERS.enchanter(), block.getX(), block.getY(), block.getZ()) || t.get().state() == State.DEFEATED) {
            return null;
        }
        return "Der Sculk Enchanter des Tempels erwacht erst, wenn sein Wächter besiegt ist.";
    }

    // ---- protection -----------------------------------------------------------------------------------------

    /** Whether this block is part of a temple that must not be broken now. */
    public boolean protectedBlock(Block block) {
        if (!protect) {
            return false;
        }
        Optional<Temple> found = at(block);
        if (found.isEmpty()) {
            return false;
        }
        Temple t = found.get();
        boolean permanent = t.at(MARKERS.enchanter(), block.getX(), block.getY(), block.getZ())
                || t.at(MARKERS.altar(), block.getX(), block.getY(), block.getZ())
                || MARKERS.chambers().values().stream().anyMatch(c -> new TempleLayout.Box(
                        new TempleLayout.Pos(c.min().x() - 1, c.min().y() - 1, c.min().z() - 1),
                        new TempleLayout.Pos(c.max().x() + 1, c.max().y() + 1, c.max().z() + 1))
                        .contains(block.getX() - t.x(), block.getY() - t.y(), block.getZ() - t.z()));
        if (!permanent && t.state() == State.DEFEATED) {
            return false;
        }
        if (block.getType() == Material.CHEST) {
            return true;
        }
        CustomBlock custom = blocks.at(block);
        return custom != null && TempleLayout.PALETTE.contains(custom.id());
    }

    // LOW: before the custom block and enchanter listeners (HIGH), so a cancelled break changes nothing there
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedBlock(event.getBlock()) && !event.getPlayer().hasPermission(BYPASS)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Der Tempel lässt sich nicht abtragen, solange sein Wächter schläft.",
                    NamedTextColor.DARK_AQUA));
        }
    }

    // LOW for explosions and pistons too: the custom block listener (HIGH) takes Sift blocks off the explosion list and
    // breaks them itself, so the temple must take its blocks off first
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::protectedBlock)) {
            event.setCancelled(true);
        }
    }

    /** The Seelen-Altar sleeps until the boss comes (alpha.42). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAltar(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Optional<Temple> t = at(block);
        if (t.isPresent() && t.get().at(MARKERS.altar(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendActionBar(Component.text(t.get().state() == State.DEFEATED ? "Der Wächter dieses Tempels ist besiegt."
                    : altarMessage, NamedTextColor.DARK_AQUA));
        }
    }

    /** A temple's enchanter gets its mark (particles, crystal) when its chunk loads. */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!SiftWorlds.isSift(event.getWorld())) {
            return;
        }
        for (Temple t : temples.values()) {
            TempleLayout.Pos p = MARKERS.enchanter();
            if ((t.x() + p.x()) >> 4 == event.getChunk().getX() && (t.z() + p.z()) >> 4 == event.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> markEnchanter.accept(enchanterBlock(t)));
            }
        }
    }
}
