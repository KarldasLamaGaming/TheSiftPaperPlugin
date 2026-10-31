package de.lamamc.sift.mob;

import de.lamamc.sift.core.mob.SpawnRules;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * One kind of Sift mob: which invisible vanilla mob carries it, its rig and its settings (config.yml
 * mobs.&lt;id&gt;). Creates the {@link SiftMob} that drives one entity.
 */
public abstract class MobKind {

    private final String id;
    private final MobAssets assets;
    protected final ConfigurationSection config;

    protected MobKind(String id, ConfigurationSection config) {
        this.id = id;
        this.config = config;
        this.assets = MobAssets.load(id);
    }

    public final String id() {
        return id;
    }

    public final MobAssets assets() {
        return assets;
    }

    public final ConfigurationSection config() {
        return config;
    }

    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    /** The vanilla entity class carrying the mob (hitbox, pathfinding, physics). */
    public abstract Class<? extends Mob> baseClass();

    /** Texture variants of the rig; the first is the default (item models mob/&lt;variant&gt;/&lt;cube&gt;). */
    public abstract List<String> variants();

    /** Animation layers in the order they are applied (later layers override earlier ones). */
    public abstract String[] layers();

    /** Model scale like the original's renderer (1 = as modelled). */
    public float modelScale() {
        return (float) config.getDouble("model-scale", 1.0);
    }

    /**
     * Sets up the base mob. {@code fresh} = just spawned (attributes, equipment); otherwise it was loaded from
     * the world. Goals are not saved by the game, so this runs on every load.
     */
    public abstract void prepare(Mob base, boolean fresh);

    public abstract SiftMob create(Mob base, MobManager manager);

    /** Natural spawning, or null when this mob only comes from eggs and commands. */
    public SpawnRules spawnRules() {
        return null;
    }

    /** Extra conditions for natural spawning at this spot (light, blocks ...). */
    public boolean canSpawnAt(org.bukkit.Location location) {
        return true;
    }

    /** Whether the base mob may teleport on its own (e.g. a wolf following its owner). */
    public boolean allowsTeleport() {
        return false;
    }

    /** Drops on death (replace the base mob's). */
    public List<ItemStack> drops(SiftMob mob) {
        return List.of();
    }

    /** Experience on death: a number or [min, max]. */
    public int experience() {
        List<Integer> range = config.getIntegerList("experience");
        if (range.size() == 2) {
            return range.get(0) + java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, range.get(1) - range.get(0) + 1));
        }
        return config.getInt("experience", 0);
    }
}
