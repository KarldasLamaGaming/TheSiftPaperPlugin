package de.lamamc.sift.world.weather;

import de.lamamc.sift.core.atmosphere.SnowRule;
import de.lamamc.sift.world.SiftWorlds;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Snow;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * Weather in the Sift (alpha.32, config weather.*): snow and ice that the vanilla weather forms in the Sift go
 * through {@link SnowRule}. Snowfall only in ichor_snowy_peaks (whose temperature the bootstrapper lowers so
 * vanilla snows there at all heights), block light below 10, at most 2 layers; ichor does not freeze.
 */
public final class SiftWeather implements Listener {

    public final AtomicLong snowFormed = new AtomicLong();
    public final AtomicLong snowRefused = new AtomicLong();
    public final AtomicLong iceRefused = new AtomicLong();
    /** Why snow was refused: the biome, "Licht" or "Schichten". */
    public final java.util.Map<String, Integer> refusals = new java.util.concurrent.ConcurrentHashMap<>();
    private final boolean snowfall;
    private final int maxLayers;
    private final boolean ichorFreezes;

    public SiftWeather(ConfigurationSection weather) {
        ConfigurationSection w = weather == null ? new org.bukkit.configuration.MemoryConfiguration() : weather;
        this.snowfall = w.getBoolean("ichor-snowfall.enabled", true);
        this.maxLayers = Math.max(1, Math.min(8, w.getInt("ichor-snowfall.max-layers", 2)));
        this.ichorFreezes = w.getBoolean("ichor-freezes", false);
    }

    public boolean snowfall() {
        return snowfall;
    }

    public int maxLayers() {
        return maxLayers;
    }

    public boolean ichorFreezes() {
        return ichorFreezes;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (!SiftWorlds.isSift(block.getWorld())) {
            return;
        }
        Material formed = event.getNewState().getType();
        if (formed == Material.ICE && !SnowRule.freeze(ichorFreezes)) {
            event.setCancelled(true);
            iceRefused.incrementAndGet();
            return;
        }
        if (formed != Material.SNOW || !snowfall) {
            return; // with ichor-snowfall off the vanilla rules apply unchanged
        }
        int layers = event.getNewState().getBlockData() instanceof Snow snow ? snow.getLayers() : 1;
        // vanilla precipitation uses the zoomed biome (BiomeManager), which getComputedBiome returns; getBiome is the raw noise biome
        var key = block.getWorld().getComputedBiome(block.getX(), block.getY(), block.getZ()).getKey();
        String biome = key.getNamespace().equals("the_sift") ? key.getKey() : key.toString();
        if (SnowRule.snow(biome, block.getLightFromBlocks(), layers, maxLayers)) {
            snowFormed.incrementAndGet();
        } else {
            event.setCancelled(true);
            snowRefused.incrementAndGet();
            String why = !SnowRule.BIOME.equals(biome) ? biome : layers > maxLayers ? "Schichten" : "Licht";
            refusals.merge(why, 1, Integer::sum);
        }
    }
}
