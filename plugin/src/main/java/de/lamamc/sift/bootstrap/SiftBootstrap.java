package de.lamamc.sift.bootstrap;

import de.lamamc.sift.core.VersionCheck;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import net.kyori.adventure.text.Component;

/** Runs before the server loads registries and worlds: installs the datapack with the Sift dimension. */
public final class SiftBootstrap implements PluginBootstrap {

    static final String PACK_ID = "the_sift";

    /** Set when the server version does not fit; the plugin then disables itself in onEnable. */
    private static volatile String incompatibility;

    public static String incompatibility() {
        return incompatibility;
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        String builtFor = builtForMinecraft();
        String running = ServerBuildInfo.buildInfo().minecraftVersionId();
        if (!VersionCheck.compatible(builtFor, running)) {
            // without the datapack the server starts normally, just without the Sift
            incompatibility = "TheSift " + context.getConfiguration().getVersion() + " is built for Minecraft "
                    + builtFor + ", but this server runs " + running + ". The Sift datapack was NOT loaded and "
                    + "the plugin stays disabled. Use the TheSift release made for " + running
                    + ", or run Paper " + builtFor + ".";
            context.getLogger().error(incompatibility);
            return;
        }
        Path packDir = context.getDataDirectory().resolve("datapack");
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
    }

    private static String builtForMinecraft() {
        Properties props = new Properties();
        try (InputStream in = SiftBootstrap.class.getResourceAsStream("/sift-build.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            return null;
        }
        return props.getProperty("minecraft");
    }
}
