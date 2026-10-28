package de.lamamc.sift.bootstrap;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;

/** Runs before the server loads registries and worlds: installs the datapack with the Sift dimension. */
public final class SiftBootstrap implements PluginBootstrap {

    static final String PACK_ID = "the_sift";

    @Override
    public void bootstrap(BootstrapContext context) {
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
}
