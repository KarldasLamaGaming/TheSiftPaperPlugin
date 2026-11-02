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
import de.lamamc.sift.world.gen.SoulCanyonLandmarks;
import de.lamamc.sift.world.gen.SoulCanyons;
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
    private SoulCanyonLandmarks canyonLandmarks;
    private de.lamamc.sift.world.gen.SoulCanyonParticles canyonParticles;
    private MainPortalBuilder mainPortal;
    private de.lamamc.sift.world.portal.PortalService portals;
    private de.lamamc.sift.world.portal.SonorousConsoles consoles;
    private final java.util.Map<String, de.lamamc.sift.world.portal.ShrinePopulator> shrines = new java.util.HashMap<>();
    private Messages messages;
    private de.lamamc.sift.mob.MobManager mobs;
    private de.lamamc.sift.mob.MobSpawner mobSpawner;
    private de.lamamc.sift.mob.singer.SingerSummoner singerSummoner;
    private de.lamamc.sift.mob.darksniffer.DarkSnifferSpawns darkSnifferSpawns;
    private de.lamamc.sift.mob.echogolem.GolemLight golemLight;
    private de.lamamc.sift.mob.echogolem.EchoGolemSpawner golemSpawner;
    private de.lamamc.sift.mob.darksniffer.SnifferInfection infection;

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
        consoles.setKeysEnabled(getConfig().getBoolean("portals.keys.enabled", true));
        consoles.setAncientCitySequence(getConfig().getIntegerList("portals.ancient-city.melody").stream()
                .mapToInt(Integer::intValue).filter(n -> n >= 1 && n <= 8).toArray());
        getServer().getPluginManager().registerEvents(consoles, this);
        de.lamamc.sift.core.portal.KeyLoot keyLoot = new de.lamamc.sift.core.portal.KeyLoot();
        org.bukkit.configuration.ConfigurationSection keySection = getConfig().getConfigurationSection("portals.keys");
        if (keySection != null) {
            for (String dimension : keySection.getKeys(false)) {
                if (!keySection.isConfigurationSection(dimension)) {
                    continue; // enabled
                }
                for (String table : keySection.getStringList(dimension + ".loot.tables")) {
                    keyLoot.add(table, keySection.getString(dimension + ".item", ""), keySection.getDouble(dimension + ".loot.chance", 0));
                }
            }
        }
        if (consoles.keysEnabled()) {
            getServer().getPluginManager().registerEvents(new de.lamamc.sift.world.portal.PortalKeyLoot(keyLoot, items), this);
        }
        new de.lamamc.sift.world.portal.SiftCompass(this::shrineSites).start(this, getConfig().getInt("portals.compass.update-ticks", 40));
        messages = Messages.load(this);
        if (getConfig().getBoolean("portals.ancient-city.enabled", true)) {
            de.lamamc.sift.world.portal.AncientCityPortals cities = new de.lamamc.sift.world.portal.AncientCityPortals(this, blocks,
                    portals, getConfig().getInt("portals.frame-radius", 10), getLogger());
            getServer().getPluginManager().registerEvents(cities, this);
            cities.start();
            getServer().getPluginManager().registerEvents(new de.lamamc.sift.world.portal.AncientCityBook(
                    getConfig().getStringList("portals.ancient-city.book.tables"), getConfig().getDouble("portals.ancient-city.book.chance", 0.15),
                    consoles.ancientCitySequence(), messages), this);
        }
        startMobs();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new SiftCommand(this).register(event.registrar()));
        // load: STARTUP enables the plugin before the worlds exist; this only matters after a reload
        getServer().getWorlds().forEach(this::attach);
    }

    /** Sift mobs (phase 2): registry, base mob guard, spawn eggs, natural spawning (config.yml mobs). */
    private void startMobs() {
        org.bukkit.configuration.ConfigurationSection config = getConfig().getConfigurationSection("mobs");
        mobs = new de.lamamc.sift.mob.MobManager(this, config);
        mobSpawner = new de.lamamc.sift.mob.MobSpawner(mobs);
        if (config == null || !config.getBoolean("enabled", true)) {
            getLogger().info("Sift mobs are off (mobs.enabled)");
            return;
        }
        org.bukkit.configuration.ConfigurationSection blub = config.getConfigurationSection("blub");
        mobs.register(new de.lamamc.sift.mob.blub.BlubKind(blub != null ? blub : new org.bukkit.configuration.MemoryConfiguration()));
        org.bukkit.configuration.ConfigurationSection sifter = config.getConfigurationSection("sifter");
        mobs.register(new de.lamamc.sift.mob.sifter.SifterKind(sifter != null ? sifter : new org.bukkit.configuration.MemoryConfiguration(),
                blocks, items));
        org.bukkit.configuration.ConfigurationSection singer = config.getConfigurationSection("singer");
        de.lamamc.sift.mob.singer.SingerKind singerKind = new de.lamamc.sift.mob.singer.SingerKind(
                singer != null ? singer : new org.bukkit.configuration.MemoryConfiguration(), portals, consoles, items);
        mobs.register(singerKind);
        singerSummoner = new de.lamamc.sift.mob.singer.SingerSummoner(this, mobs, singerKind);
        getServer().getPluginManager().registerEvents(singerSummoner, this);
        org.bukkit.configuration.ConfigurationSection darkSniffer = config.getConfigurationSection("dark_sniffer");
        de.lamamc.sift.mob.darksniffer.DarkSnifferKind darkSnifferKind = new de.lamamc.sift.mob.darksniffer.DarkSnifferKind(
                darkSniffer != null ? darkSniffer : new org.bukkit.configuration.MemoryConfiguration(), items);
        mobs.register(darkSnifferKind);
        infection = new de.lamamc.sift.mob.darksniffer.SnifferInfection(mobs, darkSnifferKind);
        getServer().getPluginManager().registerEvents(infection, this);
        infection.start(this);
        de.lamamc.sift.mob.darksniffer.WardenFriends wardens = new de.lamamc.sift.mob.darksniffer.WardenFriends(mobs, darkSnifferKind);
        getServer().getPluginManager().registerEvents(wardens, this);
        wardens.start(this);
        darkSnifferSpawns = new de.lamamc.sift.mob.darksniffer.DarkSnifferSpawns(mobs, darkSnifferKind);
        darkSnifferSpawns.start(this);
        org.bukkit.configuration.ConfigurationSection golem = config.getConfigurationSection("echo_golem");
        if (golem == null) {
            golem = new org.bukkit.configuration.MemoryConfiguration();
        }
        de.lamamc.sift.mob.echogolem.EchoGolemKind golemKind = new de.lamamc.sift.mob.echogolem.EchoGolemKind(golem, items, blocks,
                () -> canyonLandmarks);
        mobs.register(golemKind);
        golemLight = new de.lamamc.sift.mob.echogolem.GolemLight(this, golem, getLogger());
        golemKind.light(golemLight);
        getServer().getPluginManager().registerEvents(golemLight, this);
        golemLight.cleanLoaded();
        golemKind.singers(new de.lamamc.sift.mob.singer.SoulSingers(mobs, singerKind, () -> canyonLandmarks));
        golemSpawner = new de.lamamc.sift.mob.echogolem.EchoGolemSpawner(mobs, golemKind, () -> canyonLandmarks);
        golemSpawner.start(this);
        getServer().getPluginManager().registerEvents(mobs, this);
        getServer().getPluginManager().registerEvents(new de.lamamc.sift.mob.BaseMobGuard(mobs), this);
        getServer().getPluginManager().registerEvents(new de.lamamc.sift.mob.SpawnEggs(mobs), this);
        getServer().getPluginManager().registerEvents(new de.lamamc.sift.mob.blub.BlubTargets(), this);
        mobs.start();
        mobSpawner.start(this);
    }

    public Messages messages() {
        return messages;
    }

    public de.lamamc.sift.mob.MobManager mobs() {
        return mobs;
    }

    public de.lamamc.sift.mob.darksniffer.SnifferInfection snifferInfection() {
        return infection;
    }

    public de.lamamc.sift.mob.echogolem.GolemLight golemLight() {
        return golemLight;
    }

    public de.lamamc.sift.mob.echogolem.EchoGolemSpawner golemSpawner() {
        return golemSpawner;
    }

    public de.lamamc.sift.mob.darksniffer.DarkSnifferSpawns darkSnifferSpawns() {
        return darkSnifferSpawns;
    }

    public de.lamamc.sift.mob.singer.SingerSummoner singerSummoner() {
        return singerSummoner;
    }

    public de.lamamc.sift.mob.MobSpawner mobSpawner() {
        return mobSpawner;
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
                if (!section.isConfigurationSection(dimension)) {
                    continue; // enabled
                }
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
            canyonLandmarks = new SoulCanyonLandmarks(blocks);
            canyonLandmarks.load(world);
            getServer().getPluginManager().registerEvents(canyonLandmarks, this);
            canyonParticles = new de.lamamc.sift.world.gen.SoulCanyonParticles(canyonLandmarks, blocks,
                    de.lamamc.sift.world.gen.SoulCanyonParticles.Settings.load(
                            getConfig().getConfigurationSection("worldgen.soul-canyons")));
            canyonParticles.start(this);
            SoulCanyons canyons = new SoulCanyons(blocks, canyonLandmarks, worldgen.canyonCellChunks(), worldgen.canyonMinSouls());
            siftPopulator = new SiftPopulator(blocks, new PlantSupport(blockSettings, blocks), worldgen, templates, reservation,
                    landmarkLog, canyons, getLogger());
            // worldgen threads record, the main thread writes into the world's persistent data
            getServer().getScheduler().runTaskTimer(this, () -> {
                landmarkLog.save(world);
                canyonLandmarks.save(world);
            }, 100L, 100L);
            world.getPopulators().add(siftPopulator);
            getLogger().info("Sift worldgen attached to " + world.getKey() + " (" + templates.size() + " templates)");
        }
    }

    @Override
    public void onDisable() {
        World sift = getServer().getWorld(SiftWorlds.DIMENSION);
        if (siftPopulator != null && sift != null) {
            siftPopulator.log().save(sift);
            canyonLandmarks.save(sift);
        }
        if (blocks != null) {
            getServer().getOnlinePlayers().forEach(BreakSpeedListener::clear);
        }
        if (golemLight != null) {
            golemLight.removeAll(); // before the golems detach: no block changes are scheduled any more
        }
        if (mobs != null) {
            mobs.stop();
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

    public de.lamamc.sift.world.gen.SoulCanyonParticles canyonParticles() {
        return canyonParticles;
    }

    public SoulCanyonLandmarks canyonLandmarks() {
        return canyonLandmarks;
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
