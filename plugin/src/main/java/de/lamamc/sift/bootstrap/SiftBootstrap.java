package de.lamamc.sift.bootstrap;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Runs before the server loads registries and worlds: installs the datapack with the Sift dimension and
 * prepares the Paper world config of that dimension.
 */
public final class SiftBootstrap implements PluginBootstrap {

    static final String PACK_ID = "the_sift";

    @Override
    public void bootstrap(BootstrapContext context) {
        Path dataDir = context.getDataDirectory();
        YamlConfiguration config = loadConfig(context, dataDir);

        Path packDir = dataDir.resolve("datapack");
        try {
            int files = DatapackInstaller.install(context.getPluginSource(), packDir);
            context.getLogger().info("Datapack installed to {} ({} files)", packDir, files);
        } catch (IOException e) {
            throw new IllegalStateException("Could not install the Sift datapack", e);
        }

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            DatapackRegistrar registrar = event.registrar();
            try {
                registrar.discoverPack(packDir, PACK_ID, configurer -> configurer
                        .title(Component.text("The Sift"))
                        .autoEnableOnServerStart(true));
            } catch (IOException e) {
                throw new IllegalStateException("Could not register the Sift datapack", e);
            }
        });

        prepareWorldConfig(context, dataDir, config);
    }

    private static void prepareWorldConfig(BootstrapContext context, Path dataDir, YamlConfiguration config) {
        // plugins/TheSift -> server root
        Path serverRoot = dataDir.toAbsolutePath().getParent().getParent();
        try {
            Path worldFolder = SiftWorldConfig.worldFolder(serverRoot, config.getString("server-config.world-folder"));
            Path file = SiftWorldConfig.configFile(worldFolder);
            // Paper treats an existing dimension folder as an existing world and then requires its
            // world_gen_settings.dat, so the folder must not be created here before the first start.
            // In that case the plugin writes the switches once Paper has created the world (restart needed).
            if (!Files.isDirectory(file.getParent())) {
                context.getLogger().info("The Sift world does not exist yet; its Paper world config is written after creation");
                return;
            }
            if (config.getBoolean("server-config.manage-sift-world-config", true)) {
                List<String> changed = SiftWorldConfig.ensureSwitches(file);
                if (!changed.isEmpty()) {
                    context.getLogger().info("Set {} in {}", changed, file);
                }
            } else {
                List<String> missing = SiftWorldConfig.missingSwitches(file);
                if (!missing.isEmpty()) {
                    context.getLogger().warn("The Sift needs {} = true in {}; custom blocks will change shape without it",
                            missing, file);
                }
            }
        } catch (IOException e) {
            context.getLogger().error("Could not prepare the Paper world config of the Sift", e);
        }
    }

    /** The bootstrap runs before the plugin instance exists, so it reads config.yml itself. */
    private static YamlConfiguration loadConfig(BootstrapContext context, Path dataDir) {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStream defaults = SiftBootstrap.class.getResourceAsStream("/config.yml")) {
            if (defaults != null) {
                config.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8)));
            }
            Path file = dataDir.resolve("config.yml");
            if (Files.isRegularFile(file)) {
                config.loadFromString(Files.readString(file));
            }
        } catch (Exception e) {
            context.getLogger().error("Could not read config.yml, using defaults", e);
        }
        return config;
    }
}
