package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.world.SiftWorlds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Souls rising out of the canyons (SiftSoulCanyonParticles of the original). Every {@code interval} ticks,
 * each canyon with a player within {@code view-distance} and a loaded chunk sends {@code amount} particles
 * above a few of its open soul blocks (soul blocks with {@code air-above} passable blocks above them), only to
 * those players. The original needs 50 blocks of air and sends one particle per tick for one canyon; with its
 * own client particle that is visible, with a vanilla particle it is not, hence the configurable values.
 */
public final class SoulCanyonParticles {

    /** worldgen.soul-canyons.particles; the old form "particles: true/false" still works. */
    public record Settings(boolean enabled, Particle type, int amount, int soulsPerRun, int airAbove, double viewDistance,
                           int intervalTicks) {

        public static Settings defaults(boolean enabled) {
            return new Settings(enabled, Particle.SCULK_SOUL, 3, 4, 4, 48, 10);
        }

        public static Settings load(ConfigurationSection canyons) {
            if (canyons == null) {
                return defaults(true);
            }
            if (!canyons.isConfigurationSection("particles")) {
                return defaults(canyons.getBoolean("particles", true));
            }
            ConfigurationSection p = canyons.getConfigurationSection("particles");
            Settings d = defaults(true);
            Particle type = d.type();
            try {
                Particle configured = Particle.valueOf(p.getString("type", d.type().name()).toUpperCase(Locale.ROOT));
                if (configured.getDataType() == Void.class) {
                    type = configured;
                }
            } catch (IllegalArgumentException ignored) {
                // unknown name: keep the default
            }
            return new Settings(p.getBoolean("enabled", true), type, Math.clamp(p.getInt("amount", d.amount()), 1, 20),
                    Math.clamp(p.getInt("souls-per-run", d.soulsPerRun()), 1, 16), Math.clamp(p.getInt("air-above", d.airAbove()), 1, 64),
                    Math.clamp(p.getDouble("view-distance", d.viewDistance()), 8, 128), Math.clamp(p.getInt("interval-ticks",
                    d.intervalTicks()), 1, 100));
        }
    }

    /** Counters for /sift debug landmarks. */
    public static final class Stats {
        public long runs;
        public int lastCanyons;
        public int lastOpenSouls;
        public int lastParticles;
        public long particles;
    }

    private record Sources(List<SoulCanyonLandmarks.Pos> open, int souls, long validUntil) {
    }

    private final SoulCanyonLandmarks landmarks;
    private final CustomBlockRegistry registry;
    private final Settings settings;
    private final Stats stats = new Stats();
    private final Map<SoulCanyonLandmarks.Pos, Sources> cache = new HashMap<>();

    public SoulCanyonParticles(SoulCanyonLandmarks landmarks, CustomBlockRegistry registry, Settings settings) {
        this.landmarks = landmarks;
        this.registry = registry;
        this.settings = settings;
    }

    public Settings settings() {
        return settings;
    }

    public Stats stats() {
        return stats;
    }

    public void start(Plugin plugin) {
        if (settings.enabled()) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, settings.intervalTicks());
        }
    }

    private void tick() {
        World world = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        stats.runs++;
        int canyons = 0;
        int open = 0;
        int sent = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (SoulCanyonLandmarks.Pos cluster : landmarks.all(SoulCanyonLandmarks.Kind.CLUSTER)) {
            List<Player> viewers = viewers(world, cluster);
            if (viewers.isEmpty() || !world.isChunkLoaded(cluster.x() >> 4, cluster.z() >> 4)) {
                continue;
            }
            canyons++;
            List<SoulCanyonLandmarks.Pos> sources = sources(world, cluster).open();
            open += sources.size();
            for (int i = 0; i < Math.min(settings.soulsPerRun(), sources.size()); i++) {
                SoulCanyonLandmarks.Pos soul = sources.get(random.nextInt(sources.size()));
                settings.type().builder().location(world, soul.x() + 0.5, soul.y() + 1.15, soul.z() + 0.5)
                        .count(settings.amount()).offset(0.35, 0.2, 0.35).speed(0.02).receivers(viewers).force(true).spawn();
                sent += settings.amount();
            }
        }
        stats.lastCanyons = canyons;
        stats.lastOpenSouls = open;
        stats.lastParticles = sent;
        stats.particles += sent;
    }

    private List<Player> viewers(World world, SoulCanyonLandmarks.Pos cluster) {
        double max = settings.viewDistance() * settings.viewDistance();
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (cluster.distanceSquared(at.getX(), at.getY(), at.getZ()) <= max) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /** Open soul blocks of a canyon (cached for 5 s; a soul that is gone drops out on the next refresh). */
    private Sources sources(World world, SoulCanyonLandmarks.Pos cluster) {
        long now = world.getGameTime();
        Sources cached = cache.get(cluster);
        if (cached != null && cached.validUntil() > now) {
            return cached;
        }
        List<SoulCanyonLandmarks.Pos> souls = landmarks.near(SoulCanyonLandmarks.Kind.SOUL_BLOCK, cluster.x() + 0.5, cluster.y(),
                cluster.z() + 0.5, 20, 14);
        List<SoulCanyonLandmarks.Pos> open = new ArrayList<>();
        int present = 0;
        for (SoulCanyonLandmarks.Pos soul : souls) {
            if (isSoul(world, soul)) {
                present++;
                if (open(world, soul)) {
                    open.add(soul);
                }
            }
        }
        Sources sources = new Sources(open, present, now + 100);
        cache.put(cluster, sources);
        return sources;
    }

    private boolean isSoul(World world, SoulCanyonLandmarks.Pos pos) {
        if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
            return false;
        }
        CustomBlock custom = registry.of(world.getBlockData(pos.x(), pos.y(), pos.z()));
        return custom != null && SoulCanyonLandmarks.SOUL_BLOCK_ID.equals(custom.id());
    }

    /** {@code air-above} passable blocks (air, plants, light) directly above the soul block. */
    private boolean open(World world, SoulCanyonLandmarks.Pos source) {
        for (int above = 1; above <= settings.airAbove(); above++) {
            int y = source.y() + above;
            if (y >= world.getMaxHeight()) {
                return true;
            }
            if (!world.getBlockAt(source.x(), y, source.z()).isPassable()) {
                return false;
            }
        }
        return true;
    }

    /** What the loop sees from a position: canyons in view distance, soul blocks there, open ones. */
    public String describe(Location at) {
        World world = at.getWorld();
        if (world == null || !SiftWorlds.isSift(world)) {
            return "nicht in der Sift";
        }
        int canyons = 0;
        int souls = 0;
        int open = 0;
        double max = settings.viewDistance() * settings.viewDistance();
        for (SoulCanyonLandmarks.Pos cluster : landmarks.all(SoulCanyonLandmarks.Kind.CLUSTER)) {
            if (cluster.distanceSquared(at.getX(), at.getY(), at.getZ()) <= max
                    && world.isChunkLoaded(cluster.x() >> 4, cluster.z() >> 4)) {
                canyons++;
                cache.remove(cluster);
                Sources sources = sources(world, cluster);
                souls += sources.souls();
                open += sources.open().size();
            }
        }
        return String.format(Locale.ROOT, "%s, %s; hier in %.0f Blöcken: %d Schluchten, %d Seelenblöcke, davon %d offen "
                        + "(%d Blöcke frei darüber); letzter Lauf: %d Schluchten, %d offene Seelen, %d Partikel; gesamt %d Partikel "
                        + "in %d Läufen",
                settings.enabled() ? "an" : "aus", settings.type().name(), settings.viewDistance(), canyons, souls, open,
                settings.airAbove(), stats.lastCanyons, stats.lastOpenSouls, stats.lastParticles, stats.particles, stats.runs);
    }
}
