package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalFrame;
import de.lamamc.sift.core.portal.PortalState;
import de.lamamc.sift.core.portal.ReturnPoint;
import de.lamamc.sift.core.portal.TransitGuard;
import de.lamamc.sift.core.worldgen.MainPortalPlan;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.SiftWorlds;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Travelling through portals (SiftTeleportManager of the original): an entity that touches an open
 * entrance goes to the main portal of the Sift and remembers where it came from; one that touches the
 * Sift's main portal goes back there (or to the main world's spawn). After a trip it must step out of the
 * surface before it can travel again.
 */
public final class PortalService implements Listener {

    public static final String SIFT_EXIT_ID = "sift-main";
    public static final NamespacedKey RETURN_POINT = new NamespacedKey("the_sift", "portal_return");
    /** Set while an entity stands in the portal it arrived through; survives logouts. */
    public static final NamespacedKey EXIT_REQUIRED = new NamespacedKey("the_sift", "portal_exit_required");

    private final Plugin plugin;
    private final PortalRegistry registry;
    private final PortalSurface surface;
    private final Logger logger;
    private final TransitGuard guard = new TransitGuard();
    private final Set<UUID> travelling = new HashSet<>();
    private final Random random = new Random();
    private int ticks;

    public PortalService(Plugin plugin, PortalRegistry registry, PortalSurface surface) {
        this.plugin = plugin;
        this.registry = registry;
        this.surface = surface;
        this.logger = plugin.getLogger();
    }

    public PortalRegistry registry() {
        return registry;
    }

    public PortalSurface surface() {
        return surface;
    }

    public void start(int checkTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, Math.max(1, checkTicks));
    }

    /** Registers the opening of the Sift's main portal as an open exit once the portal is built. */
    public void ensureSiftExit(World sift) {
        int[] origin = sift.getPersistentDataContainer().get(MainPortalBuilder.ORIGIN, PersistentDataType.INTEGER_ARRAY);
        if (origin == null || origin.length != 3 || registry.get(sift, SIFT_EXIT_ID).isPresent()) {
            return;
        }
        PortalFrame frame = new PortalFrame(PortalFrame.Axis.Z, origin[0] + MainPortalPlan.OPENING_FRAME_X,
                origin[1] + MainPortalPlan.OPENING_FRAME_Y, origin[2] + MainPortalPlan.OPENING_FRAME_Z,
                MainPortalPlan.OPENING_FRAME_WIDTH, MainPortalPlan.OPENING_FRAME_HEIGHT);
        SiftPortal exit = new SiftPortal(SIFT_EXIT_ID, SiftPortal.Kind.SIFT_EXIT, frame, PortalState.OPEN, List.of());
        registry.put(sift, exit);
        surface.open(sift, exit, false);
        logger.info("Sift main portal opening registered (" + frame.serialize() + ")");
    }

    /** Opens a portal (tune or admin). */
    public void open(World world, SiftPortal portal) {
        SiftPortal open = portal.withState(PortalState.OPEN);
        registry.put(world, open);
        PortalSurface.play(world, portal.frame(), PortalSurface.TRIGGER_SOUND, 1.0f);
        surface.open(world, open, true);
    }

    /** Closes a portal (admin); consoles stay awake. */
    public void close(World world, SiftPortal portal) {
        registry.put(world, portal.withState(portal.state().deactivate()));
        surface.close(world, portal);
    }

    private void tick() {
        ticks++;
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift != null && ticks % 50 == 0) {
            ensureSiftExit(sift);
        }
        Set<UUID> touching = new HashSet<>();
        for (World world : Bukkit.getWorlds()) {
            for (SiftPortal portal : registry.all(world)) {
                if (portal.state() != PortalState.OPEN) {
                    continue;
                }
                PortalFrame frame = portal.frame();
                if (!world.isChunkLoaded(frame.minX() >> 4, frame.minZ() >> 4)) {
                    continue;
                }
                BoundingBox box = surfaceBox(frame);
                for (Entity entity : world.getNearbyEntities(box)) {
                    if (entity instanceof Display || !entity.isValid() || !entity.getBoundingBox().overlaps(box)) {
                        continue;
                    }
                    touching.add(entity.getUniqueId());
                    if (entity instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        guard.arrived(entity.getUniqueId());
                        continue;
                    }
                    if (!guard.waiting(entity.getUniqueId()) && Boolean.TRUE.equals(
                            entity.getPersistentDataContainer().get(EXIT_REQUIRED, PersistentDataType.BOOLEAN))) {
                        guard.arrived(entity.getUniqueId()); // arrived before a restart or chunk unload
                    }
                    if (guard.shouldTransfer(entity.getUniqueId(), true)) {
                        transfer(entity, portal);
                    }
                }
                if (ticks % 5 == 0 && !world.getNearbyPlayers(box.getCenter().toLocation(world), 32).isEmpty()) {
                    PortalSurface.ambient(world, portal, random);
                }
            }
        }
        touching.addAll(travelling);
        releaseStepOuts(touching);
    }

    private void releaseStepOuts(Set<UUID> touching) {
        for (UUID id : guard.releaseAllExcept(touching)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.getPersistentDataContainer().remove(EXIT_REQUIRED);
            }
        }
    }

    /** The block layer of the opening (slightly shrunk, like the original's deflate). */
    static BoundingBox surfaceBox(PortalFrame frame) {
        boolean alongX = frame.axis() == PortalFrame.Axis.X;
        double minX = frame.minX() + (alongX ? 1 : 0);
        double minZ = frame.minZ() + (alongX ? 0 : 1);
        double maxX = alongX ? frame.minX() + frame.width() - 1 : frame.minX() + 1;
        double maxZ = alongX ? frame.minZ() + 1 : frame.minZ() + frame.width() - 1;
        return new BoundingBox(minX, frame.minY() + 1, minZ, maxX, frame.minY() + frame.height() - 1, maxZ).expand(-1.0E-7);
    }

    private void transfer(Entity entity, SiftPortal portal) {
        if (travelling.contains(entity.getUniqueId()) || entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) {
            return; // riders and their mounts are not carried along (yet)
        }
        Location target;
        if (portal.kind() == SiftPortal.Kind.ENTRANCE) {
            World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
            Location anchor = sift == null ? null : MainPortalBuilder.anchor(sift);
            if (anchor == null) {
                if (entity instanceof Player player) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text("Das Haupt-Portal der Sift ist noch nicht gebaut."));
                }
                guard.arrived(entity.getUniqueId());
                return;
            }
            Location here = entity.getLocation();
            entity.getPersistentDataContainer().set(RETURN_POINT, PersistentDataType.STRING, new ReturnPoint(
                    here.getWorld().getKey().toString(), here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch()).serialize());
            target = anchor.clone();
            target.setYaw(here.getYaw());
            target.setPitch(here.getPitch());
        } else {
            ReturnPoint point = ReturnPoint.parse(entity.getPersistentDataContainer().get(RETURN_POINT, PersistentDataType.STRING));
            NamespacedKey key = point == null ? null : NamespacedKey.fromString(point.world());
            World world = key == null ? null : Bukkit.getWorld(key);
            target = world == null
                    ? Bukkit.getWorlds().getFirst().getSpawnLocation()
                    : new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        }
        UUID id = entity.getUniqueId();
        travelling.add(id);
        guard.arrived(id);
        entity.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((ok, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    travelling.remove(id);
                    if (Boolean.TRUE.equals(ok)) {
                        if (portal.kind() == SiftPortal.Kind.SIFT_EXIT) {
                            entity.getPersistentDataContainer().remove(RETURN_POINT);
                        }
                        entity.getPersistentDataContainer().set(EXIT_REQUIRED, PersistentDataType.BOOLEAN, true);
                        if (entity instanceof Player player) {
                            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.0f);
                        }
                    } else {
                        guard.forget(id);
                        if (error != null) {
                            logger.warning("Portal trip of " + entity.getName() + " failed: " + error);
                        }
                    }
                }));
    }

    /** Whoever logs in standing in the portal they arrived through stays put until they step out. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (Boolean.TRUE.equals(player.getPersistentDataContainer().get(EXIT_REQUIRED, PersistentDataType.BOOLEAN))) {
            guard.arrived(player.getUniqueId());
        }
    }
}
