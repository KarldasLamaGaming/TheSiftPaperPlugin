package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.PortalSites;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * The Sift compass: while a player holds it, its needle points at the nearest portal of the dimension
 * (in the Sift at the main portal) and the action bar shows the distance. Sites are computed, not
 * searched, so this costs nothing even where the shrine is not generated yet.
 */
public final class SiftCompass {

    public static final String ID = "sift_compass";
    private static final int SEARCH_RINGS = 64;

    private final Function<World, PortalSites> sites;

    public SiftCompass(Function<World, PortalSites> sites) {
        this.sites = sites;
    }

    public void start(Plugin plugin, int updateTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, Math.max(5, updateTicks));
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
        Location target = target(player.getLocation());
        if (target == null) {
            if (compass.hasData(DataComponentTypes.LODESTONE_TRACKER)) {
                compass.unsetData(DataComponentTypes.LODESTONE_TRACKER); // needle spins
            }
            player.sendActionBar(Component.text("Kein Portal in dieser Welt", NamedTextColor.GRAY));
            return;
        }
        LodestoneTracker current = compass.getData(DataComponentTypes.LODESTONE_TRACKER);
        Location old = current == null ? null : current.location();
        if (old == null || old.getWorld() != target.getWorld() || old.getBlockX() != target.getBlockX()
                || old.getBlockZ() != target.getBlockZ()) {
            compass.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(target, false));
        }
        long distance = Math.round(Math.hypot(target.getX() - player.getX(), target.getZ() - player.getZ()));
        player.sendActionBar(Component.text("Portal: " + distance + " Blöcke", NamedTextColor.LIGHT_PURPLE));
    }
}
