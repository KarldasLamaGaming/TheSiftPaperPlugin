package de.lamamc.sift.world.siftite;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import de.lamamc.sift.Messages;
import de.lamamc.sift.core.siftite.SiftiteRecovery;
import de.lamamc.sift.item.SiftAdvancements;
import de.lamamc.sift.item.SiftItems;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Siftite kept on death (alpha.23, config siftite.keep-on-death.*), as in the original 1.1.1: on a real
 * death without keepInventory the Siftite items are saved in the player's data and only then taken from the
 * drops. Two seconds after respawn (or login) a mini rift opens next to the player and the items fly back
 * one by one. Each item leaves the saved list only in the same moment it lands in the inventory, so a
 * logout, a restart or a full inventory never loses or doubles anything. /sift siftite restore is the
 * emergency exit.
 */
public final class SiftiteKeeper implements Listener {

    public static final NamespacedKey PENDING = new NamespacedKey("the_sift", "siftite_pending");
    static final NamespacedKey DISPLAY = new NamespacedKey("the_sift", "siftite_return");
    private static final Vector3f TINY = new Vector3f(0.01f, 0.01f, 0.01f);

    private final Plugin plugin;
    private final Messages messages;
    private final SiftAdvancements advancements;
    private final Logger logger;
    private final Set<String> ids;
    private final long delay;
    private final int emitInterval;
    private final int openTicks;
    private final boolean particles;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> startAt = new HashMap<>();
    /** Saved during this death event: the list before, to undo if the death does not drop after all. */
    private final Map<UUID, List<byte[]>> secured = new HashMap<>();
    private long tick;

    /** A delivery in progress: the mini rift and one flying display per pending entry already let out. */
    private static final class Session {
        final UUID player;
        final World world;
        ItemDisplay rift;
        final List<ItemDisplay> flying = new ArrayList<>();
        final List<double[]> motion = new ArrayList<>();
        long started;
        long lastEmit;
        long closingSince = -1;
        long lastFullNotice = -1000;
        boolean awarded;

        Session(UUID player, World world) {
            this.player = player;
            this.world = world;
        }
    }

    public SiftiteKeeper(Plugin plugin, Messages messages, SiftAdvancements advancements, ConfigurationSection config) {
        this.plugin = plugin;
        this.messages = messages;
        this.advancements = advancements;
        this.logger = plugin.getLogger();
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        delay = Math.max(1, c.getLong("delay-ticks", 40));
        emitInterval = Math.max(1, c.getInt("emit-interval-ticks", 5));
        openTicks = Math.max(1, c.getInt("open-ticks", 12));
        particles = c.getBoolean("particles", true);
        ids = loadTag(plugin);
    }

    private static Set<String> loadTag(Plugin plugin) {
        try (var in = plugin.getResource("item-data/tags/item/siftite_items.json")) {
            JsonArray values = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values");
            List<String> list = new ArrayList<>();
            values.forEach(v -> list.add(v.getAsString()));
            return SiftiteRecovery.idsFromTag(list);
        } catch (Exception e) {
            throw new IllegalStateException("siftite_items tag missing in the plugin jar", e);
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedule(player);
        }
    }

    public Set<String> ids() {
        return ids;
    }

    // ---- saved list -----------------------------------------------------------------------------------------

    public static List<byte[]> pending(PersistentDataContainer pdc) {
        List<byte[]> list = pdc.get(PENDING, PersistentDataType.LIST.byteArrays());
        return list == null ? List.of() : list;
    }

    public static List<byte[]> pending(io.papermc.paper.persistence.PersistentDataContainerView pdc) {
        List<byte[]> list = pdc.get(PENDING, PersistentDataType.LIST.byteArrays());
        return list == null ? List.of() : list;
    }

    private static void save(Player player, List<byte[]> list) {
        if (list.isEmpty()) {
            player.getPersistentDataContainer().remove(PENDING);
        } else {
            player.getPersistentDataContainer().set(PENDING, PersistentDataType.LIST.byteArrays(), list);
        }
    }

    // ---- death ----------------------------------------------------------------------------------------------

    /** Early, so grave or backpack plugins do not take the Siftite first. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> taken = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (ids.contains(SiftItems.idOf(drop))) {
                taken.add(drop);
            }
        }
        // the event's flag, i.e. keep_inventory of the world the player died in (per world in 26.3)
        if (!SiftiteRecovery.secure(event.getKeepInventory(), taken.size())) {
            if (event.getKeepInventory() && hasSiftite(player)) {
                logger.info("Siftite of " + player.getName() + " not saved: keep_inventory is on in " + player.getWorld().getName()
                        + " (the inventory stays as it is)");
            }
            return;
        }
        stop(player.getUniqueId()); // a delivery still running: its items are still in the list
        List<byte[]> old = pending(player.getPersistentDataContainer());
        List<byte[]> add = taken.stream().map(ItemStack::serializeAsBytes).toList();
        List<byte[]> next = SiftiteRecovery.append(old, add);
        try {
            save(player, next);
        } catch (RuntimeException e) {
            logger.warning("Siftite of " + player.getName() + " could not be saved, it drops normally: " + e);
            return;
        }
        // only take the items from the drops once the saved list reads back complete
        List<byte[]> check = pending(player.getPersistentDataContainer());
        if (check.size() != next.size() || !java.util.Arrays.equals(check.getLast(), next.getLast())) {
            logger.warning("Siftite of " + player.getName() + " did not save correctly, it drops normally");
            save(player, old);
            return;
        }
        event.getDrops().removeAll(taken);
        secured.put(player.getUniqueId(), old);
    }

    /**
     * After all plugins: if one cancelled the death or switched keepInventory on after us, the player keeps
     * the items in the inventory, so the saved copies are removed again (no duplicates).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathDone(PlayerDeathEvent event) {
        List<byte[]> old = secured.remove(event.getPlayer().getUniqueId());
        if (old != null && SiftiteRecovery.revert(event.getKeepInventory(), event.isCancelled())) {
            save(event.getPlayer(), old);
            logger.info("Siftite of " + event.getPlayer().getName() + " not kept on the side: the death was cancelled "
                    + "or keepInventory was switched on by another plugin");
        }
    }

    private boolean hasSiftite(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (ids.contains(SiftItems.idOf(stack))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedule(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId()); // the list stays; it starts again on the next login
    }

    private void schedule(Player player) {
        if (!pending(player.getPersistentDataContainer()).isEmpty()) {
            startAt.put(player.getUniqueId(), tick + delay);
        }
    }

    // ---- delivery -------------------------------------------------------------------------------------------

    private void tick() {
        tick++;
        for (Iterator<Map.Entry<UUID, Long>> it = startAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (tick >= e.getValue() && !player.isDead() && !sessions.containsKey(player.getUniqueId())) {
                it.remove();
                begin(player);
            }
        }
        for (Iterator<Session> it = sessions.values().iterator(); it.hasNext(); ) {
            Session session = it.next();
            if (!update(session)) {
                clear(session);
                it.remove();
                // anything still saved starts again (unless the player is gone or waits for a respawn)
                Player player = Bukkit.getPlayer(session.player);
                if (player != null && !pending(player.getPersistentDataContainer()).isEmpty()) {
                    startAt.putIfAbsent(session.player, tick + delay);
                }
            }
        }
    }

    private void begin(Player player) {
        if (pending(player.getPersistentDataContainer()).isEmpty()) {
            return;
        }
        Session session = new Session(player.getUniqueId(), player.getWorld());
        Location at = riftSpot(player);
        ItemStack cube = ItemStack.of(Material.PAPER);
        cube.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL, new NamespacedKey("the_sift", "mini_rift"));
        session.rift = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(cube);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), TINY, new AxisAngle4f()));
            d.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BOOLEAN, true);
        });
        ItemDisplay rift = session.rift;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (rift.isValid()) {
                rift.setInterpolationDelay(0);
                rift.setInterpolationDuration(openTicks);
                rift.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            }
        }, 2L);
        at.getWorld().playSound(at, "the_sift:entity.rift.appear", SoundCategory.PLAYERS, 0.6f, 1.6f);
        session.started = tick;
        session.lastEmit = tick;
        sessions.put(player.getUniqueId(), session);
    }

    /** Next to the player (1.55 blocks), where the block and the one above are free. */
    private static Location riftSpot(Player player) {
        Location base = player.getLocation();
        for (int i = 0; i < 9; i++) {
            double angle = i == 0 ? 0 : Math.PI / 4 * (i - 1);
            Location at = base.clone().add(Math.cos(angle) * 1.55, 0.75, Math.sin(angle) * 1.55);
            if (at.getBlock().isPassable() && at.getBlock().getRelative(0, 1, 0).isPassable()) {
                return at;
            }
        }
        return base.clone().add(0, 1.5, 0);
    }

    /** One tick of a delivery; false when it is over (done, or the player left, died or changed world). */
    private boolean update(Session session) {
        Player player = Bukkit.getPlayer(session.player);
        if (player == null || player.isDead()) {
            if (player != null) {
                startAt.put(player.getUniqueId(), Long.MAX_VALUE); // after the respawn event
            }
            return false;
        }
        if (!player.getWorld().equals(session.world)) {
            startAt.put(player.getUniqueId(), tick + delay);
            return false;
        }
        List<byte[]> pending = pending(player.getPersistentDataContainer());
        if (session.closingSince >= 0) {
            return tick - session.closingSince <= openTicks;
        }
        if (pending.isEmpty()) {
            closeRift(session);
            return true;
        }
        // let one more item out every few ticks once the rift is open
        if (tick - session.started >= openTicks && session.flying.size() < pending.size() && tick - session.lastEmit >= emitInterval) {
            emit(session, pending.get(session.flying.size()));
            session.lastEmit = tick;
        }
        double height = player.getHeight() * 0.62;
        Location p = player.getLocation();
        double[] target = {p.getX(), p.getY() + height, p.getZ()};
        for (int i = 0; i < session.flying.size(); i++) {
            ItemDisplay display = session.flying.get(i);
            if (!display.isValid()) {
                return false; // removed by someone: start again later from the saved list
            }
            double[] m = session.motion.get(i);
            boolean head = i == 0;
            boolean room = head && room(player, pending.getFirst());
            double[] goal = head && room ? target
                    : SiftiteRecovery.orbit(p.getX(), p.getY() + height, p.getZ(), tick, i, session.flying.size());
            double[] next = SiftiteRecovery.step(new double[] {m[0], m[1], m[2]}, new double[] {m[3], m[4], m[5]}, goal,
                    SiftiteRecovery.KEEP, SiftiteRecovery.PULL, head && room ? SiftiteRecovery.MAX_SPEED : SiftiteRecovery.ORBIT_SPEED);
            System.arraycopy(next, 0, m, 0, 6);
            display.teleport(new Location(session.world, m[0], m[1], m[2]));
            if (particles && tick % 2 == 0) {
                session.world.spawnParticle(Particle.END_ROD, m[0], m[1], m[2], 1, 0, 0, 0, 0);
            }
            if (head && !room && tick - session.lastFullNotice > 100) {
                session.lastFullNotice = tick;
                player.sendActionBar(messages.get("siftite.inventory-full"));
            }
            if (head && room && SiftiteRecovery.reached(new double[] {m[0], m[1], m[2]}, target)) {
                deliver(player, session, pending);
                return true; // one per tick; the list changed
            }
        }
        return true;
    }

    private void emit(Session session, byte[] entry) {
        ItemStack stack = ItemStack.deserializeBytes(entry);
        Location from = session.rift != null && session.rift.isValid() ? session.rift.getLocation() : null;
        if (from == null) {
            return;
        }
        ItemDisplay display = session.world.spawn(from, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(stack);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(3);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(DISPLAY, PersistentDataType.BOOLEAN, true);
        });
        session.flying.add(display);
        session.motion.add(new double[] {from.getX(), from.getY(), from.getZ(), 0, 0, 0});
    }

    /** Whether the first pending item fits at least partly into the main inventory (no armor slots). */
    private static boolean room(Player player, byte[] entry) {
        ItemStack stack = ItemStack.deserializeBytes(entry);
        ItemStack[] main = player.getInventory().getStorageContents();
        for (ItemStack slot : main) {
            if (slot == null || slot.isEmpty() || slot.isSimilar(stack) && slot.getAmount() < slot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** The head item goes into the inventory and leaves the saved list in the same step. */
    private void deliver(Player player, Session session, List<byte[]> pending) {
        ItemStack stack = ItemStack.deserializeBytes(pending.getFirst());
        int before = stack.getAmount();
        Map<Integer, ItemStack> rest = player.getInventory().addItem(stack);
        ItemStack leftover = rest.isEmpty() ? null : rest.values().iterator().next();
        List<byte[]> next = SiftiteRecovery.afterDelivery(pending, leftover == null ? null : leftover.serializeAsBytes());
        save(player, next);
        if (leftover == null || leftover.getAmount() < before) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.4f);
            if (!session.awarded) {
                session.awarded = true;
                advancements.award(player, "the_sift/keep_inventory");
            }
        }
        if (leftover == null) {
            session.flying.removeFirst().remove();
            session.motion.removeFirst();
        }
    }

    private void closeRift(Session session) {
        session.closingSince = tick;
        if (session.rift != null && session.rift.isValid()) {
            session.rift.setInterpolationDelay(0);
            session.rift.setInterpolationDuration(openTicks);
            session.rift.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), TINY, new AxisAngle4f()));
        }
    }

    private void clear(Session session) {
        if (session.rift != null) {
            session.rift.remove();
        }
        session.flying.forEach(Entity::remove);
    }

    /** Ends a delivery (logout, death, restore, disable); the saved list is untouched. */
    public void stop(UUID player) {
        Session session = sessions.remove(player);
        if (session != null) {
            clear(session);
        }
    }

    public void stopAll() {
        sessions.values().forEach(this::clear);
        sessions.clear();
    }

    // ---- admin ----------------------------------------------------------------------------------------------

    /**
     * Emergency exit: everything still saved goes straight into the inventory (the rest to the feet,
     * owned by the player) and the list is cleared in the same step. Returns how many stacks.
     */
    public int restore(Player player) {
        stop(player.getUniqueId());
        startAt.remove(player.getUniqueId());
        List<byte[]> pending = pending(player.getPersistentDataContainer());
        List<ItemStack> stacks = pending.stream().map(ItemStack::deserializeBytes).toList();
        save(player, List.of());
        for (ItemStack stack : stacks) {
            player.getInventory().addItem(stack).values().forEach(rest ->
                    player.getWorld().dropItem(player.getLocation(), rest, item -> item.setOwner(player.getUniqueId())));
        }
        return stacks.size();
    }
}
