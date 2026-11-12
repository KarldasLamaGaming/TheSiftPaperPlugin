package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.access.SiftAccess;
import de.lamamc.sift.core.portal.PortalSchedule;
import de.lamamc.sift.core.portal.PortalSchedule.Group;
import de.lamamc.sift.world.access.SiftGate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The portal unlock schedule at runtime (alpha.42a.2, portals.unlock-schedule): day 0 (when the Sift opened), the
 * admins' overrides per group, and the checks for keys and travel. Everything is kept in the main world's data, so a
 * reset of the Sift does not touch it.
 */
public final class PortalUnlocks {

    private static final NamespacedKey FIRST_OPEN = new NamespacedKey("the_sift", "sift_first_open");
    private static final NamespacedKey SET_OPEN = new NamespacedKey("the_sift", "sift_opened_set");
    private static final NamespacedKey OVERRIDES = new NamespacedKey("the_sift", "portal_unlock_overrides");

    private final Plugin plugin;
    private final SiftGate gate;
    private final PortalSchedule.Settings settings;
    private final Map<UUID, Long> lastRefusal = new HashMap<>();

    public PortalUnlocks(Plugin plugin, SiftGate gate, PortalSchedule.Settings settings) {
        this.plugin = plugin;
        this.gate = gate;
        this.settings = settings;
    }

    public PortalSchedule.Settings settings() {
        return settings;
    }

    /** Records day 0 the first time the Sift is seen open (modes always and manual, /sift open). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (gate != null && gate.state().open() && data().get(FIRST_OPEN, PersistentDataType.LONG) == null) {
                data().set(FIRST_OPEN, PersistentDataType.LONG, System.currentTimeMillis());
                plugin.getLogger().info("Portal unlock schedule: the Sift is open, day 0 is now");
            }
        }, 40L, 20L);
    }

    private static PersistentDataContainer data() {
        return Bukkit.getWorlds().getFirst().getPersistentDataContainer();
    }

    /** Day 0 of the schedule, or null while the Sift has not opened yet. */
    public Long openedAt() {
        SiftAccess.Settings access = gate == null ? new SiftAccess.Settings(SiftAccess.Mode.ALWAYS, 0, null) : gate.settings();
        return PortalSchedule.openedAt(access, gate == null ? null : gate.dragonKill(), data().get(FIRST_OPEN, PersistentDataType.LONG),
                data().get(SET_OPEN, PersistentDataType.LONG));
    }

    /** Admin: day 0 set by hand (null: back to the access mode / first opening). */
    public void setOpenedAt(Long at) {
        if (at == null) {
            data().remove(SET_OPEN);
        } else {
            data().set(SET_OPEN, PersistentDataType.LONG, at);
        }
    }

    public Map<Group, PortalSchedule.Override> overrides() {
        Map<Group, PortalSchedule.Override> out = new EnumMap<>(Group.class);
        String text = data().get(OVERRIDES, PersistentDataType.STRING);
        if (text != null) {
            for (String part : text.split(",")) {
                String[] kv = part.split("=");
                Group g = kv.length == 2 ? Group.of(kv[0]) : null;
                if (g != null) {
                    try {
                        out.put(g, PortalSchedule.Override.valueOf(kv[1]));
                    } catch (IllegalArgumentException ignored) {
                        // skip a broken entry
                    }
                }
            }
        }
        return out;
    }

    public void override(Group group, PortalSchedule.Override override) {
        Map<Group, PortalSchedule.Override> all = overrides();
        if (override == PortalSchedule.Override.NONE) {
            all.remove(group);
        } else {
            all.put(group, override);
        }
        StringBuilder text = new StringBuilder();
        all.forEach((g, o) -> text.append(text.isEmpty() ? "" : ",").append(g.key()).append('=').append(o));
        if (text.isEmpty()) {
            data().remove(OVERRIDES);
        } else {
            data().set(OVERRIDES, PersistentDataType.STRING, text.toString());
        }
    }

    public PortalSchedule.Status status(Group group) {
        return PortalSchedule.status(settings, group, System.currentTimeMillis(), openedAt(),
                overrides().getOrDefault(group, PortalSchedule.Override.NONE));
    }

    public static Group group(SiftPortal portal, World world) {
        return Group.of(portal.kind() == SiftPortal.Kind.ANCIENT_CITY, portal.kind() == SiftPortal.Kind.SIFT_EXIT,
                world.getEnvironment().name());
    }

    /** Whether this entity may open or pass this portal now (players with thesift.access.bypass always). */
    public boolean allows(Entity entity, SiftPortal portal, World world) {
        if (entity instanceof Player player && player.hasPermission(SiftGate.BYPASS)) {
            return true;
        }
        return status(group(portal, world)).unlocked();
    }

    /** The message for a sealed portal (throttled, at most every 3 s). */
    public void refuse(Player player, SiftPortal portal, World world) {
        long now = System.currentTimeMillis();
        Long last = lastRefusal.get(player.getUniqueId());
        if (last != null && now - last < 3000) {
            return;
        }
        lastRefusal.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(sealedText(status(group(portal, world))), NamedTextColor.DARK_PURPLE));
    }

    public String sealedText(PortalSchedule.Status status) {
        if (status.at() != null) {
            return "Dieses Portal ist noch versiegelt. Es öffnet sich am " + SiftAccess.format(status.at(), zone()) + " (in "
                    + SiftAccess.remaining(status.at() - System.currentTimeMillis()) + ").";
        }
        return status.reason().equals("per Befehl versiegelt") ? "Dieses Portal ist versiegelt."
                : "Dieses Portal ist noch versiegelt; es öffnet sich erst, wenn die Sift offen ist.";
    }

    public java.time.ZoneId zone() {
        return gate == null ? java.time.ZoneId.systemDefault() : gate.zone();
    }
}
