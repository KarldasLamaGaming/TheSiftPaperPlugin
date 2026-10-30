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
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.gen.LandmarkLog;
import de.lamamc.sift.world.gen.PortalReservation;
import de.lamamc.sift.world.gen.SiftPopulator;
import de.lamamc.sift.world.gen.SiftTemplates;
import de.lamamc.sift.world.gen.WorldgenSettings;
import de.lamamc.sift.block.PlantSupport;
import de.lamamc.sift.block.SiftFire;
import de.lamamc.sift.item.SiftFuel;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiftPlugin extends JavaPlugin implements Listener {

    private CustomBlockRegistry blocks;
    private BlockSettings blockSettings;
    private SiftItems items;
    private ResourcePackService resourcePack;
    private SiftRecipes recipes;
    private SiftPopulator siftPopulator;
    private MainPortalBuilder mainPortal;
    private de.lamamc.sift.world.portal.PortalService portals;
    private de.lamamc.sift.world.portal.SonorousConsoles consoles;
    private final java.util.Map<String, de.lamamc.sift.world.portal.ShrinePopulator> shrines = new java.util.HashMap<>();

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
        List<MappingEntry> mapping = MappingEntry.bundled();
        blocks = new CustomBlockRegistry(mapping);
        blockSettings = BlockSettings.load(getConfig(), getLogger());
        blocks.restrictWorlds(blockSettings::allowedIn);
        items = new SiftItems(ItemRegistry.loadFromJar(mapping), Translations.loadFromJar(),
                ItemSettings.load(getConfig().getConfigurationSection("items")));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CustomBlockListener(this, blocks, blockSettings, items), this);
        getServer().getPluginManager().registerEvents(new BreakSpeedListener(blocks, blockSettings), this);
        getServer().getPluginManager().registerEvents(
                new PhysicsGuard(this, blocks, blockSettings.placeOnlyInSift()), this);
        getServer().getPluginManager().registerEvents(new ItemMenuListener(items, SiftCommand.ITEMS_PERMISSION), this);
        recipes = new SiftRecipes(items, getConfig().getConfigurationSection("portals.recipes"), getLogger());
        recipes.register();
        getServer().getPluginManager().registerEvents(new SiftiteGuard(items), this);
        getServer().getPluginManager().registerEvents(new SiftFire(this, blocks, blockSettings), this);
        getServer().getPluginManager().registerEvents(new SiftFuel(blockSettings), this);
        getServer().getPluginManager().registerEvents(new LootNormalizer(items), this);
        getServer().getPluginManager().registerEvents(new SiftAdvancements(blocks, recipes,
                getConfig().getBoolean("recipes.discover-on-join", true), getLogger()), this);
        resourcePack = new ResourcePackService(this);
        resourcePack.load(getConfig().getConfigurationSection("resource-pack"));
        getServer().getPluginManager().registerEvents(resourcePack, this);
        portals = new de.lamamc.sift.world.portal.PortalService(this, new de.lamamc.sift.world.portal.PortalRegistry(),
                new de.lamamc.sift.world.portal.PortalSurface(this, getConfig().getInt("portals.light-level", 12)));
        getServer().getPluginManager().registerEvents(portals, this);
        portals.start(getConfig().getInt("portals.check-ticks", 2));
        List<Integer> melody = getConfig().getIntegerList("portals.melody");
        consoles = new de.lamamc.sift.world.portal.SonorousConsoles(this, blocks, portals,
                melody.isEmpty() ? de.lamamc.sift.core.portal.PortalMelody.DEFAULT_SEQUENCE : melody.stream().mapToInt(Integer::intValue).toArray(),
                portalKeys());
        getServer().getPluginManager().registerEvents(consoles, this);
        de.lamamc.sift.core.portal.KeyLoot keyLoot = new de.lamamc.sift.core.portal.KeyLoot();
        org.bukkit.configuration.ConfigurationSection keySection = getConfig().getConfigurationSection("portals.keys");
        if (keySection != null) {
            for (String dimension : keySection.getKeys(false)) {
                for (String table : keySection.getStringList(dimension + ".loot.tables")) {
                    keyLoot.add(table, keySection.getString(dimension + ".item", ""), keySection.getDouble(dimension + ".loot.chance", 0));
                }
            }
        }
        getServer().getPluginManager().registerEvents(new de.lamamc.sift.world.portal.PortalKeyLoot(keyLoot, items), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new SiftCommand(this).register(event.registrar()));
        // load: STARTUP enables the plugin before the worlds exist; this only matters after a reload
        getServer().getWorlds().forEach(this::attach);
    }

    /** Portal shrines in the main world, the nether and the end (portals.structures.*). */
    private void attachShrines(World world) {
        de.lamamc.sift.world.portal.ShrinePopulator.Dimension dimension = switch (world.getKey().toString()) {
            case "minecraft:overworld" -> de.lamamc.sift.world.portal.ShrinePopulator.Dimension.OVERWORLD;
            case "minecraft:the_nether" -> de.lamamc.sift.world.portal.ShrinePopulator.Dimension.NETHER;
            case "minecraft:the_end" -> de.lamamc.sift.world.portal.ShrinePopulator.Dimension.END;
            default -> null;
        };
        String name = dimension == null ? null : dimension.name().toLowerCase(java.util.Locale.ROOT);
        if (dimension == null || shrines.containsKey(world.getKey().toString())
                || !getConfig().getBoolean("portals.structures." + name + ".enabled", true)) {
            return;
        }
        long salt = switch (dimension) {
            case OVERWORLD -> de.lamamc.sift.core.portal.PortalSites.OVERWORLD_SALT;
            case NETHER -> de.lamamc.sift.core.portal.PortalSites.NETHER_SALT;
            case END -> de.lamamc.sift.core.portal.PortalSites.END_SALT;
        };
        int defaultCell = switch (dimension) {
            case OVERWORLD -> 64;
            case NETHER -> 16;
            case END -> 32;
        };
        de.lamamc.sift.core.portal.PortalSites sites = new de.lamamc.sift.core.portal.PortalSites(
                Math.max(3, getConfig().getInt("portals.structures." + name + ".cell-chunks", defaultCell)),
                getConfig().getInt("portals.structures." + name + ".min-distance", dimension == de.lamamc.sift.world.portal.ShrinePopulator.Dimension.END ? 1100 : 0),
                salt);
        List<de.lamamc.sift.world.portal.CustomShrine.Block> custom = null;
        java.io.File file = new java.io.File(getDataFolder(), "structures/" + name + ".nbt");
        if (file.isFile()) {
            try {
                custom = de.lamamc.sift.world.portal.CustomShrine.load(file, blocks);
                getLogger().info("Portal shrine of " + world.getKey() + " from " + file);
            } catch (java.io.IOException | RuntimeException e) {
                getLogger().warning("Ignoring " + file + ": " + e.getMessage() + " (using the built-in shrine)");
            }
        }
        de.lamamc.sift.world.portal.ShrinePopulator populator = new de.lamamc.sift.world.portal.ShrinePopulator(dimension, sites,
                blocks, custom, portals.shrineQueue(), getLogger());
        shrines.put(world.getKey().toString(), populator);
        world.getPopulators().add(populator);
        getLogger().info("Portal shrines attached to " + world.getKey() + " (one per " + sites.cellChunks() + " x "
                + sites.cellChunks() + " chunks)");
    }

    /** World key -> id of the key that wakes its portals (portals.keys). */
    private java.util.Map<String, String> portalKeys() {
        java.util.Map<String, String> keys = new java.util.HashMap<>();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("portals.keys");
        if (section != null) {
            for (String dimension : section.getKeys(false)) {
                String world = switch (dimension) {
                    case "overworld" -> "minecraft:overworld";
                    case "nether" -> "minecraft:the_nether";
                    case "end" -> "minecraft:the_end";
                    default -> dimension;
                };
                keys.put(world, section.getString(dimension + ".item", ""));
            }
        }
        return keys;
    }

    /** Where the shrines of this world stand, or null (no shrines in this world). */
    public de.lamamc.sift.core.portal.PortalSites shrineSites(World world) {
        de.lamamc.sift.world.portal.ShrinePopulator populator = shrines.get(world.getKey().toString());
        return populator == null ? null : populator.sites();
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
            attachShrines(world);
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
            LandmarkLog landmarkLog = new LandmarkLog();
            landmarkLog.load(world);
            siftPopulator = new SiftPopulator(blocks, new PlantSupport(blockSettings, blocks), worldgen, templates, reservation,
                    landmarkLog, getLogger());
            // worldgen threads record, the main thread writes into the world's persistent data
            getServer().getScheduler().runTaskTimer(this, () -> landmarkLog.save(world), 100L, 100L);
            world.getPopulators().add(siftPopulator);
            getLogger().info("Sift worldgen attached to " + world.getKey() + " (" + templates.size() + " templates)");
        }
    }

    @Override
    public void onDisable() {
        World sift = getServer().getWorld(SiftWorlds.DIMENSION);
        if (siftPopulator != null && sift != null) {
            siftPopulator.log().save(sift);
        }
        if (blocks != null) {
            getServer().getOnlinePlayers().forEach(BreakSpeedListener::clear);
        }
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

    public de.lamamc.sift.world.portal.SonorousConsoles consoles() {
        return consoles;
    }

    public de.lamamc.sift.world.portal.PortalService portals() {
        return portals;
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
