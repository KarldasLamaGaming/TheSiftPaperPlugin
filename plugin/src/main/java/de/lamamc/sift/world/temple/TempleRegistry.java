package de.lamamc.sift.world.temple;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.temple.BroodSpawner;
import de.lamamc.sift.core.temple.TempleLayout;
import de.lamamc.sift.core.temple.TempleSites;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The temples of the Sift (phase 4, alpha.41a, bastion layout since alpha.41.1): which are built and in which state,
 * kept in the Sift's world data ({@code the_sift:temples}). A temple's boss can be defeated once (per temple, not per
 * player); until then its blocks cannot be broken or blown up, its Sculk Enchanter is locked and the Seelen-Altar
 * sleeps. The enchanter, the altar and the sealed trial rooms stay protected after the victory as well.
 *
 * <p>Parts of a temple whose chunk was generated before the floor was known wait in {@code the_sift:temple_pending}
 * and are built on the main thread as soon as their chunk is loaded (a few per tick).
 */
public final class TempleRegistry implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "temples");
    public static final NamespacedKey PENDING = new NamespacedKey("the_sift", "temple_pending");
    public static final NamespacedKey SPAWNERS = new NamespacedKey("the_sift", "temple_spawners");
    public static final String BYPASS = "thesift.temple.bypass";
    /** Layout versions: 1 = alpha.41a (41 x 41, one chunk), 2 = alpha.41.1 bastion, 3 = with brood spawners (alpha.42a). */
    public static final int CURRENT = 3;
    /** Oldest version built with the bastion layout (markers, size). */
    public static final int BASTION = 2;

    public enum State { SLEEPING, FIGHT, DEFEATED }

    /** A registered temple; {@code y} is the level players stand on in the court. */
    public record Temple(String id, int x, int y, int z, State state, long defeatedAt, int version) {

        Temple withState(State newState, long at) {
            return new Temple(id, x, y, z, newState, at, version);
        }

        String serialize() {
            return id + " " + x + " " + y + " " + z + " " + state + " " + defeatedAt + " " + version;
        }

        static Temple parse(String line) {
            String[] p = line.trim().split(" ");
            try {
                return new Temple(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), State.valueOf(p[4]),
                        Long.parseLong(p[5]), p.length > 6 ? Integer.parseInt(p[6]) : 1);
            } catch (RuntimeException e) {
                return null;
            }
        }

        boolean at(TempleLayout.Pos pos, int bx, int by, int bz) {
            return bx == x + pos.x() && by == y + pos.y() && bz == z + pos.z();
        }
    }

    /** Positions that are the same in every temple of a version (weathering changes only walls and floors). */
    private record Markers(TempleLayout.Pos altar, TempleLayout.Pos enchanter, List<TempleLayout.Box> rooms, int extent, int bottom,
                           int top) {

        boolean room(Temple t, Block b) {
            return rooms.stream().anyMatch(c -> new TempleLayout.Box(
                    new TempleLayout.Pos(c.min().x() - 1, c.min().y() - 1, c.min().z() - 1),
                    new TempleLayout.Pos(c.max().x() + 1, c.max().y() + 1, c.max().z() + 1))
                    .contains(b.getX() - t.x(), b.getY() - t.y(), b.getZ() - t.z()));
        }
    }

    /** alpha.41a: 41 x 41, enchanter in the south hall, chambers east and west, foundation 24 deep. */
    private static final Markers LEGACY = new Markers(new TempleLayout.Pos(0, 2, 0), new TempleLayout.Pos(0, 0, 17),
            List.of(new TempleLayout.Box(new TempleLayout.Pos(14, 0, -2), new TempleLayout.Pos(18, 4, 2)),
                    new TempleLayout.Box(new TempleLayout.Pos(-18, 0, -2), new TempleLayout.Pos(-14, 4, 2))), 20, -25, 12);

    private static final Set<Material> VANILLA = TempleLayout.VANILLA.stream()
            .map(n -> Material.matchMaterial("minecraft:" + n)).filter(m -> m != null).collect(Collectors.toUnmodifiableSet());

    private final Plugin plugin;
    private final CustomBlockRegistry blocks;
    private final TempleBuilder builder;
    private final Markers markers;
    private final boolean protect;
    private final boolean enchanterRequiresDefeat;
    private final String altarMessage;
    private final int perTick;
    private final Map<String, Temple> temples = new LinkedHashMap<>();
    private final Set<TempleBuilder.Slice> pending = new LinkedHashSet<>();
    private final Deque<Runnable> rebuildSteps = new ArrayDeque<>();
    private Consumer<Block> markEnchanter = b -> { };
    private World sift;

    public TempleRegistry(Plugin plugin, CustomBlockRegistry blocks, TempleBuilder builder, boolean protect,
                          boolean enchanterRequiresDefeat, String altarMessage, int deferredPerTick) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.builder = builder;
        this.protect = protect;
        this.enchanterRequiresDefeat = enchanterRequiresDefeat;
        this.altarMessage = altarMessage;
        this.perTick = Math.max(1, deferredPerTick);
        TempleLayout.Layout plan = TempleLayout.build(0, builder.params());
        TempleLayout.Params p = builder.params();
        this.markers = new Markers(plan.altar(), plan.enchanter(), List.copyOf(plan.chambers().values()), p.extent(), p.bottom(),
                p.top());
        this.spawnerPlan = plan.spawners();
    }

    public TempleBuilder builder() {
        return builder;
    }

    /** How the temple's enchanter becomes a Sculk Enchanter (particles, crystal) when its chunk is loaded. */
    public void onEnchanter(Consumer<Block> mark) {
        this.markEnchanter = mark;
    }

    private Markers markers(Temple t) {
        return t.version() >= BASTION ? markers : LEGACY;
    }

    // ---- brood spawners (alpha.42a) ---------------------------------------------------------------------------

    private final Map<String, BroodSpawner.State[]> spawnerStates = new LinkedHashMap<>();
    private Consumer<Temple> onDefeat = t -> { };
    private boolean altarGate;

    /** Brood spawners of the plan (relative); only temples of version 3 or later have them. */
    public List<TempleLayout.Spawner> spawners() {
        return spawnerPlan;
    }

    private List<TempleLayout.Spawner> spawnerPlan = List.of();

    public boolean hasSpawners(Temple t) {
        return t.version() >= CURRENT && !spawnerPlan.isEmpty();
    }

    /** The current states of a temple's spawners (a copy). */
    public BroodSpawner.State[] spawnerStates(Temple t) {
        BroodSpawner.State[] states = spawnerStates.computeIfAbsent(t.id(), id -> fresh(t));
        return states.clone();
    }

    private BroodSpawner.State[] fresh(Temple t) {
        BroodSpawner.State[] states = new BroodSpawner.State[spawnerPlan.size()];
        java.util.Arrays.fill(states, t.state() == State.DEFEATED ? new BroodSpawner.State(BroodSpawner.Phase.OFF, 0, 0, 0)
                : BroodSpawner.State.FRESH);
        return states;
    }

    public void spawnerState(Temple t, int index, BroodSpawner.State state) {
        BroodSpawner.State[] states = spawnerStates.computeIfAbsent(t.id(), id -> fresh(t));
        if (!states[index].equals(state)) {
            states[index] = state;
            save();
        }
    }

    /** Sets all spawners of a temple (admin clear/reset, defeat). */
    public void allSpawners(Temple t, BroodSpawner.State state) {
        BroodSpawner.State[] states = new BroodSpawner.State[spawnerPlan.size()];
        java.util.Arrays.fill(states, state);
        spawnerStates.put(t.id(), states);
        save();
    }

    /** Spawners that are not exhausted (or off) yet; 0 for temples without spawners. */
    public int spawnersAwake(Temple t) {
        if (!hasSpawners(t)) {
            return 0;
        }
        int awake = 0;
        for (BroodSpawner.State s : spawnerStates(t)) {
            if (!s.done()) {
                awake++;
            }
        }
        return awake;
    }

    /** The Seelen-Altar waits until all brood spawners of its temple are exhausted (phase4.temples.spawners.altar-gate). */
    public void altarGate(boolean gate) {
        this.altarGate = gate;
    }

    /** Called when a temple's boss is defeated (the spawner runtime turns everything off). */
    public void onDefeat(Consumer<Temple> listener) {
        this.onDefeat = listener;
    }

    public Block spawnerBlock(Temple t, int index) {
        TempleLayout.Pos p = spawnerPlan.get(index).pos();
        return sift.getBlockAt(t.x() + p.x(), t.y() + p.y(), t.z() + p.z());
    }

    public void start(World sift) {
        this.sift = sift;
        String stored = sift.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (stored != null) {
            for (String line : stored.split("\n")) {
                Temple t = Temple.parse(line);
                if (t != null) {
                    if (t.state() == State.FIGHT) {
                        t = t.withState(State.SLEEPING, 0); // a fight never survives a restart (the boss is not saved)
                    }
                    temples.put(t.id(), t);
                    if (t.version() >= BASTION) {
                        builder.knownFloor(t.id(), t.y());
                    }
                }
            }
        }
        String waiting = sift.getPersistentDataContainer().get(PENDING, PersistentDataType.STRING);
        if (waiting != null) {
            for (String line : waiting.split("\n")) {
                String[] p = line.trim().split(" ");
                if (p.length == 3) {
                    try {
                        pending.add(new TempleBuilder.Slice(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                    } catch (NumberFormatException ignored) {
                        // skip a broken line
                    }
                }
            }
        }
        String spawnerText = sift.getPersistentDataContainer().get(SPAWNERS, PersistentDataType.STRING);
        if (spawnerText != null) {
            for (String line : spawnerText.split("\n")) {
                String[] p = line.trim().split(" ", 3);
                Temple t = p.length == 3 ? temples.get(p[0]) : null;
                if (t != null) {
                    try {
                        int index = Integer.parseInt(p[1]);
                        if (index >= 0 && index < spawnerPlan.size()) {
                            spawnerStates.computeIfAbsent(t.id(), id -> fresh(t))[index] = BroodSpawner.State.parse(p[2]);
                        }
                    } catch (NumberFormatException ignored) {
                        // skip a broken line
                    }
                }
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 21L, 1L);
    }

    /** Floors and parts from the worldgen threads into the registry. */
    private void drain() {
        boolean changed = false;
        TempleBuilder.Built b;
        while ((b = builder.built().poll()) != null) {
            TempleSites.Site site = b.site();
            if (!temples.containsKey(site.id())) {
                temples.put(site.id(), new Temple(site.id(), site.x(), b.floorY(), site.z(), State.SLEEPING, 0, CURRENT));
                plugin.getLogger().info("Temple " + site.id() + " registered at " + site.x() + " " + b.floorY() + " " + site.z());
                changed = true;
            }
            markEnchanterIfLoaded(temples.get(site.id()));
        }
        TempleBuilder.Slice s;
        while ((s = builder.waiting().poll()) != null) {
            changed |= pending.add(s);
        }
        while ((s = builder.sliced().poll()) != null) {
            changed |= pending.remove(s);
        }
        if (changed) {
            save();
        }
    }

    /** Builds waiting parts whose chunk is loaded (a few per tick), and steps of a running rebuild. */
    private void tick() {
        Runnable step = rebuildSteps.poll();
        if (step != null) {
            step.run();
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        int done = 0;
        boolean changed = false;
        for (var it = pending.iterator(); it.hasNext() && done < perTick; ) {
            TempleBuilder.Slice slice = it.next();
            Temple t = temples.get(slice.siteId());
            if (t == null || t.version() < BASTION) {
                continue; // floor not known yet (or an old temple, rebuilt by command)
            }
            if (!sift.isChunkLoaded(slice.chunkX(), slice.chunkZ())) {
                continue;
            }
            TempleSites.Site site = site(t.id());
            if (site != null) {
                builder.buildLate(new WorldCanvas(sift), site, t.y(), slice.chunkX(), slice.chunkZ(), lootSeed(t, slice));
                markEnchanterIfLoaded(t);
            }
            it.remove();
            done++;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    private long lootSeed(Temple t, TempleBuilder.Slice slice) {
        return sift.getSeed() ^ (long) t.id().hashCode() * 31 ^ TempleBuilder.key(slice.chunkX(), slice.chunkZ());
    }

    private TempleSites.Site site(String id) {
        return builder.sites().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    private void markEnchanterIfLoaded(Temple t) {
        Block e = enchanterBlock(t);
        if (sift.isChunkLoaded(e.getX() >> 4, e.getZ() >> 4)) {
            markEnchanter.accept(e);
        }
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        temples.values().forEach(t -> text.append(t.serialize()).append('\n'));
        sift.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, text.toString());
        StringBuilder waiting = new StringBuilder();
        pending.forEach(s -> waiting.append(s.siteId()).append(' ').append(s.chunkX()).append(' ').append(s.chunkZ()).append('\n'));
        sift.getPersistentDataContainer().set(PENDING, PersistentDataType.STRING, waiting.toString());
        StringBuilder spawnerText = new StringBuilder();
        spawnerStates.forEach((id, states) -> {
            for (int i = 0; i < states.length; i++) {
                spawnerText.append(id).append(' ').append(i).append(' ').append(states[i].serialize()).append('\n');
            }
        });
        sift.getPersistentDataContainer().set(SPAWNERS, PersistentDataType.STRING, spawnerText.toString());
    }

    public List<Temple> all() {
        return new ArrayList<>(temples.values());
    }

    public Optional<Temple> get(String id) {
        return Optional.ofNullable(temples.get(id));
    }

    /** Parts of this temple still waiting for their chunk. */
    public long pending(String id) {
        return pending.stream().filter(s -> s.siteId().equals(id)).count();
    }

    private boolean contains(Temple t, int bx, int by, int bz) {
        Markers m = markers(t);
        return Math.abs(bx - t.x()) <= m.extent() && Math.abs(bz - t.z()) <= m.extent()
                && by >= t.y() + m.bottom() && by <= t.y() + m.top();
    }

    public Optional<Temple> at(Block block) {
        if (!SiftWorlds.isSift(block.getWorld())) {
            return Optional.empty();
        }
        return temples.values().stream().filter(t -> contains(t, block.getX(), block.getY(), block.getZ())).findFirst();
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

    /** Admin: back to sleep (the state only; the arena is restored with the boss in alpha.42). */
    public boolean reset(String id) {
        Temple t = temples.get(id);
        if (t == null) {
            return false;
        }
        temples.put(id, t.withState(State.SLEEPING, 0));
        spawnerStates.remove(id); // fresh again
        save();
        return true;
    }

    /** A boss fight began (FIGHT) or ended without a victory (back to SLEEPING); the spawners keep their state. */
    public void fighting(String id, boolean fight) {
        Temple t = temples.get(id);
        if (t != null && t.state() != State.DEFEATED) {
            temples.put(id, t.withState(fight ? State.FIGHT : State.SLEEPING, 0));
            save();
        }
    }

    /** Whether this temple has the arena the boss needs (bastion layout, alpha.41.1 or later). */
    public boolean hasArena(Temple t) {
        return t.version() >= BASTION;
    }

    /** Arena centre block (x, z) and the level fighters stand on (y), for temples with an arena. */
    public org.bukkit.Location arenaCenter(Temple t) {
        TempleLayout.Pos altar = markers(t).altar();
        return new org.bukkit.Location(sift, t.x() + altar.x() + 0.5, t.y() + altar.y() - 2, t.z() + altar.z() + 0.5);
    }

    /** Top of the keep (the shaft opens there), absolute y. */
    public int keepTop(Temple t) {
        return t.y() + TempleLayout.KEEP_TOP + 1;
    }

    /** Positions of the soul blocks in the arena floor. */
    public java.util.List<org.bukkit.Location> soulRing(Temple t) {
        org.bukkit.Location c = arenaCenter(t);
        java.util.List<org.bukkit.Location> out = new java.util.ArrayList<>();
        for (int[] xz : TempleLayout.soulRing()) {
            out.add(new org.bukkit.Location(sift, c.getBlockX() + xz[0], c.getBlockY() - 1, c.getBlockZ() + xz[1]));
        }
        return out;
    }

    /** The Seelen-Altar of a sleeping temple whose spawners are exhausted was used (alpha.42b: the boss fight). */
    public interface AltarUse {
        void use(Player player, Temple temple);
    }

    private AltarUse altarUse;

    public void altarUse(AltarUse use) {
        this.altarUse = use;
    }

    /** For the boss (alpha.42) and tests: the boss of this temple is defeated. */
    public void defeat(String id) {
        Temple t = temples.get(id);
        if (t != null) {
            Temple defeated = t.withState(State.DEFEATED, System.currentTimeMillis());
            temples.put(id, defeated);
            if (!spawnerPlan.isEmpty()) {
                allSpawners(defeated, new BroodSpawner.State(BroodSpawner.Phase.OFF, 0, 0, 0));
            }
            save();
            onDefeat.accept(defeated);
        }
    }

    public Block enchanterBlock(Temple t) {
        TempleLayout.Pos p = markers(t).enchanter();
        return sift.getBlockAt(t.x() + p.x(), t.y() + p.y(), t.z() + p.z());
    }

    public Block altarBlock(Temple t) {
        TempleLayout.Pos p = markers(t).altar();
        return sift.getBlockAt(t.x() + p.x(), t.y() + p.y(), t.z() + p.z());
    }

    /** Why this Sculk Enchanter cannot be used yet (a temple enchanter before the victory), or null. */
    public String lockReason(Block block) {
        if (!enchanterRequiresDefeat) {
            return null;
        }
        Optional<Temple> t = at(block);
        if (t.isEmpty() || !t.get().at(markers(t.get()).enchanter(), block.getX(), block.getY(), block.getZ())
                || t.get().state() == State.DEFEATED) {
            return null;
        }
        return "Der Sculk Enchanter des Tempels erwacht erst, wenn sein Wächter besiegt ist.";
    }

    // ---- rebuild and inspection ------------------------------------------------------------------------------

    /**
     * Admin: builds a temple anew at its place with the current layout (alpha.41.1). First the old temple is taken
     * away (its materials inside its old and its new area: Sift masonry, ornament blocks, the vanilla stairs, slabs, walls,
     * bars, chains, lanterns and chests, chests emptied), then every chunk of the new layout is built, one chunk per
     * tick. The temple sleeps again afterwards. done gets a summary.
     */
    public boolean rebuild(String id, Consumer<String> done) {
        Temple old = temples.get(id);
        TempleSites.Site site = site(id);
        if (old == null || site == null || !rebuildSteps.isEmpty()) {
            return false;
        }
        long start = System.nanoTime();
        int[] removed = {0};
        Markers before = markers(old);
        Markers after = markers;
        int reach = Math.max(before.extent(), builder.reach());
        int minY = old.y() + Math.min(before.bottom(), after.bottom()) - 1;
        int maxY = old.y() + Math.max(before.top(), after.top()) + 1;
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (old.x() - reach) >> 4; cx <= (old.x() + reach) >> 4; cx++) {
            for (int cz = (old.z() - reach) >> 4; cz <= (old.z() + reach) >> 4; cz++) {
                chunks.add(new long[] {cx, cz});
            }
        }
        // 1. take the old temple away
        for (long[] c : chunks) {
            rebuildSteps.add(() -> removed[0] += clear(old, (int) c[0], (int) c[1], reach, minY, maxY));
        }
        // 2. build the new one at the same floor
        rebuildSteps.add(() -> {
            temples.put(id, new Temple(id, old.x(), old.y(), old.z(), State.SLEEPING, 0, CURRENT));
            spawnerStates.remove(id);
            builder.knownFloor(id, old.y());
            pending.removeIf(s -> s.siteId().equals(id));
            save();
        });
        for (long[] c : builder.chunks(site)) {
            rebuildSteps.add(() -> {
                sift.getChunkAt((int) c[0], (int) c[1]);
                TempleBuilder.Slice slice = new TempleBuilder.Slice(id, (int) c[0], (int) c[1]);
                builder.buildLate(new WorldCanvas(sift), site, old.y(), (int) c[0], (int) c[1], lootSeed(temples.get(id), slice));
            });
        }
        rebuildSteps.add(() -> {
            markEnchanterIfLoaded(temples.get(id));
            done.accept(String.format(java.util.Locale.ROOT, "%s neu gebaut: %d alte Blöcke entfernt, %d Chunks, %.0f ms insgesamt "
                    + "(verteilt auf %d Ticks)", id, removed[0], builder.chunks(site).size(), (System.nanoTime() - start) / 1e6,
                    chunks.size() + builder.chunks(site).size() + 2));
        });
        return true;
    }

    /** Removes the temple materials of a temple in one chunk (inside its box); returns how many. */
    private int clear(Temple t, int chunkX, int chunkZ, int reach, int minY, int maxY) {
        sift.getChunkAt(chunkX, chunkZ);
        int count = 0;
        BlockData air = Material.AIR.createBlockData();
        for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
            for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                if (Math.abs(x - t.x()) > reach || Math.abs(z - t.z()) > reach) {
                    continue;
                }
                for (int y = Math.max(minY, sift.getMinHeight()); y <= Math.min(maxY, sift.getMaxHeight() - 1); y++) {
                    Block b = sift.getBlockAt(x, y, z);
                    if (!templeMaterial(b, true)) {
                        continue;
                    }
                    if (b.getState() instanceof InventoryHolder holder) {
                        holder.getInventory().clear();
                    }
                    b.setBlockData(air, false);
                    count++;
                }
            }
        }
        return count;
    }

    /** Sift masonry and ornaments of the temple palette, or one of its vanilla materials. */
    private boolean templeMaterial(Block block, boolean forRemoval) {
        if (VANILLA.contains(block.getType())) {
            return true;
        }
        CustomBlock custom = blocks.at(block);
        if (custom == null || !TempleLayout.PALETTE.contains(custom.id())) {
            return false;
        }
        // sculk and ichor glass also grow naturally around a temple: taken away only by the rebuild of the area
        return !forRemoval || !custom.id().equals("healthy_sculk");
    }

    /**
     * Admin: how well a temple sits in the terrain. Floating: columns standing on the court with air or fluid right
     * below the court floor. Buried: foreign blocks (not of the plan) in the first three blocks above the court.
     */
    public List<String> inspect(String id) {
        Temple t = temples.get(id);
        TempleSites.Site site = site(id);
        List<String> out = new ArrayList<>();
        if (t == null || site == null) {
            return out;
        }
        out.add("Version " + t.version() + (t.version() < BASTION ? " (alpha.41a, /sift temple rebuild " + id + " baut die Bastion)"
                : t.version() < CURRENT ? " (ohne Brut-Spawner, /sift temple rebuild " + id + " setzt sie)" : "")
                + ", Boden y " + t.y() + ", wartende Teile " + pending(id));
        if (t.version() < BASTION) {
            return out;
        }
        TempleLayout.Layout plan = builder.layout(site);
        int r = builder.params().radius();
        int floating = 0;
        int columns = 0;
        int foreign = 0;
        int unloaded = 0;
        Map<String, Integer> kinds = new java.util.TreeMap<>();
        String example = null;
        Set<Long> standing = TempleLayout.standingColumns(plan);
        int ext = builder.params().extent();
        for (int dx = -ext; dx <= ext; dx++) {
            for (int dz = -ext; dz <= ext; dz++) {
                int x = t.x() + dx;
                int z = t.z() + dz;
                if (!sift.isChunkLoaded(x >> 4, z >> 4)) {
                    unloaded++;
                    continue;
                }
                if (standing.contains(TempleLayout.column(dx, dz))) {
                    columns++;
                    Material below = sift.getBlockAt(x, t.y() - 2, z).getType();
                    String planned = plan.blocks().get(new TempleLayout.Pos(dx, -2, dz));
                    if ((below.isAir() || below == Material.WATER || below == Material.LAVA) && planned == null) {
                        floating++;
                    }
                }
                if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                    for (int y = 0; y < 3; y++) {
                        String planned = plan.blocks().get(new TempleLayout.Pos(dx, y, dz));
                        Block b = sift.getBlockAt(x, t.y() + y, z);
                        if (planned == null && !b.getType().isAir() && !templeMaterial(b, false)) {
                            foreign++;
                            CustomBlock custom = blocks.at(b);
                            kinds.merge(custom != null ? custom.id() : b.getType().getKey().getKey(), 1, Integer::sum);
                            if (example == null) {
                                example = b.getX() + " " + b.getY() + " " + b.getZ() + " auf "
                                        + sift.getBlockAt(b.getX(), b.getY() - 1, b.getZ()).getBlockData().getAsString();
                            }
                        }
                    }
                }
            }
        }
        out.add("Säulen auf dem Hof: " + columns + ", davon schwebend " + floating + "; fremde Blöcke über dem Hof " + foreign
                + (unloaded > 0 ? " (" + unloaded + " Säulen nicht geladen)" : ""));
        if (!kinds.isEmpty()) {
            out.add("Fremd: " + kinds.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5)
                    .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", ")) + " (z. B. " + example + ")");
        }
        return out;
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
        Markers m = markers(t);
        boolean permanent = t.at(m.enchanter(), block.getX(), block.getY(), block.getZ())
                || t.at(m.altar(), block.getX(), block.getY(), block.getZ())
                || m.room(t, block);
        if (!permanent && t.state() == State.DEFEATED) {
            return false;
        }
        return templeMaterial(block, false);
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
        if (t.isPresent() && t.get().at(markers(t.get()).altar(), block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            int awake = altarGate && t.get().state() != State.DEFEATED ? spawnersAwake(t.get()) : 0;
            if (awake == 0 && t.get().state() != State.DEFEATED && altarUse != null && hasArena(t.get())) {
                altarUse.use(player, t.get());
                return;
            }
            player.sendActionBar(Component.text(t.get().state() == State.DEFEATED ? "Der Wächter dieses Tempels ist besiegt."
                    : awake > 0 ? "Der Seelen-Altar schweigt, solange " + awake + (awake == 1 ? " Brutkern wacht." : " Brutkerne wachen.")
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
            TempleLayout.Pos p = markers(t).enchanter();
            if ((t.x() + p.x()) >> 4 == event.getChunk().getX() && (t.z() + p.z()) >> 4 == event.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> markEnchanter.accept(enchanterBlock(t)));
            }
        }
    }

    /** The loaded world on the main thread (parts built late, rebuilds). */
    static final class WorldCanvas implements TempleBuilder.Canvas {

        private final World world;

        WorldCanvas(World world) {
            this.world = world;
        }

        @Override
        public int surfaceY(int x, int z) {
            return world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return world.getBlockAt(x, y, z).getType().isAir();
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return world.getBlockAt(x, y, z).getType().isSolid();
        }

        @Override
        public void set(int x, int y, int z, BlockData data) {
            world.getBlockAt(x, y, z).setBlockData(data, false);
        }

        @Override
        public void loot(int x, int y, int z, LootTable table, long seed) {
            var state = world.getBlockAt(x, y, z).getState();
            if (state instanceof Lootable lootable) {
                lootable.setLootTable(table, seed);
                state.update(true, false);
            }
        }

        @Override
        public int minY() {
            return world.getMinHeight();
        }

        @Override
        public int maxY() {
            return world.getMaxHeight();
        }
    }
}
