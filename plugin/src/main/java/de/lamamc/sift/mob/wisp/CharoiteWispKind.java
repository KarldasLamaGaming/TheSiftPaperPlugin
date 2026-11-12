package de.lamamc.sift.mob.wisp;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.lamamc.sift.core.mob.CharoiteWispRules;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * Charoite Wisp (alpha.42d, our own mob, not in the original): a floating core of sculk light with charoite shards
 * around it. It hovers at a distance from its target, shoots slow, slightly homing charoite shards and blinks away when
 * hit. Comes only from the temple brood spawners (zone 3) and /sift spawn. Carried by an invisible bee (it flies by
 * itself, its goals are all removed), scaled to about 1 x 0.9 blocks.
 */
public final class CharoiteWispKind extends MobKind {

    public static final String ID = "charoite_wisp";

    private final CharoiteWispRules rules;
    private final SiftItems items;

    public CharoiteWispKind(ConfigurationSection config, SiftItems items) {
        super(ID, config);
        this.items = items;
        CharoiteWispRules d = CharoiteWispRules.defaults();
        ConfigurationSection c = config.getConfigurationSection("combat");
        if (c == null) {
            c = new MemoryConfiguration();
        }
        this.rules = new CharoiteWispRules(c.getDouble("aggro-range", d.aggroRange()), c.getDouble("preferred-distance", d.preferredDistance()),
                c.getDouble("min-distance", d.minDistance()), c.getDouble("hover-height", d.hoverHeight()),
                c.getDouble("shoot-range", d.shootRange()), c.getInt("shoot-cooldown-ticks", d.shootCooldown()),
                c.getDouble("shard-speed", d.shardSpeed()), c.getDouble("shard-turn-degrees", d.shardTurn()),
                c.getInt("shard-lifetime-ticks", d.shardLifetime()), c.getDouble("shard-damage", d.shardDamage()),
                c.getDouble("shard-hit-radius", d.shardHitRadius()), c.getDouble("blink-distance", d.blinkDistance()),
                c.getInt("blink-cooldown-ticks", d.blinkCooldown()));
    }

    public CharoiteWispRules rules() {
        return rules;
    }

    /** The shard a wisp shoots (the charoite item, its model is the crystal). */
    ItemStack shardItem() {
        ItemStack stack = items.create("charoite", 1);
        return stack != null ? stack : ItemStack.of(org.bukkit.Material.AMETHYST_SHARD);
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return Bee.class;
    }

    @Override
    public List<String> variants() {
        return List.of("charoite_wisp");
    }

    @Override
    public String[] layers() {
        return new String[] {"idle", "action"};
    }

    @Override
    public boolean hostile() {
        return true;
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        Bee bee = (Bee) base;
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        set(bee, Attribute.MAX_HEALTH, attributes, "max-health", 18.0);
        set(bee, Attribute.FLYING_SPEED, attributes, "flying-speed", 0.6);
        set(bee, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.3);
        set(bee, Attribute.FOLLOW_RANGE, attributes, "follow-range", 32.0);
        set(bee, Attribute.SCALE, config, "base-scale", 1.5);
        if (fresh) {
            bee.setAdult();
            bee.setHealth(bee.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        bee.setAgeLock(true);
        bee.setBreed(false);
        bee.setHasStung(false);
        bee.setHasNectar(false);
        bee.setCannotEnterHiveTicks(Integer.MAX_VALUE);
        bee.setGravity(false); // it hovers also when it has no target (a bee only floats while flying)
        var goals = Bukkit.getMobGoals();
        for (Goal<Bee> goal : List.copyOf(goals.getAllGoals(bee))) {
            if (!VanillaGoal.FLOAT.equals(goal.getKey())) {
                goals.removeGoal(bee, goal);
            }
        }
    }

    private static void set(Mob mob, Attribute attribute, ConfigurationSection section, String key, double fallback) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) {
            mob.registerAttribute(attribute);
            instance = mob.getAttribute(attribute);
        }
        if (instance != null) {
            instance.setBaseValue(section == null ? fallback : section.getDouble(key, fallback));
        }
    }

    @Override
    public SiftMob create(Mob base, MobManager manager) {
        return new CharoiteWisp(this, (Bee) base, manager);
    }
}
