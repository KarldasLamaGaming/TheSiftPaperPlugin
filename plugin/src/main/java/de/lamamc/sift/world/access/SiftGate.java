package de.lamamc.sift.world.access;

import de.lamamc.sift.Messages;
import de.lamamc.sift.core.access.SiftAccess;
import de.lamamc.sift.core.portal.ReturnPoint;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.portal.PortalService;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Access to the Sift over time (alpha.20, config access.*): the schedule ({@link SiftAccess}) plus a state set by
 * command, kept with the first dragon kill in the main world's persistent data. While the Sift is closed nobody
 * gets in (portals, teleports, respawn, login inside) except players with thesift.access.bypass; leaving always
 * works. When it closes, players inside are sent back after a countdown; when it opens, the server is told.
 */
public final class SiftGate implements Listener {

    public static final String BYPASS = "thesift.access.bypass";
    /** Stored as the world of a dragon kill set by command. */
    public static final String BY_COMMAND = "command";
    private static final NamespacedKey DRAGON_KILL = new NamespacedKey("the_sift", "dragon_first_kill");
    private static final NamespacedKey DRAGON_WORLD = new NamespacedKey("the_sift", "dragon_first_kill_world");
    private static final NamespacedKey OVERRIDE = new NamespacedKey("the_sift", "access_override");

    private final Plugin plugin;
    private final Messages messages;
    private final Logger logger;
    private final ZoneId zone = ZoneId.systemDefault();
    private SiftAccess.Settings settings;
    private List<String> dragonWorlds = List.of();
    private int warningSeconds = 30;
    private Boolean lastOpen;
    private int countdown = -1;
    private boolean warnedUnknownKill;
    private final Map<UUID, Long> lastRefusal = new HashMap<>();

    public SiftGate(Plugin plugin, Messages messages, ConfigurationSection config) {
        this.plugin = plugin;
        this.messages = messages;
        this.logger = plugin.getLogger();
        apply(config);
    }

    /** Takes over the access section (start and /sift access reload). */
    public void apply(ConfigurationSection config) {
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        String dateText = c.getString("date", "");
        Long date = SiftAccess.parseDate(dateText, zone);
        if (!dateText.isBlank() && date == null) {
            logger.warning("access.date '" + dateText + "' is not an ISO date (e.g. 2026-12-01T18:00); fixed-date stays closed");
        }
        settings = new SiftAccess.Settings(SiftAccess.Mode.parse(c.getString("mode", "always")),
                (long) (Math.max(0, c.getDouble("delay-days", 7)) * 86_400_000L), date);
        dragonWorlds = List.copyOf(c.getStringList("dragon-worlds"));
        warningSeconds = Math.max(0, c.getInt("close-warning-seconds", 30));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private PersistentDataContainer store() {
        return Bukkit.getWorlds().getFirst().getPersistentDataContainer();
    }

    public Long dragonKill() {
        return store().get(DRAGON_KILL, PersistentDataType.LONG);
    }

    public String dragonWorld() {
        return store().get(DRAGON_WORLD, PersistentDataType.STRING);
    }

    public void setDragonKill(Long when, String world) {
        if (when == null) {
            store().remove(DRAGON_KILL);
            store().remove(DRAGON_WORLD);
        } else {
            store().set(DRAGON_KILL, PersistentDataType.LONG, when);
            store().set(DRAGON_WORLD, PersistentDataType.STRING, world);
        }
        // the 1 s timer picks up the change (announcement or countdown)
    }

    public SiftAccess.Override override() {
        String value = store().get(OVERRIDE, PersistentDataType.STRING);
        return "open".equals(value) ? SiftAccess.Override.OPEN : "closed".equals(value) ? SiftAccess.Override.CLOSED
                : SiftAccess.Override.NONE;
    }

    /** open / close / reset by command; kept across restarts. */
    public void setOverride(SiftAccess.Override override) {
        if (override == SiftAccess.Override.NONE) {
            store().remove(OVERRIDE);
        } else {
            store().set(OVERRIDE, PersistentDataType.STRING, override == SiftAccess.Override.OPEN ? "open" : "closed");
        }
        // the 1 s timer picks up the change (announcement or countdown)
    }

    public SiftAccess.State state() {
        return SiftAccess.evaluate(settings, System.currentTimeMillis(), dragonKill(), override());
    }

    public SiftAccess.Settings settings() {
        return settings;
    }

    public ZoneId zone() {
        return zone;
    }

    public int countdown() {
        return countdown;
    }

    public List<String> dragonWorlds() {
        return dragonWorlds;
    }

    /** Whether this entity may go into the Sift now. */
    public boolean mayEnter(Entity entity) {
        return state().open() || entity instanceof Player player && player.hasPermission(BYPASS);
    }

    /** The message for a player who is turned away (with the time left when known). */
    public void refuse(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastRefusal.get(player.getUniqueId());
        if (last != null && now - last < 3000) {
            return;
        }
        lastRefusal.put(player.getUniqueId(), now);
        SiftAccess.State state = state();
        player.sendActionBar(state.opensAt() != null
                ? messages.get("access.closed-until", "time", SiftAccess.remaining(state.opensAt() - now))
                : messages.get("access.closed"));
    }

    private void tick() {
        SiftAccess.State state = state();
        if (!warnedUnknownKill && settings.mode() == SiftAccess.Mode.AFTER_DRAGON && dragonKill() == null && dragonKilledBefore()) {
            warnedUnknownKill = true;
            logger.warning("access.mode after-dragon: the dragon was killed before the plugin ran, the time is unknown. "
                    + "The Sift stays closed until /sift access set dragon-kill <date> is given.");
        }
        if (lastOpen == null) {
            lastOpen = state.open();
            if (!state.open()) {
                sendBackAll(); // closed at start: whoever is inside goes back
            }
            return;
        }
        if (state.open() && !lastOpen) {
            countdown = -1;
            Bukkit.broadcast(messages.get("access.opened"));
            logger.info("The Sift is open (" + state.reason() + ")");
        } else if (!state.open() && lastOpen) {
            countdown = warningSeconds;
            logger.info("The Sift closes in " + warningSeconds + " s (" + state.reason() + ")");
            for (Player player : insiders()) {
                player.sendMessage(messages.get("access.closing", "seconds", String.valueOf(warningSeconds)));
            }
        }
        lastOpen = state.open();
        if (!state.open() && countdown >= 0) {
            if (countdown == 0) {
                sendBackAll();
                countdown = -1;
            } else {
                for (Player player : insiders()) {
                    player.sendActionBar(messages.get("access.countdown", "seconds", String.valueOf(countdown)));
                }
                countdown--;
            }
        }
    }

    /** A dragon was killed in an End world (vanilla's record) - possibly before the plugin ran. */
    public boolean dragonKilledBefore() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.THE_END && world.getEnderDragonBattle() != null
                    && world.getEnderDragonBattle().hasBeenPreviouslyKilled()) {
                return true;
            }
        }
        return false;
    }

    private List<Player> insiders() {
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        return sift == null ? List.of() : sift.getPlayers().stream().filter(p -> !p.hasPermission(BYPASS)).toList();
    }

    private void sendBackAll() {
        for (Player player : insiders()) {
            sendBack(player);
        }
    }

    /** To the return point of the last portal trip, else to the main world's spawn. */
    public void sendBack(Player player) {
        ReturnPoint point = ReturnPoint.parse(player.getPersistentDataContainer().get(PortalService.RETURN_POINT, PersistentDataType.STRING));
        NamespacedKey key = point == null ? null : NamespacedKey.fromString(point.world());
        World world = key == null ? null : Bukkit.getWorld(key);
        Location target = world == null || SiftWorlds.isSift(world) ? Bukkit.getWorlds().getFirst().getSpawnLocation()
                : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                player.getPersistentDataContainer().remove(PortalService.RETURN_POINT);
                player.sendMessage(messages.get("access.sent-back"));
            }
        });
    }

    // ---- events ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || dragonKill() != null) {
            return;
        }
        World world = event.getEntity().getWorld();
        if (SiftAccess.countsDragon(world.getName(), world.getEnvironment() == World.Environment.THE_END, dragonWorlds)) {
            setDragonKill(System.currentTimeMillis(), world.getName());
            logger.info("First dragon kill recorded in " + world.getName());
        }
    }

    /** Any teleport into the Sift (other plugins, /tp, /sift tp) while it is closed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || !SiftWorlds.isSift(event.getTo().getWorld()) || SiftWorlds.isSift(event.getFrom().getWorld())) {
            return;
        }
        if (!mayEnter(event.getPlayer())) {
            event.setCancelled(true);
            refuse(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (SiftWorlds.isSift(event.getRespawnLocation().getWorld()) && !mayEnter(event.getPlayer())) {
            event.setRespawnLocation(Bukkit.getWorlds().getFirst().getSpawnLocation());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (SiftWorlds.isSift(player.getWorld()) && !mayEnter(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && SiftWorlds.isSift(player.getWorld())) {
                    sendBack(player);
                }
            });
        }
    }
}
