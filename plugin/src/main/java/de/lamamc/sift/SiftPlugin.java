package de.lamamc.sift;

import de.lamamc.sift.block.BlockSettings;
import de.lamamc.sift.block.BreakSpeedListener;
import de.lamamc.sift.block.CustomBlockListener;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.block.MappingEntry;
import de.lamamc.sift.block.PhysicsGuard;
import de.lamamc.sift.bootstrap.SiftBootstrap;
import de.lamamc.sift.command.SiftCommand;
import de.lamamc.sift.item.ItemRegistry;
import de.lamamc.sift.item.ItemSettings;
import de.lamamc.sift.item.LootNormalizer;
import de.lamamc.sift.item.SiftAdvancements;
import de.lamamc.sift.item.SiftRecipes;
import de.lamamc.sift.item.SiftiteGuard;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.item.Translations;
import de.lamamc.sift.menu.ItemMenuListener;
import de.lamamc.sift.pack.ResourcePackService;
import de.lamamc.sift.world.DebugPopulator;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.gen.PortalReservation;
import de.lamamc.sift.world.gen.SiftPopulator;
import de.lamamc.sift.world.gen.SiftTemplates;
import de.lamamc.sift.world.gen.WorldgenSettings;
import de.lamamc.sift.block.PlantSupport;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftPlugin extends JavaPlugin implements Listener {

    private DebugPopulator debugPopulator;
    private CustomBlockRegistry blocks;
    private BlockSettings blockSettings;
    private SiftItems items;
    private ResourcePackService resourcePack;
    private SiftRecipes recipes;
    private SiftPopulator siftPopulator;
    private MainPortalBuilder mainPortal;

    @Override
    public void onEnable() {
        if (SiftBootstrap.incompatibility() != null) {
            getLogger().severe(SiftBootstrap.incompatibility());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        // new settings of an update land in the existing config.yml; reading the merged file back, because
        // sections that only exist in the defaults show up empty in memory until then
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadConfig();
        if (getConfig().getBoolean("debug.populator-marker", false)) {
            debugPopulator = new DebugPopulator(getLogger(), getConfig().getInt("debug.populator-log-interval", 64));
            getLogger().warning("Debug populator is enabled: every new Sift chunk gets a glowstone marker");
        }
        List<MappingEntry> mapping = MappingEntry.bundled();
        blocks = new CustomBlockRegistry(mapping);
        blockSettings = BlockSettings.load(getConfig(), getLogger());
        items = new SiftItems(ItemRegistry.loadFromJar(mapping), Translations.loadFromJar(),
                ItemSettings.load(getConfig().getConfigurationSection("items")));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CustomBlockListener(this, blocks, blockSettings, items), this);
        getServer().getPluginManager().registerEvents(new BreakSpeedListener(blocks, blockSettings), this);
        getServer().getPluginManager().registerEvents(
                new PhysicsGuard(this, blocks, blockSettings.placeOnlyInSift()), this);
        getServer().getPluginManager().registerEvents(new ItemMenuListener(items, SiftCommand.ITEMS_PERMISSION), this);
        recipes = new SiftRecipes(items, getLogger());
        recipes.register();
        getServer().getPluginManager().registerEvents(new SiftiteGuard(items), this);
        getServer().getPluginManager().registerEvents(new LootNormalizer(items), this);
        getServer().getPluginManager().registerEvents(new SiftAdvancements(blocks, recipes,
                getConfig().getBoolean("recipes.discover-on-join", true), getLogger()), this);
        resourcePack = new ResourcePackService(this);
        resourcePack.load(getConfig().getConfigurationSection("resource-pack"));
        getServer().getPluginManager().registerEvents(resourcePack, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new SiftCommand(this).register(event.registrar()));
        // load: STARTUP enables the plugin before the worlds exist; this only matters after a reload
        getServer().getWorlds().forEach(this::attach);
    }

    // Populators have to be in place before the first chunk of the world is generated
    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attach(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (SiftWorlds.isSift(event.getWorld())) {
            World world = event.getWorld();
            getLogger().info("The Sift is loaded as world '" + world.getName() + "' (" + world.getKey()
                    + ", environment " + world.getEnvironment() + ", height " + world.getMinHeight() + ".."
                    + world.getMaxHeight() + ")");
            if (mainPortal != null) {
                mainPortal.ensure(world);
            }
        }
    }

    private void attach(World world) {
        if (!SiftWorlds.isSift(world)) {
            return;
        }
        if (siftPopulator == null) {
            WorldgenSettings worldgen = WorldgenSettings.load(getConfig());
            SiftTemplates templates = new SiftTemplates(blocks, SiftPopulator.templateNames(), getLogger());
            PortalReservation reservation = new PortalReservation();
            if (worldgen.mainPortal()) {
                mainPortal = new MainPortalBuilder(this, blocks, templates, reservation);
                mainPortal.reserve(world);
            }
            siftPopulator = new SiftPopulator(blocks, new PlantSupport(blockSettings, blocks), worldgen, templates, reservation,
                    getLogger());
            world.getPopulators().add(siftPopulator);
            getLogger().info("Sift worldgen attached to " + world.getKey() + " (" + templates.size() + " templates)");
        }
        if (debugPopulator != null && !world.getPopulators().contains(debugPopulator)) {
            world.getPopulators().add(debugPopulator);
            getLogger().info("Debug populator attached to " + world.getKey());
        }
    }

    @Override
    public void onDisable() {
        if (blocks != null) {
            getServer().getOnlinePlayers().forEach(BreakSpeedListener::clear);
        }
    }

    public DebugPopulator debugPopulator() {
        return debugPopulator;
    }

    public CustomBlockRegistry blocks() {
        return blocks;
    }

    public BlockSettings blockSettings() {
        return blockSettings;
    }

    public SiftItems items() {
        return items;
    }

    public SiftPopulator siftPopulator() {
        return siftPopulator;
    }

    public SiftRecipes recipes() {
        return recipes;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    /** Re-reads config.yml for the parts that can change at runtime (resource pack for now). */
    public void reloadResourcePack() {
        reloadConfig();
        resourcePack.load(getConfig().getConfigurationSection("resource-pack"));
    }
}
