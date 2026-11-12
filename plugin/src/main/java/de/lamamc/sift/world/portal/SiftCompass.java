package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalSites;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Sift compass: while a player holds it, its needle points at the nearest portal of the dimension
 * (in the Sift at the main portal) and the action bar shows the distance. Sites are computed, not
 * searched, so this costs nothing even where the shrine is not generated yet.
 *
 * <p>In the Sift, sneak + right-click switches the compass between the main portal and the nearest temple whose
 * guardian still sleeps (alpha.41b, phase4.temples.compass); the mode is kept on the item. Defeated temples are
 * skipped. Outside the Sift the compass always points at portals.
 */
public final class SiftCompass implements Listener {

    public static final String ID = "sift_compass";
    private static final int SEARCH_RINGS = 64;

    /** Item data: "temple" when the compass points at temples (absent = main portal). */
    public static final NamespacedKey MODE = new NamespacedKey("the_sift", "compass_mode");
    private static final String TEMPLE = "temple";

    /** The nearest sleeping temple from a position in the Sift, or null when there is none (or temples are off). */
    public interface TempleTarget {
        Location nearest(Location here);
    }

    private final Function<World, PortalSites> sites;
    private Supplier<TempleTarget> temples = () -> null;

    public SiftCompass(Function<World, PortalSites> sites) {
        this.sites = sites;
    }

    /** Enables the temple mode (alpha.41b); the supplier gives null while the temples are off. */
    public SiftCompass temples(Supplier<TempleTarget> temples) {
        this.temples = temples;
        return this;
    }

    public void start(Plugin plugin, int updateTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, Math.max(5, updateTicks));
    }

    private static boolean templeMode(ItemStack compass) {
        return TEMPLE.equals(compass.getPersistentDataContainer().get(MODE, PersistentDataType.STRING));
    }

    @EventHandler
    public void onSwitch(PlayerInteractEvent event) {
        if (event.getHand() == null || !event.getPlayer().isSneaking()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack compass = player.getInventory().getItem(event.getHand());
        if (!ID.equals(SiftItems.idOf(compass)) || !SiftWorlds.isSift(player.getWorld()) || temples.get() == null) {
            return;
        }
        event.setCancelled(true); // no lodestone binding, no block use
        boolean toTemple = !templeMode(compass);
        compass.editPersistentDataContainer(pdc -> {
            if (toTemple) {
                pdc.set(MODE, PersistentDataType.STRING, TEMPLE);
            } else {
                pdc.remove(MODE);
            }
        });
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.6f, toTemple ? 0.8f : 1.2f);
        point(player, compass);
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack main = player.getInventory().getItemInMainHand();
            ItemStack off = player.getInventory().getItemInOffHand();
            ItemStack compass = ID.equals(SiftItems.idOf(main)) ? main : ID.equals(SiftItems.idOf(off)) ? off : null;
            if (compass != null) {
                point(player, compass);
            }
        }
    }

    /** The nearest portal for a player, or null where there is none. */
    public Location target(Location here) {
        World world = here.getWorld();
        if (SiftWorlds.isSift(world)) {
            return MainPortalBuilder.anchor(world);
        }
        PortalSites worldSites = sites.apply(world);
        PortalSites.Site site = worldSites == null ? null : worldSites.nearest(world.getSeed(), here.getX(), here.getZ(), SEARCH_RINGS);
        // the frame of the shrine (ShrineLayout: corner = chunk corner, frame centre 7 / 10 blocks in)
        return site == null ? null : new Location(world, (site.chunkX() << 4) + 7, here.getY(), (site.chunkZ() << 4) + 10);
    }

    private void point(Player player, ItemStack compass) {
        TempleTarget templeTarget = temples.get();
        boolean temple = templeTarget != null && SiftWorlds.isSift(player.getWorld()) && templeMode(compass);
        Location target = temple ? templeTarget.nearest(player.getLocation()) : target(player.getLocation());
        if (target == null && temple) {
            if (compass.hasData(DataComponentTypes.LODESTONE_TRACKER)) {
                compass.unsetData(DataComponentTypes.LODESTONE_TRACKER);
            }
            player.sendActionBar(de.lamamc.sift.Messages.current().get(player, "compass.temples-silent"));
            return;
        }
        if (target == null) {
            if (compass.hasData(DataComponentTypes.LODESTONE_TRACKER)) {
                compass.unsetData(DataComponentTypes.LODESTONE_TRACKER); // needle spins
            }
            player.sendActionBar(de.lamamc.sift.Messages.current().get(player, "compass.no-portal"));
            return;
        }
        LodestoneTracker current = compass.getData(DataComponentTypes.LODESTONE_TRACKER);
        Location old = current == null ? null : current.location();
        if (old == null || old.getWorld() != target.getWorld() || old.getBlockX() != target.getBlockX()
                || old.getBlockZ() != target.getBlockZ()) {
            compass.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(target, false));
        }
        long distance = Math.round(Math.hypot(target.getX() - player.getX(), target.getZ() - player.getZ()));
        player.sendActionBar(de.lamamc.sift.Messages.current().get(player, temple ? "compass.temple" : "compass.portal", "distance",
                String.valueOf(distance)));
    }
}
