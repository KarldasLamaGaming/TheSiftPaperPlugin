package de.lamamc.sift.world;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Test populator for the smoke test: counts newly generated chunks of the Sift and marks each one with
 * glowstone on the highest block of the chunk center. Proves that plugin populators run in the
 * datapack world, which the worldgen ports rely on.
 */
public final class DebugPopulator extends BlockPopulator {

    private final Logger logger;
    private final int logInterval;
    private final AtomicLong chunks = new AtomicLong();
    private final AtomicLong markers = new AtomicLong();
    private final BlockData marker = Bukkit.createBlockData(Material.GLOWSTONE);

    public DebugPopulator(Logger logger, int logInterval) {
        this.logger = logger;
        this.logInterval = Math.max(1, logInterval);
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int y = region.getHighestBlockYAt(x, z) + 1;
        if (y > worldInfo.getMinHeight() && y < worldInfo.getMaxHeight() && region.isInRegion(x, y, z)) {
            region.setBlockData(x, y, z, marker);
            markers.incrementAndGet();
        }
        long count = chunks.incrementAndGet();
        if (count % logInterval == 0) {
            logger.info("Debug populator: " + count + " Sift chunks populated, " + markers.get() + " markers");
        }
    }

    public long chunks() {
        return chunks.get();
    }

    public long markers() {
        return markers.get();
    }
}
