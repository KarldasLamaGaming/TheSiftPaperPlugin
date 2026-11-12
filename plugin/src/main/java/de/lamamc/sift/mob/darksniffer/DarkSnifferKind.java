package de.lamamc.sift.mob.darksniffer;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.lamamc.sift.core.mob.DarkSnifferRules;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PolarBear;
import org.bukkit.inventory.ItemStack;

/**
 * Dark Sniffer (DarkSnifferEntity of the original 1.1.1): a sculk-corrupted sniffer that hunts players and
 * ordinary sniffers within 32 blocks, bites (7), and pulls far targets in with a sound wave; it digs sculk
 * for sculkflower seeds. Carried by an invisible polar bear (scaled to 1.9 × 1.9) that keeps only its float
 * goal; it never rears up, its standing, anger and cub protection are gone with its goals.
 */
public final class DarkSnifferKind extends MobKind {

    /** Polar bear x 1.36: 1.9 x 1.9 (alpha.42e: never spawned into a wall). */
    @Override
    public int[] spawnRoom() {
        return new int[] {3, 2};
    }

    public static final String ID = "dark_sniffer";

    private final DarkSnifferRules rules;
    private final SiftItems items;
    private MobManager manager;

    public DarkSnifferKind(ConfigurationSection config, SiftItems items) {
        super(ID, config);
        this.items = items;
        DarkSnifferRules d = DarkSnifferRules.defaults();
        ConfigurationSection combat = section(config, "combat");
        ConfigurationSection sonic = section(config, "sonic");
        this.rules = new DarkSnifferRules(combat.getDouble("aggro-range", d.aggroRange()), combat.getDouble("reach", d.reach()),
                combat.getInt("cooldown-ticks", d.attackCooldown()), sonic.getInt("windup-ticks", d.sonicWindup()),
                sonic.getInt("cooldown-ticks", d.sonicCooldown()), sonic.getInt("out-of-reach-ticks", d.outOfReachTicks()),
                sonic.getDouble("min-distance", d.sonicMinDistance()), sonic.getDouble("wave-speed", d.waveSpeed()),
                sonic.getDouble("hit-radius", d.hitRadius()), sonic.getDouble("overshoot", d.overshoot()),
                sonic.getDouble("max-lead-ticks", d.maxLeadTicks()), sonic.getDouble("pull-strength", d.pullStrength()));
    }

    static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    DarkSnifferRules rules() {
        return rules;
    }

    SiftItems items() {
        return items;
    }

    MobManager manager() {
        return manager;
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return PolarBear.class;
    }

    @Override
    public List<String> variants() {
        return List.of("dark_sniffer");
    }

    @Override
    public String[] layers() {
        return new String[] {"main"};
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        PolarBear bear = (PolarBear) base;
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        set(bear, Attribute.MAX_HEALTH, attributes, "max-health", 40.0);
        set(bear, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.1);
        set(bear, Attribute.ATTACK_DAMAGE, attributes, "attack-damage", 7.0);
        set(bear, Attribute.FOLLOW_RANGE, attributes, "follow-range", 32.0);
        set(bear, Attribute.STEP_HEIGHT, attributes, "step-height", 1.25);
        set(bear, Attribute.ATTACK_KNOCKBACK, attributes, "attack-knockback", 0.75);
        set(bear, Attribute.SCALE, config, "base-scale", 1.36);
        if (fresh) {
            bear.setAdult();
            bear.setHealth(bear.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        bear.setAgeLock(true);
        bear.setBreed(false);
        bear.setStanding(false);
        bear.setRemoveWhenFarAway(false); // like the original (removeWhenFarAway false)
        var goals = Bukkit.getMobGoals();
        for (Goal<PolarBear> goal : List.copyOf(goals.getAllGoals(bear))) {
            if (!VanillaGoal.FLOAT.equals(goal.getKey())) {
                goals.removeGoal(bear, goal);
            }
        }
        goals.addGoal(bear, 2, new DarkSnifferGoal(bear, this));
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
        this.manager = manager;
        return new DarkSniffer(this, (PolarBear) base, manager);
    }

    /** One sculk block (loot table entities/dark_sniffer of the original). */
    @Override
    public List<ItemStack> drops(SiftMob mob) {
        int count = Math.max(0, config.getInt("drops.sculk", 1));
        return count == 0 ? List.of() : List.of(ItemStack.of(Material.SCULK, count));
    }
}
