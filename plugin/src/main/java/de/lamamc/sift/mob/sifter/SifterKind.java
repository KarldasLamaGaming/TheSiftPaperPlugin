package de.lamamc.sift.mob.sifter;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.mob.SifterRules;
import de.lamamc.sift.core.mob.SpawnRules;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.MobSpawner;
import de.lamamc.sift.mob.SiftMob;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Sifter (SifterEntity of the original 1.1.1): a hostile Sift predator that hunts players and bites.
 * Carried by an invisible pig (hitbox 0.9 × 0.9, original 0.9 × 0.95) that keeps only float, stroll and
 * looking around; targeting, chasing and the delayed bite are the plugin's, with the pig's attack damage
 * attribute added.
 */
public final class SifterKind extends MobKind {

    public static final String ID = "sifter";

    /** Vanilla pig goals that the original's SifterEntity registers too; the rest (panic, breed, tempt ...) go. */
    private static final Set<GoalKey<?>> KEPT_GOALS = Set.of(VanillaGoal.FLOAT, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL,
            VanillaGoal.LOOK_AT_PLAYER, VanillaGoal.RANDOM_LOOK_AROUND);

    private final SpawnRules spawnRules;
    private final SifterRules rules;
    private final CustomBlockRegistry blocks;
    private final SiftItems items;
    private final Set<String> floors;

    public SifterKind(ConfigurationSection config, CustomBlockRegistry blocks, SiftItems items) {
        super(ID, config);
        this.blocks = blocks;
        this.items = items;
        ConfigurationSection spawn = config.getConfigurationSection("spawn");
        this.spawnRules = MobSpawner.rules(spawn);
        List<String> floorList = spawn == null ? List.of() : spawn.getStringList("floor-blocks");
        this.floors = Set.copyOf(floorList);
        SifterRules d = SifterRules.defaults();
        ConfigurationSection bite = section(config, "bite");
        List<Integer> meat = section(config, "drops").getIntegerList("raw-meat");
        this.rules = new SifterRules(bite.getInt("ticks", d.biteTicks()), bite.getInt("sound-tick", d.soundTick()),
                bite.getInt("damage-tick", d.damageTick()), bite.getDouble("reach", d.reach()),
                bite.getInt("cooldown-ticks", d.cooldownTicks()), bite.getDouble("start-range-inflate", d.attackInflate()),
                meat.size() == 2 ? new int[] {meat.get(0), meat.get(1)} : d.meat(),
                section(config, "drops").getInt("looting-bonus", d.lootingBonus()));
    }

    private static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    SifterRules rules() {
        return rules;
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return Pig.class;
    }

    @Override
    public List<String> variants() {
        return List.of("sifter");
    }

    @Override
    public String[] layers() {
        return new String[] {"attack_controller"};
    }

    @Override
    public SpawnRules spawnRules() {
        return spawnRules;
    }

    @Override
    public boolean hostile() {
        return true;
    }

    /** Siftslate, siftslate growth and the healthy sculk blocks, like SifterEntity#canSpawnOn. */
    @Override
    public boolean canSpawnOn(Block floor) {
        if (floors.isEmpty()) {
            return true;
        }
        CustomBlock custom = blocks.at(floor);
        return custom != null && floors.contains(custom.id().replace("the_sift:", ""));
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        Pig pig = (Pig) base;
        if (pig.getAttribute(Attribute.ATTACK_DAMAGE) == null) {
            pig.registerAttribute(Attribute.ATTACK_DAMAGE);
        }
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        base(pig, Attribute.MAX_HEALTH, attributes, "max-health", 40.0);
        base(pig, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.31);
        base(pig, Attribute.ATTACK_DAMAGE, attributes, "attack-damage", 10.0);
        base(pig, Attribute.FOLLOW_RANGE, attributes, "follow-range", 28.0);
        base(pig, Attribute.STEP_HEIGHT, attributes, "step-height", 1.0);
        base(pig, Attribute.SCALE, config, "base-scale", 1.0);
        if (fresh) {
            pig.setAdult();
            pig.setHealth(pig.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        pig.setBreed(false);
        pig.setAgeLock(true);
        var goals = Bukkit.getMobGoals();
        for (Goal<Pig> goal : List.copyOf(goals.getAllGoals(pig))) {
            if (!KEPT_GOALS.contains(goal.getKey())) {
                goals.removeGoal(pig, goal);
            }
        }
        goals.addGoal(pig, 2, new SifterHuntGoal(pig, this, config.getDouble("chase-speed", 1.18)));
    }

    private static void base(Mob mob, Attribute attribute, ConfigurationSection section, String key, double fallback) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(section == null ? fallback : section.getDouble(key, fallback));
        }
    }

    @Override
    public SiftMob create(Mob base, MobManager manager) {
        return new Sifter(this, (Pig) base, manager);
    }

    /** 1–2 raw Sifter meat, +0–1 per Looting level of the killer's weapon, cooked when the Sifter burns. */
    @Override
    public List<ItemStack> drops(SiftMob mob) {
        int looting = 0;
        if (mob.base().getKiller() instanceof Player killer) {
            looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        }
        int count = rules.meatCount(looting, ThreadLocalRandom.current());
        String id = mob.base().getFireTicks() > 0 ? "cooked_sifter_meat" : "raw_sifter_meat";
        List<ItemStack> drops = new ArrayList<>();
        if (count > 0 && items.ids().contains(id)) {
            drops.add(items.create(id, count));
        }
        return drops;
    }
}
