package de.lamamc.sift.mob.echogolem;

import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.gen.SoulCanyonLandmarks;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * SiftEchoGolemSpawner of the original 1.1.1, every 80 ticks in the Sift: first a golem at the entrance of a
 * canyon that is still waiting for one (a ring of up to 12 blocks around it, at most 6 blocks higher or lower),
 * otherwise, for a player with no golem within 165 × 96 × 165, one golem 82–160 blocks away (28 attempts on the
 * golden angle) where no other golem is within 165 × 96 × 165. Feet on a full block at most 12 below the
 * surface, 3 free blocks, no water. Golems count towards the Sift mob limits like every Sift mob.
 */
public final class EchoGolemSpawner {

    private static final double GOLDEN_ANGLE = 2.399963229728653;

    private final MobManager mobs;
    private final EchoGolemKind kind;
    private final java.util.function.Supplier<SoulCanyonLandmarks> landmarksSupplier;
    private long spawnedAtCanyons;
    private long spawnedNearPlayers;

    public EchoGolemSpawner(MobManager mobs, EchoGolemKind kind, java.util.function.Supplier<SoulCanyonLandmarks> landmarks) {
        this.mobs = mobs;
        this.kind = kind;
        this.landmarksSupplier = landmarks;
    }

    private ConfigurationSection spawn() {
        return EchoGolemKind.section(kind.config(), "spawn");
    }

    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, Math.max(20, spawn().getInt("interval", 80)));
    }

    public String describe() {
        SoulCanyonLandmarks landmarks = landmarksSupplier.get();
        return "Echo Golems erschienen: " + spawnedAtCanyons + " an Schlucht-Eingängen, " + spawnedNearPlayers
                + " um Spieler; Eingänge ausstehend: " + (landmarks == null ? 0 : landmarks.count(SoulCanyonLandmarks.Kind.GOLEM_PENDING));
    }

    private void tick() {
        World world = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (world == null || !kind.enabled() || !spawn().getBoolean("enabled", true)) {
            return;
        }
        if (spawn().getBoolean("canyon-entrances", true) && spawnAtCanyon(world)) {
            return;
        }
        if (!spawn().getBoolean("near-players", true)) {
            return;
        }
        double spacing = spawn().getDouble("spacing", 165);
        double spacingY = spawn().getDouble("spacing-vertical", 96);
        for (Player player : world.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR || golemWithin(player.getLocation(), spacing, spacingY)) {
                continue;
            }
            if (trySpawnNear(world, player.getLocation(), spacing, spacingY)) {
                return;
            }
        }
    }

    /** One pending canyon entrance per run gets its golem (no spacing rule here, like the original). */
    private boolean spawnAtCanyon(World world) {
        SoulCanyonLandmarks landmarks = landmarksSupplier.get();
        if (landmarks == null) {
            return false;
        }
        int ring = spawn().getInt("entrance-ring", 12);
        int maxDy = spawn().getInt("entrance-max-dy", 6);
        for (SoulCanyonLandmarks.Pos entrance : landmarks.all(SoulCanyonLandmarks.Kind.GOLEM_PENDING)) {
            if (!world.isChunkLoaded(entrance.x() >> 4, entrance.z() >> 4)) {
                continue;
            }
            for (int r = 0; r <= ring; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        int x = entrance.x() + dx;
                        int z = entrance.z() + dz;
                        if (!areaLoaded(world, x, z)) {
                            continue;
                        }
                        Location feet = feet(world, x, z);
                        if (feet == null || Math.abs(feet.getBlockY() - entrance.y()) > maxDy) {
                            continue;
                        }
                        if (spawnAt(feet)) {
                            landmarks.forget(SoulCanyonLandmarks.Kind.GOLEM_PENDING, entrance.x(), entrance.y(), entrance.z());
                            spawnedAtCanyons++;
                            return true;
                        }
                        return false; // world limit reached: try again next run
                    }
                }
            }
        }
        return false;
    }

    private boolean trySpawnNear(World world, Location center, double spacing, double spacingY) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int min = spawn().getInt("min-distance", 82);
        int max = Math.max(min, spawn().getInt("max-distance", 160));
        double phase = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < spawn().getInt("attempts", 28); i++) {
            double angle = phase + i * GOLDEN_ANGLE;
            int radius = min + random.nextInt(max - min + 1);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * radius);
            if (!areaLoaded(world, x, z)) {
                continue;
            }
            Location feet = feet(world, x, z);
            if (feet != null && !golemWithin(feet, spacing, spacingY) && spawnAt(feet)) {
                spawnedNearPlayers++;
                return true;
            }
        }
        return false;
    }

    private boolean spawnAt(Location feet) {
        feet.setYaw(ThreadLocalRandom.current().nextFloat() * 360);
        if (mobs.spawn(kind, feet, false, null) == null) {
            return false;
        }
        feet.getWorld().playSound(Sound.sound(Key.key("the_sift", "entity.echo_golem.spawn"), Sound.Source.NEUTRAL, 0.8f, 0.94f),
                feet.getX(), feet.getY(), feet.getZ());
        return true;
    }

    private static boolean areaLoaded(World world, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Feet on a full block at most 12 below the surface, with 3 free blocks (no fluid) above. */
    static Location feet(World world, int x, int z) {
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        for (int drop = 0; drop <= 12; drop++) {
            Block floor = world.getBlockAt(x, top - drop, z);
            if (!floor.getType().isOccluding() || !floor.isCollidable()) {
                continue;
            }
            boolean free = true;
            for (int up = 1; up <= 3 && free; up++) {
                Block block = floor.getRelative(0, up, 0);
                free = block.isPassable() && !block.isLiquid();
            }
            if (free) {
                return new Location(world, x + 0.5, floor.getY() + 1, z + 0.5);
            }
        }
        return null;
    }

    private boolean golemWithin(Location at, double horizontal, double vertical) {
        for (SiftMob mob : mobs.mobs()) {
            if (mob.kind() == kind && mob.base().getWorld().equals(at.getWorld())) {
                Location l = mob.base().getLocation();
                if (Math.abs(l.getX() - at.getX()) <= horizontal && Math.abs(l.getZ() - at.getZ()) <= horizontal
                        && Math.abs(l.getY() - at.getY()) <= vertical) {
                    return true;
                }
            }
        }
        return false;
    }
}
