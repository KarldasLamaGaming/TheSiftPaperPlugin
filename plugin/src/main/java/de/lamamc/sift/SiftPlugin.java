package de.lamamc.sift;

import de.lamamc.sift.bootstrap.SiftWorldConfig;
import de.lamamc.sift.command.SiftCommand;
import de.lamamc.sift.world.DebugPopulator;
import de.lamamc.sift.world.SiftWorlds;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftPlugin extends JavaPlugin implements Listener {

    private DebugPopulator debugPopulator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getBoolean("debug.populator-marker", false)) {
            debugPopulator = new DebugPopulator(getLogger(), getConfig().getInt("debug.populator-log-interval", 64));
            getLogger().warning("Debug populator is enabled: every new Sift chunk gets a glowstone marker");
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new SiftCommand(this).register(event.registrar()));
        // load: STARTUP enables the plugin before the worlds exist; this only matters after a reload
        getServer().getWorlds().forEach(this::attach);
    }

    // Populators have to be in place before the first chunk of the world is generated
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
        if (SiftWorlds.isSift(event.getWorld())) {
            checkWorldConfig(event.getWorld());
        }
    }

    /**
     * On the very first start the bootstrapper cannot prepare the Paper world config (see SiftBootstrap), and
     * Paper has read it already when this event fires; the switches then take effect after a restart.
     */
    private void checkWorldConfig(World world) {
        Path file = world.getWorldPath().resolve("paper-world.yml");
        try {
            List<String> missing = SiftWorldConfig.missingSwitches(file);
            if (missing.isEmpty()) {
                return;
            }
            if (getConfig().getBoolean("server-config.manage-sift-world-config", true)) {
                SiftWorldConfig.ensureSwitches(file);
                getLogger().warning("Set " + missing + " in " + file + ". RESTART the server before players enter "
                        + "The Sift, until then custom blocks there can change their look.");
            } else {
                getLogger().warning("The Sift needs " + missing + " = true in " + file
                        + "; custom blocks there will change their look without it.");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not check the Paper world config of the Sift", e);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (SiftWorlds.isSift(event.getWorld())) {
            World world = event.getWorld();
            getLogger().info("The Sift is loaded as world '" + world.getName() + "' (" + world.getKey()
                    + ", environment " + world.getEnvironment() + ", height " + world.getMinHeight() + ".."
                    + world.getMaxHeight() + ")");
        }
    }

    private void attach(World world) {
        if (!SiftWorlds.isSift(world) || debugPopulator == null || world.getPopulators().contains(debugPopulator)) {
            return;
        }
        world.getPopulators().add(debugPopulator);
        getLogger().info("Debug populator attached to " + world.getKey());
    }

    public DebugPopulator debugPopulator() {
        return debugPopulator;
    }
}
