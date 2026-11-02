package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.world.SiftWorlds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * Souls rising out of the canyons (SiftSoulCanyonParticles of the original): every second tick one canyon
 * in turn, if its chunk is loaded, sends one rising soul particle above an open soul block (50 blocks of air
 * above it). The original's own canyon_soul particle is a client particle; vanilla SOUL looks the same.
 */
public final class SoulCanyonParticles {

    private record Cached(SoulCanyonLandmarks.Pos source, long validUntil) {
    }

    private final SoulCanyonLandmarks landmarks;
    private final CustomBlockRegistry registry;
    private final Map<SoulCanyonLandmarks.Pos, Cached> cache = new HashMap<>();
    private List<SoulCanyonLandmarks.Pos> clusters = List.of();
    private int refresh;
    private int cursor;

    public SoulCanyonParticles(SoulCanyonLandmarks landmarks, CustomBlockRegistry registry) {
        this.landmarks = landmarks;
        this.registry = registry;
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 2L);
    }

    private void tick() {
        World world = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (world == null) {
            return;
        }
        if (--refresh <= 0) {
            refresh = 200; // 400 ticks at one run every 2 ticks
            clusters = landmarks.all(SoulCanyonLandmarks.Kind.CLUSTER);
            if (cursor >= clusters.size()) {
                cursor = 0;
            }
        }
        if (clusters.isEmpty()) {
            return;
        }
        SoulCanyonLandmarks.Pos center = clusters.get(cursor++);
        if (cursor >= clusters.size()) {
            cursor = 0;
        }
        if (!world.isChunkLoaded(center.x() >> 4, center.z() >> 4)) {
            return;
        }
        SoulCanyonLandmarks.Pos source = openSoulBlock(world, center);
        if (source != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            world.spawnParticle(Particle.SOUL, source.x() - 1.75 + random.nextDouble() * 4.5, source.y() + 0.95,
                    source.z() - 1.75 + random.nextDouble() * 4.5, 1, 0.04, 0.01, 0.04, 0.0);
        }
    }

    private boolean isSoul(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        CustomBlock custom = registry.of(world.getBlockData(x, y, z));
        return custom != null && SoulCanyonLandmarks.SOUL_BLOCK_ID.equals(custom.id());
    }

    private SoulCanyonLandmarks.Pos openSoulBlock(World world, SoulCanyonLandmarks.Pos center) {
        long now = world.getGameTime();
        Cached cached = cache.get(center);
        if (cached != null && cached.validUntil() > now
                && (cached.source() == null || isSoul(world, cached.source().x(), cached.source().y(), cached.source().z()))) {
            return cached.source();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<SoulCanyonLandmarks.Pos> indexed = landmarks.near(SoulCanyonLandmarks.Kind.SOUL_BLOCK, center.x() + 0.5, center.y(),
                center.z() + 0.5, 10, 8);
        indexed.removeIf(pos -> !isSoul(world, pos.x(), pos.y(), pos.z()));
        if (!indexed.isEmpty()) {
            int start = random.nextInt(indexed.size());
            for (int offset = 0; offset < Math.min(indexed.size(), 12); offset++) {
                SoulCanyonLandmarks.Pos candidate = indexed.get((start + offset) % indexed.size());
                if (open(world, candidate)) {
                    cache.put(center, new Cached(candidate, now + 6));
                    return candidate;
                }
            }
            cache.put(center, new Cached(null, now + 20));
            return null;
        }
        if (!landmarks.near(SoulCanyonLandmarks.Kind.SOUL_BLOCK, center.x() + 0.5, center.y(), center.z() + 0.5, 12, 10).isEmpty()) {
            cache.put(center, new Cached(null, now + 40));
            return null;
        }
        for (int attempt = 0; attempt < 48; attempt++) {
            int x = center.x() + random.nextInt(17) - 8;
            int y = center.y() + random.nextInt(11) - 5;
            int z = center.z() + random.nextInt(17) - 8;
            SoulCanyonLandmarks.Pos pos = new SoulCanyonLandmarks.Pos(x, y, z);
            if (isSoul(world, x, y, z) && open(world, pos)) {
                cache.put(center, new Cached(pos, now + 40));
                return pos;
            }
        }
        cache.put(center, new Cached(null, now + 20));
        return null;
    }

    private static boolean open(World world, SoulCanyonLandmarks.Pos source) {
        for (int above = 1; above <= 50; above++) {
            int y = source.y() + above;
            if (y >= world.getMaxHeight()) {
                return true;
            }
            if (!world.getBlockAt(source.x(), y, source.z()).getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
