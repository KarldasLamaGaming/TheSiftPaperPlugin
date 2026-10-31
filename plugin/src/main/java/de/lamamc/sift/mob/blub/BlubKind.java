package de.lamamc.sift.mob.blub;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.lamamc.sift.core.mob.BlubRules;
import de.lamamc.sift.core.mob.SpawnRules;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.MobSpawner;
import de.lamamc.sift.mob.SiftMob;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Wolf;

/**
 * Blub (BlubEntity of the original 1.1.1): a hopping, tamable Sift creature. Carried by an invisible wolf
 * (scaled to roughly the original hitbox) that keeps only the vanilla goals the original uses as well
 * (float, sit, melee, follow owner, stroll, look around, owner protection); tempting with soul blocks,
 * the hopping, taming, sitting and pack anger are the plugin's.
 */
public final class BlubKind extends MobKind {

    public static final String ID = "blub";

    /** Vanilla wolf goals that the original's BlubEntity registers too; every other wolf goal is removed. */
    private static final Set<GoalKey<?>> KEPT_GOALS = Set.of(VanillaGoal.FLOAT, VanillaGoal.SIT_WHEN_ORDERED_TO,
            VanillaGoal.MELEE_ATTACK, VanillaGoal.FOLLOW_OWNER, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL,
            VanillaGoal.LOOK_AT_PLAYER, VanillaGoal.RANDOM_LOOK_AROUND, VanillaGoal.OWNER_HURT_BY, VanillaGoal.OWNER_HURT);

    private final SpawnRules spawnRules;
    private final BlubRules rules;

    public BlubKind(ConfigurationSection config) {
        super(ID, config);
        this.spawnRules = MobSpawner.rules(config.getConfigurationSection("spawn"));
        BlubRules d = BlubRules.defaults();
        ConfigurationSection hop = section(config, "hopping");
        ConfigurationSection anger = section(config, "pack-anger");
        this.rules = new BlubRules(hop.getDouble("jump-power", d.jumpPower()), hop.getDouble("fast-jump-power", d.fastJumpPower()),
                hop.getDouble("climb-jump-power", d.climbJumpPower()), d.fastSpeed(),
                ints(hop, "landing-delay-ticks", d.landingDelays(), 3), hop.getDouble("hop-velocity", d.hopVelocity()),
                Math.max(1, config.getInt("tame-one-in", d.tameOneIn())), anger.getInt("ticks", d.angerTicks()),
                anger.getDouble("radius", d.angerRadius()), ints(config, "flap-interval-ticks", d.flapInterval(), 2),
                config.getInt("flap-ticks", d.flapTicks()), ints(config, "idle-sound-interval-ticks", d.idleSoundInterval(), 2),
                config.getDouble("feed-heal", d.feedHeal()));
    }

    private static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new org.bukkit.configuration.MemoryConfiguration();
    }

    private static int[] ints(ConfigurationSection section, String key, int[] fallback, int size) {
        List<Integer> values = section.getIntegerList(key);
        if (values.size() != size) {
            return fallback;
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    BlubRules rules() {
        return rules;
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return Wolf.class;
    }

    @Override
    public List<String> variants() {
        return List.of("blub", "tamed_blub");
    }

    @Override
    public String[] layers() {
        return new String[] {"body", "ears"};
    }

    @Override
    public SpawnRules spawnRules() {
        return spawnRules;
    }

    @Override
    public boolean allowsTeleport() {
        return true; // a tamed Blub teleports to its owner like the original (FollowOwnerGoal)
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        Wolf wolf = (Wolf) base;
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        base(wolf, Attribute.MAX_HEALTH, attributes, "max-health", 20.0);
        base(wolf, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.3);
        base(wolf, Attribute.ATTACK_DAMAGE, attributes, "attack-damage", 3.0);
        base(wolf, Attribute.FOLLOW_RANGE, attributes, "follow-range", 24.0);
        base(wolf, Attribute.TEMPT_RANGE, attributes, "tempt-range", 10.0);
        base(wolf, Attribute.STEP_HEIGHT, attributes, "step-height", 1.0);
        base(wolf, Attribute.SCALE, config, "base-scale", 1.1);
        if (fresh) {
            wolf.setAdult();
            wolf.setHealth(wolf.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        wolf.setBreed(false);
        wolf.setAgeLock(true);
        var goals = Bukkit.getMobGoals();
        for (Goal<Wolf> goal : List.copyOf(goals.getAllGoals(wolf))) {
            if (!KEPT_GOALS.contains(goal.getKey())) {
                goals.removeGoal(wolf, goal);
            }
        }
        goals.addGoal(wolf, 3, new SoulBlockTemptGoal(wolf, config.getDouble("tempt-speed", 1.1),
                config.getInt("tempt-cooldown-ticks", 100)));
    }

    private static void base(Wolf wolf, Attribute attribute, ConfigurationSection section, String key, double fallback) {
        AttributeInstance instance = wolf.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(section == null ? fallback : section.getDouble(key, fallback));
        }
    }

    /** Max health again after taming (vanilla wolves get 40 when tamed). */
    void restoreHealthAttribute(Wolf wolf) {
        base(wolf, Attribute.MAX_HEALTH, config.getConfigurationSection("attributes"), "max-health", 20.0);
        double max = wolf.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (wolf.getHealth() > max) {
            wolf.setHealth(max);
        }
    }

    @Override
    public SiftMob create(Mob base, MobManager manager) {
        return new Blub(this, (Wolf) base, manager);
    }
}
