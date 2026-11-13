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
    private de.lamamc.sift.world.access.SiftGate gate;
    private SiftAdvancements advancements;
    private de.lamamc.sift.world.rift.RiftManager rifts;
    private de.lamamc.sift.world.atmosphere.SiftIntro intro;
    private de.lamamc.sift.mob.allay.AllaySpawner allays;
    private de.lamamc.sift.world.weather.SiftWeather weather;
    private de.lamamc.sift.world.admin.PlayerReset playerReset;
    private de.lamamc.sift.world.siftite.SiftiteKeeper siftiteKeeper;
    private de.lamamc.sift.mob.MobManager mobs;
    private de.lamamc.sift.mob.MobSpawner mobSpawner;
    private de.lamamc.sift.mob.singer.SingerSummoner singerSummoner;
    private de.lamamc.sift.world.ichor.FluidMarks fluidMarks;
    private de.lamamc.sift.world.enchanter.SculkEnchanters enchanters;
    private de.lamamc.sift.world.temple.TempleRegistry temples;
    private de.lamamc.sift.world.ichor.FluidIdentityListener fluidIdentity;
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
        updateConfig();
        messages = Messages.load(this); // before anything that talks to players (alpha.42c: German and English)
        messages.console(de.lamamc.sift.core.lang.Lang.ofSetting(getConfig().getString("language", "de")));
        List<MappingEntry> mapping = MappingEntry.bundled();
        blocks = new CustomBlockRegistry(mapping);
        blockSettings = BlockSettings.load(getConfig(), getLogger());
        if (getConfig().getBoolean("phase4.enchanter.enabled", true)) {
            blockSettings.allowOutsideSift(de.lamamc.sift.world.enchanter.SculkEnchanters.ID); // alpha.40.1, works in every world
        }
        blocks.restrictWorlds(blockSettings::allowedIn);
        items = new SiftItems(ItemRegistry.loadFromJar(mapping), Translations.loadFromJar(),
                ItemSettings.load(getConfig().getConfigurationSection("items")));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new CustomBlockListener(this, blocks, blockSettings, items), this);
        BreakSpeedListener breakSpeed = new BreakSpeedListener(blocks, blockSettings);
        getServer().getPluginManager().registerEvents(breakSpeed, this);
        breakSpeed.start(this, Math.max(0, getConfig().getInt("custom-blocks.break-speed-target-ticks", 2)));
        getServer().getPluginManager().registerEvents(
                new PhysicsGuard(this, blocks, blockSettings.placeOnlyInSift()), this);
        getServer().getPluginManager().registerEvents(new ItemMenuListener(items, SiftCommand.ITEMS_PERMISSION), this);
        recipes = new SiftRecipes(items, getConfig().getConfigurationSection("portals.recipes"),
                getConfig().getDefaults() == null ? null : getConfig().getDefaults().getConfigurationSection("portals.recipes"), getLogger());
        recipes.addConfigSection(getConfig().getConfigurationSection("phase4.recipes"),
                getConfig().getDefaults() == null ? null : getConfig().getDefaults().getConfigurationSection("phase4.recipes"));
        recipes.register();
        getServer().getPluginManager().registerEvents(new SiftiteGuard(items), this);
        getServer().getPluginManager().registerEvents(new SiftFire(this, blocks, blockSettings), this);
        getServer().getPluginManager().registerEvents(new SiftFuel(blockSettings), this);
        getServer().getPluginManager().registerEvents(new LootNormalizer(items), this);
        advancements = new SiftAdvancements(blocks, recipes, getConfig().getBoolean("recipes.discover-on-join", true), getLogger());
        getServer().getPluginManager().registerEvents(advancements, this);
        resourcePack = new ResourcePackService(this);
        resourcePack.load(getConfig().getConfigurationSection("resource-pack"));
        getServer().getPluginManager().registerEvents(resourcePack, this);
        // since alpha.37 only the keys open portals; the tune settings are gone
        for (String retired : List.of("portals.melody", "portals.ancient-city.melody", "portals.ancient-city.book")) {
            if (getConfig().isSet(retired)) {
                getLogger().warning(retired + " in config.yml is no longer used (alpha.37: only the keys open portals); "
                        + "remove it, see docs/ADMIN.md");
            }
        }
        de.lamamc.sift.world.portal.PortalRegistry registry = new de.lamamc.sift.world.portal.PortalRegistry();
        portals = new de.lamamc.sift.world.portal.PortalService(this, registry,
                new de.lamamc.sift.world.portal.PortalSurface(this, getConfig().getInt("portals.light-level", 12)));
        getServer().getPluginManager().registerEvents(portals, this);
        portals.start(getConfig().getInt("portals.check-ticks", 2));
        consoles = new de.lamamc.sift.world.portal.SonorousConsoles(this, blocks, portals, portalKeys(),
                getConfig().getString("portals.keys.ancient-city.item", "ancient_resonator"));
        consoles.setKeys(getConfig().getBoolean("portals.keys.enabled", true), getConfig().getBoolean("portals.keys.consume", true));
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
        de.lamamc.sift.world.portal.SiftCompass compass = new de.lamamc.sift.world.portal.SiftCompass(this::shrineSites);
        if (getConfig().getBoolean("phase4.temples.compass.enabled", true)) {
            // temples attach later (with the Sift world); until then and while they are off the supplier gives null
            compass.temples(() -> temples == null ? null : here -> {
                var site = temples.nearestOpenSite(here.getX(), here.getZ(), null);
                return site == null ? null : new org.bukkit.Location(here.getWorld(), site.x(), temples.floorY(site, here.getBlockY()), site.z());
            });
            getServer().getPluginManager().registerEvents(compass, this);
        }
        compass.start(this, getConfig().getInt("portals.compass.update-ticks", 40));
        registerTempleRumors();
        gate = new de.lamamc.sift.world.access.SiftGate(this, messages, getConfig().getConfigurationSection("access"));
        getServer().getPluginManager().registerEvents(gate, this);
        portals.setGate(gate);
        org.bukkit.configuration.ConfigurationSection schedule = getConfig().getConfigurationSection("portals.unlock-schedule");
        if (schedule != null && schedule.getBoolean("enabled", true)) {
            java.util.Map<de.lamamc.sift.core.portal.PortalSchedule.Group, Double> days = new java.util.EnumMap<>(
                    de.lamamc.sift.core.portal.PortalSchedule.Group.class);
            for (var g : de.lamamc.sift.core.portal.PortalSchedule.Group.values()) {
                days.put(g, Math.max(0, schedule.getDouble(g.key(), 0)));
            }
            unlocks = new de.lamamc.sift.world.portal.PortalUnlocks(this, gate, new de.lamamc.sift.core.portal.PortalSchedule.Settings(true, days));
            portals.setUnlocks(unlocks);
            unlocks.start();
        }
        gate.start();
        if (getConfig().getBoolean("ichor.enabled", true)) {
            if (getConfig().getBoolean("ichor.identity.enabled", true)) {
                fluidMarks = new de.lamamc.sift.world.ichor.FluidMarks();
                getServer().getPluginManager().registerEvents(fluidMarks, this);
                fluidMarks.start();
                fluidIdentity = new de.lamamc.sift.world.ichor.FluidIdentityListener(this, fluidMarks, items);
                getServer().getPluginManager().registerEvents(fluidIdentity, this);
            }
            de.lamamc.sift.world.ichor.IchorListener ichor = new de.lamamc.sift.world.ichor.IchorListener(
                    this, items, blocks, fluidMarks, getConfig().getConfigurationSection("ichor"));
            getServer().getPluginManager().registerEvents(ichor, this);
            ichor.start();
        }
        if (getConfig().getBoolean("phase4.enchanter.enabled", true)) {
            enchanters = new de.lamamc.sift.world.enchanter.SculkEnchanters(this, items, blocks,
                    getConfig().getConfigurationSection("phase4.enchanter"));
            getServer().getPluginManager().registerEvents(enchanters, this);
            getServer().getPluginManager().registerEvents(de.lamamc.sift.world.enchanter.EnchanterMenu.listener(), this);
            enchanters.start();
        }
        if (getConfig().getBoolean("phase4.boss-items.enabled", true)) {
            getServer().getPluginManager().registerEvents(new de.lamamc.sift.world.boss.BossDropListener(items,
                    getConfig().getConfigurationSection("phase4.boss-items")), this);
        }
        intro = new de.lamamc.sift.world.atmosphere.SiftIntro(getConfig().getConfigurationSection("atmosphere.intro-sound"));
        intro.start(this);
        allays = new de.lamamc.sift.mob.allay.AllaySpawner(getConfig().getConfigurationSection("mobs.allay"));
        allays.start(this);
        playerReset = new de.lamamc.sift.world.admin.PlayerReset(getConfig().getBoolean("admin.reset-players.enabled", false), getLogger());
        playerReset.listen(this);
        weather = new de.lamamc.sift.world.weather.SiftWeather(getConfig().getConfigurationSection("weather"));
        getServer().getPluginManager().registerEvents(weather, this);
        if (getConfig().getBoolean("rifts.enabled", false)) {
            rifts = new de.lamamc.sift.world.rift.RiftManager(this, blocks, messages, advancements, gate, getConfig().getConfigurationSection("rifts"));
            getServer().getPluginManager().registerEvents(rifts, this);
            rifts.start();
        } else {
            // alpha.42a.2: no rifts at all; only what earlier rifts left behind is cleaned up (the mini rift of the
            // Siftite keeper below is separate and stays)
            de.lamamc.sift.world.rift.RiftCleanup cleanup = new de.lamamc.sift.world.rift.RiftCleanup(this);
            getServer().getPluginManager().registerEvents(cleanup, this);
            cleanup.start();
            getLogger().info("Rifts are off (rifts.enabled: false); Siftite keep-on-death and its mini rift are independent of this");
        }
        if (getConfig().getBoolean("siftite.keep-on-death.enabled", true)) {
            siftiteKeeper = new de.lamamc.sift.world.siftite.SiftiteKeeper(this, messages, advancements,
                    getConfig().getConfigurationSection("siftite.keep-on-death"));
            getServer().getPluginManager().registerEvents(siftiteKeeper, this);
            siftiteKeeper.start();
        }
        if (getConfig().getBoolean("portals.ancient-city.enabled", true)) {
            de.lamamc.sift.world.portal.AncientCityPortals cities = new de.lamamc.sift.world.portal.AncientCityPortals(this, blocks,
                    portals, getConfig().getInt("portals.frame-radius", 10), getLogger());
            getServer().getPluginManager().registerEvents(cities, this);
            cities.start();
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
        mobs.names(id -> messages.has("mobs.names." + id) ? messages.raw(de.lamamc.sift.core.lang.Lang.EN, "mobs.names." + id) : null);
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
        org.bukkit.configuration.ConfigurationSection wisp = config.getConfigurationSection("charoite_wisp");
        mobs.register(new de.lamamc.sift.mob.wisp.CharoiteWispKind(wisp != null ? wisp : new org.bukkit.configuration.MemoryConfiguration(),
                items));
        org.bukkit.configuration.ConfigurationSection golem = config.getConfigurationSection("echo_golem");
        if (golem == null) {
            golem = new org.bukkit.configuration.MemoryConfiguration();
        }
        de.lamamc.sift.mob.echogolem.EchoGolemKind golemKind = new de.lamamc.sift.mob.echogolem.EchoGolemKind(golem, items, blocks,
                () -> canyonLandmarks);
        golemKind.messages(messages);
        mobs.register(golemKind);
        golemLight = new de.lamamc.sift.mob.echogolem.GolemLight(this, golem, getLogger());
        golemKind.light(golemLight);
        getServer().getPluginManager().registerEvents(golemLight, this);
        golemLight.cleanLoaded();
        golemKind.singers(new de.lamamc.sift.mob.singer.SoulSingers(mobs, singerKind, () -> canyonLandmarks));
        golemSpawner = new de.lamamc.sift.mob.echogolem.EchoGolemSpawner(mobs, golemKind, () -> canyonLandmarks);
        golemSpawner.start(this);
        org.bukkit.configuration.ConfigurationSection boss = getConfig().getConfigurationSection("phase4.bosses.great-echo-golem");
        if (boss == null) {
            boss = new org.bukkit.configuration.MemoryConfiguration();
        }
        bossKind = new de.lamamc.sift.mob.boss.GreatEchoGolemKind(boss, items);
        mobs.register(bossKind);
        org.bukkit.configuration.ConfigurationSection helper = boss.getConfigurationSection("helper");
        helperKind = new de.lamamc.sift.mob.boss.SoulHelperKind(helper != null ? helper : new org.bukkit.configuration.MemoryConfiguration());
        mobs.register(helperKind);
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

    /** Sculk Enchanters (alpha.40), null when phase4.enchanter.enabled is false. */
    public de.lamamc.sift.world.enchanter.SculkEnchanters enchanters() {
        return enchanters;
    }

    /** Ichor identity marks (alpha.38), null when ichor.identity.enabled is false. */
    public de.lamamc.sift.world.ichor.FluidMarks fluidMarks() {
        return fluidMarks;
    }

    public de.lamamc.sift.world.ichor.FluidIdentityListener fluidIdentity() {
        return fluidIdentity;
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

    /**
     * New settings of an update land in the existing config.yml (alpha.37: without merging into recipes the admin
     * already has; values still at an old default are replaced). The merged file is read back, because sections that
     * only exist in the defaults show up empty in memory until then.
     */
    private void updateConfig() {
        org.bukkit.configuration.file.FileConfiguration config = getConfig();
        // alpha.37: recipes and loot chances; alpha.42c: single German texts become de/en sections
        for (String version : List.of("alpha37", "alpha42c", "alpha42d", "alpha42f")) {
            java.io.InputStream migration = getResource("migrations/" + version + ".yml");
            if (migration != null && config.getDefaults() != null) {
                org.bukkit.configuration.file.YamlConfiguration old = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(migration, java.nio.charset.StandardCharsets.UTF_8));
                List<String> replaced = ConfigUpdate.dropOldDefaults(config, old);
                if (!replaced.isEmpty()) {
                    getLogger().info("config.yml: old defaults replaced by the new ones (" + version + "): " + replaced);
                }
            }
        }
        if (config.getDefaults() != null) {
            ConfigUpdate.addMissing(config, config.getDefaults());
        }
        saveConfig();
        reloadConfig();
    }

    /** World key -> id of the key that opens its shrine portals (portals.keys; the Ancient City key is separate). */
    private java.util.Map<String, String> portalKeys() {
        java.util.Map<String, String> keys = new java.util.HashMap<>();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("portals.keys");
        if (section != null) {
            for (String dimension : section.getKeys(false)) {
                if (!section.isConfigurationSection(dimension) || dimension.equals("ancient-city")) {
                    continue; // enabled, consume, the Ancient City key
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
            de.lamamc.sift.world.gen.IchorWaters waters = new de.lamamc.sift.world.gen.IchorWaters(
                    de.lamamc.sift.world.gen.IchorWaters.Settings.load(getConfig().getConfigurationSection("worldgen")));
            waters.load(world);
            waters.listen(this);
            siftPopulator = new SiftPopulator(blocks, new PlantSupport(blockSettings, blocks), worldgen, templates, reservation,
                    landmarkLog, canyons, waters, getLogger());
            siftPopulator.snow(new de.lamamc.sift.world.gen.IchorSnow(getConfig().getBoolean("worldgen.ichor-snow.enabled", true),
                    getConfig().getBoolean("worldgen.ichor-snow.willow-crowns", true)));
            de.lamamc.sift.world.gen.Landforms landforms = new de.lamamc.sift.world.gen.Landforms(
                    de.lamamc.sift.world.gen.Landforms.Settings.load(getConfig().getConfigurationSection("worldgen")));
            landforms.load(world);
            siftPopulator.landforms(landforms);
            de.lamamc.sift.world.gen.SnifferCaves snifferCaves = new de.lamamc.sift.world.gen.SnifferCaves(
                    de.lamamc.sift.world.gen.SnifferCaves.Settings.load(getConfig().getConfigurationSection("worldgen")),
                    waters.settings().lightSpacing());
            snifferCaves.load(world);
            snifferCaves.start(this);
            siftPopulator.snifferCaves(snifferCaves);
            siftPopulator.topLayer(new de.lamamc.sift.world.gen.TopLayer(getConfig().getBoolean("worldgen.covered-growth-cleanup.enabled", true),
                    Math.max(0, getConfig().getInt("worldgen.covered-growth-cleanup.depth", 16)), getConfig().getBoolean("worldgen.lava-floor.enabled", true), getConfig().getInt("worldgen.lava-floor.max-y", 11)));
            // worldgen threads record, the main thread writes into the world's persistent data
            getServer().getScheduler().runTaskTimer(this, () -> {
                landmarkLog.save(world);
                canyonLandmarks.save(world);
                waters.save(world);
                landforms.save(world);
                snifferCaves.save(world);
            }, 100L, 100L);
            attachTemples(world, reservation);
            world.getPopulators().add(siftPopulator);
            getLogger().info("Sift worldgen attached to " + world.getKey() + " (" + templates.size() + " templates)");
        }
    }

    /**
     * Temples (phase 4, alpha.41a, phase4.temples): exactly N sites on a ring around 0/0, computed from the seed and the
     * world border before the first chunk; other features keep out of them.
     */
    private void attachTemples(World world, PortalReservation reservation) {
        org.bukkit.configuration.ConfigurationSection c = getConfig().getConfigurationSection("phase4.temples");
        if (c == null || !c.getBoolean("enabled", true)) {
            return;
        }
        var ring = new de.lamamc.sift.core.temple.TempleSites.Ring(Math.max(0, c.getInt("count", 10)),
                Math.max(0, c.getInt("ring.min-radius", 1500)), Math.max(0, c.getInt("ring.max-radius", 3000)),
                c.getDouble("ring.angle-jitter", 15));
        var params = new de.lamamc.sift.core.temple.TempleLayout.Params(c.getInt("size.radius", 40), c.getInt("size.wall-height", 14),
                c.getInt("size.tower-height", 26), c.getInt("size.depth", 16));
        int clearance = Math.max(0, c.getInt("clearance", 8));
        double border = world.getWorldBorder().getSize() / 2;
        int reach = params.extent() + 8;
        int[] radii = de.lamamc.sift.core.temple.TempleSites.effectiveRadii(ring, border, reach + clearance + 16);
        if (radii[1] < ring.maxRadius()) {
            getLogger().warning("Sift world border (" + (int) border + " blocks from the centre) is smaller than the temple ring; "
                    + "ring pulled in to " + radii[0] + "-" + radii[1]);
        }
        var sites = de.lamamc.sift.core.temple.TempleSites.sites(world.getSeed(), ring, 0, 0, border, reach + clearance + 16);
        double closest = de.lamamc.sift.core.temple.TempleSites.minDistance(sites);
        if (closest < 2 * (reach + clearance)) {
            getLogger().warning("Two temple sites are only " + (int) closest + " blocks apart (a temple needs " + (reach + clearance)
                    + " around it); raise phase4.temples.ring.max-radius or lower phase4.temples.size.radius or count");
        }
        org.bukkit.NamespacedKey lootKey = org.bukkit.NamespacedKey.fromString(c.getString("loot-table", "the_sift:chests/temple"));
        var builder = new de.lamamc.sift.world.temple.TempleBuilder(world.getSeed(), sites, params,
                lootKey == null ? null : getServer().getLootTable(lootKey), blocks);
        getServer().getScheduler().runTaskAsynchronously(this, builder::prepare);
        reservation.reserveTemples(builder.reservedAreas(clearance));
        siftPopulator.temples(builder);
        temples = new de.lamamc.sift.world.temple.TempleRegistry(this, blocks, builder, c.getBoolean("protect-until-defeated", true),
                getConfig().getBoolean("phase4.enchanter.temple-enchanter.requires-defeat", true),
                LocalizedConfig.text(c, "altar-message", "Der Seelen-Altar schweigt noch.", "The Soul Altar is still silent."),
                c.getInt("deferred-per-tick", 1));
        temples.altarOnePerPlayer(c.getBoolean("altar-one-per-player.enabled", true));
        getServer().getPluginManager().registerEvents(temples, this);
        temples.start(world);
        startBroodSpawners(c.getConfigurationSection("spawners"));
        startBosses();
        if (enchanters != null) {
            temples.onEnchanter(b -> {
                if (enchanters.isEnchanter(b)) {
                    enchanters.mark(b, true);
                }
            });
            enchanters.setLock(temples::lockReason);
        }
        getLogger().info("Temples: " + sites.size() + " sites on the ring " + radii[0] + "-" + radii[1] + " around 0/0, "
                + (2 * params.radius() + 1) + " x " + (2 * params.radius() + 1) + " blocks, closest two " + (int) closest + " apart");
    }

    /** Brood spawners in the temples (alpha.42a, phase4.temples.spawners). */
    private void startBroodSpawners(org.bukkit.configuration.ConfigurationSection s) {
        if (s == null || !s.getBoolean("enabled", true) || mobs == null) {
            temples.altarGate(false);
            return;
        }
        java.util.Map<Integer, de.lamamc.sift.core.temple.BroodSpawner.Zone> zones = new java.util.HashMap<>();
        for (int z = 1; z <= 3; z++) {
            org.bukkit.configuration.ConfigurationSection zone = s.getConfigurationSection("zones." + z);
            if (zone == null || !zone.getBoolean("enabled", true)) {
                continue;
            }
            java.util.List<String> kinds = zone.getStringList("mobs").stream().filter(id -> {
                boolean known = mobs.kind(id) != null;
                if (!known) {
                    getLogger().warning("phase4.temples.spawners.zones." + zone.getName() + ".mobs: unknown or disabled Sift mob " + id);
                }
                return known;
            }).toList();
            if (!kinds.isEmpty()) {
                zones.put(z, new de.lamamc.sift.core.temple.BroodSpawner.Zone(zone.getInt("waves", 2), zone.getInt("mobs-per-wave", 2),
                        zone.getInt("per-extra-player", 1), s.getInt("max-players-scaled", 4), kinds));
            }
        }
        brood = new de.lamamc.sift.world.temple.BroodSpawners(this, temples, mobs, new de.lamamc.sift.world.temple.BroodSpawners.Settings(
                Math.max(1, s.getDouble("activation-radius", 12)), Math.max(8, s.getDouble("despawn-radius", 48)),
                Math.max(5, s.getLong("despawn-seconds", 30)) * 1000L, Math.max(1, s.getInt("max-alive-per-temple", 16)),
                new de.lamamc.sift.core.temple.BroodSpawner.Timing(Math.max(0, s.getLong("wave-delay-seconds", 5)) * 1000L,
                        Math.max(0, s.getLong("cooldown-minutes", 30)) * 60_000L),
                zones, Math.max(0, s.getDouble("experience-factor", 0)), s.getBoolean("drops", false)));
        getServer().getPluginManager().registerEvents(brood, this);
        brood.start();
        temples.altarGate(s.getBoolean("altar-gate", true));
        getLogger().info("Brood spawners: " + temples.spawners().size() + " per temple, zones " + zones.keySet());
    }

    private de.lamamc.sift.world.temple.BroodSpawners brood;
    private de.lamamc.sift.mob.boss.GreatEchoGolemKind bossKind;
    private de.lamamc.sift.mob.boss.SoulHelperKind helperKind;
    private de.lamamc.sift.world.temple.TempleBosses bosses;

    /** The Great Echo Golem in the temple arenas (alpha.42b, phase4.bosses.great-echo-golem). */
    private void startBosses() {
        org.bukkit.configuration.ConfigurationSection c = getConfig().getConfigurationSection("phase4.bosses.great-echo-golem");
        if (bossKind == null || helperKind == null || c == null || !c.getBoolean("enabled", true)) {
            getLogger().info("Temple boss is off (phase4.bosses.great-echo-golem.enabled or mobs.enabled); the altars stay asleep");
            return;
        }
        bosses = new de.lamamc.sift.world.temple.TempleBosses(this, temples, mobs, bossKind, helperKind, items,
                de.lamamc.sift.world.temple.TempleBosses.Settings.of(c));
        getServer().getPluginManager().registerEvents(bosses, this);
        bosses.start();
        var s = bossKind.settings();
        getLogger().info(String.format(java.util.Locale.ROOT, "Temple boss: Great Echo Golem, %.0f health (+%.0f %% per extra player, up to %d), "
                + "%d plates, offering %d %s", s.baseHealth(), s.perExtraPlayer() * 100, s.maxPlayersScaled(), s.plates(),
                bosses.settings().offeringAmount(), bosses.settings().offeringItem()));
    }

    /** Null while the temple boss is off. */
    public de.lamamc.sift.world.temple.TempleBosses bosses() {
        return bosses;
    }

    /** Brood spawner runtime (alpha.42a), null when off. */
    public de.lamamc.sift.world.temple.BroodSpawners brood() {
        return brood;
    }

    /** Rumour notes about the temples in chests (alpha.41b, phase4.temples.hints). */
    private void registerTempleRumors() {
        org.bukkit.configuration.ConfigurationSection h = getConfig().getConfigurationSection("phase4.temples.hints");
        if (h == null || !h.getBoolean("enabled", true)) {
            return;
        }
        java.util.Map<String, Double> chances = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection c = h.getConfigurationSection("chests");
        if (c != null) {
            // loot table keys contain ':' and '/', so the section holds them as plain keys
            for (String table : c.getKeys(false)) {
                chances.put(table, Math.max(0, Math.min(1, c.getDouble(table))));
            }
        }
        getServer().getPluginManager().registerEvents(new de.lamamc.sift.world.temple.TempleRumors(() -> temples, items,
                new de.lamamc.sift.world.temple.TempleRumors.Settings(chances, Math.max(1, h.getInt("distance-step", 100)),
                        LocalizedConfig.lines(h, "text"), LocalizedConfig.lines(h, "all-defeated"))), this);
    }

    /** Temples (alpha.41a), null when phase4.temples.enabled is false or the Sift is not attached yet. */
    public de.lamamc.sift.world.temple.TempleRegistry temples() {
        return temples;
    }

    @Override
    public void onDisable() {
        World sift = getServer().getWorld(SiftWorlds.DIMENSION);
        if (siftPopulator != null && sift != null) {
            siftPopulator.log().save(sift);
            canyonLandmarks.save(sift);
            siftPopulator.waters().save(sift);
            siftPopulator.landforms().save(sift);
            siftPopulator.snifferCaves().save(sift);
        }
        if (blocks != null) {
            getServer().getOnlinePlayers().forEach(BreakSpeedListener::clearAll);
        }
        if (fluidMarks != null) {
            fluidMarks.flushAll();
        }
        if (enchanters != null) {
            enchanters.removeAll();
        }
        if (rifts != null) {
            rifts.removeAll();
        }
        if (siftiteKeeper != null) {
            siftiteKeeper.stopAll(); // the saved lists stay in the player data
        }
        if (golemLight != null) {
            golemLight.removeAll(); // before the golems detach: no block changes are scheduled any more
        }
        if (bosses != null) {
            bosses.stop(); // the golem is never saved: every fight is called off, the temples sleep again
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

    /** Null when siftite.keep-on-death.enabled is false. */
    public de.lamamc.sift.world.siftite.SiftiteKeeper siftiteKeeper() {
        return siftiteKeeper;
    }

    public de.lamamc.sift.world.admin.PlayerReset playerReset() {
        return playerReset;
    }

    public de.lamamc.sift.world.weather.SiftWeather weather() {
        return weather;
    }

    public de.lamamc.sift.mob.allay.AllaySpawner allays() {
        return allays;
    }

    public de.lamamc.sift.world.atmosphere.SiftIntro intro() {
        return intro;
    }

    private de.lamamc.sift.world.portal.PortalUnlocks unlocks;

    /** Portal unlock schedule (alpha.42a.2), null when portals.unlock-schedule.enabled is false. */
    public de.lamamc.sift.world.portal.PortalUnlocks unlocks() {
        return unlocks;
    }

    /** Null when rifts.enabled is false. */
    public de.lamamc.sift.world.rift.RiftManager rifts() {
        return rifts;
    }

    public de.lamamc.sift.world.access.SiftGate gate() {
        return gate;
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
