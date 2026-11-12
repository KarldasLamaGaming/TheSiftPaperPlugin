package de.lamamc.sift.mob.boss;

import de.lamamc.sift.core.boss.GolemBoss;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.RigPart;
import de.lamamc.sift.mob.SiftMob;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

/**
 * Great Echo Golem (alpha.42b, our own boss, not in the original): the Echo Golem's model at 2.5 times its size, tinted
 * darker, wearing twelve soul blocks as armour plates, a crown and shoulder spikes of charoite and a night crystal in its
 * chest ("Charoit-Koloss"). Carried by an invisible sheep scaled to 2.9 x 4.2 blocks, so it does not fit through the
 * arena gates. Its health is its own pool (core/boss/GolemBoss); it is never saved, a restart or unload ends the fight.
 */
public final class GreatEchoGolemKind extends MobKind {

    public static final String ID = "great_echo_golem";
    /** Temple id of the fight a golem belongs to (base mob PDC). */
    public static final NamespacedKey TEMPLE = new NamespacedKey("the_sift", "boss_temple");
    /** Plate positions (rig pixels, front = +z): chest 3, brow 2, horns 2, arms 2, back 3. */
    private static final float[][] PLATES = {
        {9, 7.2f, 6.9f}, {14, 7.2f, 6.9f}, {19, 7.2f, 6.9f},
        {9.5f, 21.4f, 6.7f}, {18.5f, 21.4f, 6.7f},
        {4.5f, 36.2f, 0}, {23.5f, 36.2f, 0},
        {4, 6.5f, 0}, {24, 6.5f, 0},
        {9, 8.2f, -6.9f}, {14, 8.2f, -6.9f}, {19, 8.2f, -6.9f}};
    private static final String[] PLATE_BONES = {"torso", "torso", "torso", "head", "head", "top", "top", "left_arm", "right_arm",
        "torso", "torso", "torso"};

    private final SiftItems items;
    private final GolemBoss.Settings settings;
    private final GolemBoss.Attacks attacks;
    private Function<UUID, BossArena> arenas = id -> null;
    private List<RigPart> parts;

    public GreatEchoGolemKind(ConfigurationSection config, SiftItems items) {
        super(ID, "echo_golem", config);
        this.items = items;
        GolemBoss.Settings d = GolemBoss.Settings.defaults();
        ConfigurationSection h = section(config, "health");
        ConfigurationSection p = section(config, "phases");
        ConfigurationSection l = section(config, "loot");
        this.settings = new GolemBoss.Settings(h.getDouble("base", d.baseHealth()), h.getDouble("per-extra-player", d.perExtraPlayer()),
                h.getInt("max-players-scaled", d.maxPlayersScaled()), p.getDouble("armor-until", d.armorUntil()),
                p.getDouble("enrage-below", d.enrageBelow()), Math.min(PLATES.length, p.getInt("armor-plates", d.plates())),
                p.getInt("rearmor-plates", d.rearmorPlates()), l.getDouble("min-damage-share", d.minDamageShare()));
        GolemBoss.Attacks a = GolemBoss.Attacks.defaults();
        ConfigurationSection m = section(config, "attacks.melee");
        ConfigurationSection s = section(config, "attacks.slam");
        ConfigurationSection w = section(config, "attacks.sonic");
        this.attacks = new GolemBoss.Attacks(m.getDouble("damage", a.meleeDamage()), m.getDouble("reach", a.meleeReach()),
                m.getInt("cooldown-ticks", a.meleeCooldown()), s.getDouble("damage", a.slamDamage()), s.getDouble("radius", a.slamRadius()),
                s.getInt("windup-ticks", a.slamWindup()), s.getInt("interval-ticks", a.slamInterval()),
                s.getInt("interval-ticks-rage", a.slamIntervalRage()), w.getDouble("damage", a.sonicDamage()),
                w.getInt("windup-ticks", a.sonicWindup()), w.getInt("interval-ticks", a.sonicInterval()),
                w.getInt("interval-ticks-rage", a.sonicIntervalRage()), section(config, "attacks").getDouble("rage-speed", a.rageSpeed()));
    }

    static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    public GolemBoss.Settings settings() {
        return settings;
    }

    public GolemBoss.Attacks attacks() {
        return attacks;
    }

    /** Which fight a golem (base mob UUID) belongs to; set by the temple module. */
    public void arenas(Function<UUID, BossArena> arenas) {
        this.arenas = arenas;
    }

    BossArena arena(UUID golem) {
        return arenas.apply(golem);
    }

    @Override
    public boolean limited() {
        return false;
    }

    @Override
    public float modelScale() {
        return (float) config.getDouble("model-scale", 2.5);
    }

    Color tint(boolean rage) {
        return color(config.getString(rage ? "tint-rage" : "tint", rage ? "#E07890" : "#9E8CE0"));
    }

    private static Color color(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", "").trim(), 16));
        } catch (RuntimeException e) {
            return null;
        }
    }

    int plateCount() {
        return settings.plates();
    }

    /** The extra parts: plates first (index = plate), then crown, shoulder spikes and the night crystal core (last). */
    List<RigPart> parts() {
        if (parts == null) {
            List<RigPart> out = new ArrayList<>();
            ItemStack soul = item("soul_block");
            for (int i = 0; i < settings.plates(); i++) {
                out.add(new RigPart(PLATE_BONES[i], new Vector3f(PLATES[i][0], PLATES[i][1], PLATES[i][2]),
                        new Vector3f(4.4f, 4.4f, i < 5 || i > 8 ? 2.8f : 4.4f), new Vector3f(), soul, false));
            }
            if (config.getBoolean("model.crown", true)) {
                ItemStack charoite = item("charoite_block");
                float[][] crown = {{14, 0, 13, 5.2f, 0, 0}, {10, 1, 9, 3.6f, 22.5f, 0}, {18, -1, 10, 3.6f, -22.5f, 0},
                    {14, 4, 7, 3, 0, 22.5f}, {14, -4, 8, 3, 0, -22.5f}};
                for (float[] c : crown) {
                    out.add(new RigPart("top", new Vector3f(c[0], 25 + c[2] / 2, c[1]), new Vector3f(c[3], c[2], c[3]),
                            new Vector3f(c[5], 0, c[4]), charoite, true));
                }
                for (int side = -1; side <= 1; side += 2) {
                    out.add(new RigPart("body", new Vector3f(14 + side * 7.5f, 15, 0), new Vector3f(3, 6, 3),
                            new Vector3f(0, 0, -side * 22.5f), charoite, true));
                }
                out.add(new RigPart("torso", new Vector3f(14, 11.2f, 6.9f), new Vector3f(4.5f, 4.5f, 4.5f), new Vector3f(),
                        item("golem_trophy"), false));
            }
            parts = List.copyOf(out);
        }
        return parts;
    }

    boolean hasCore() {
        return config.getBoolean("model.crown", true);
    }

    private ItemStack item(String id) {
        ItemStack stack = items.create(id, 1);
        return stack != null ? stack : ItemStack.of(org.bukkit.Material.SCULK);
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return Sheep.class;
    }

    @Override
    public List<String> variants() {
        return List.of("echo_golem");
    }

    @Override
    public String[] layers() {
        return new String[] {"mission", "soul"};
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        Sheep sheep = (Sheep) base;
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        // the real health is the fight's pool; the base only needs to survive single hits
        set(sheep, Attribute.MAX_HEALTH, null, "", 1000.0);
        set(sheep, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.24);
        set(sheep, Attribute.FOLLOW_RANGE, attributes, "follow-range", 48.0);
        set(sheep, Attribute.STEP_HEIGHT, attributes, "step-height", 1.5);
        set(sheep, Attribute.KNOCKBACK_RESISTANCE, null, "", 1.0);
        set(sheep, Attribute.SAFE_FALL_DISTANCE, null, "", 64.0);
        set(sheep, Attribute.SCALE, config, "base-scale", 3.2);
        if (fresh) {
            sheep.setAdult();
            sheep.setHealth(1000.0);
        }
        sheep.setAgeLock(true);
        sheep.setBreed(false);
        sheep.setSheared(true);
        sheep.setPersistent(false); // never saved: an unload or restart ends the fight
        sheep.setRemoveWhenFarAway(false);
        var goals = Bukkit.getMobGoals();
        for (Goal<Sheep> goal : List.copyOf(goals.getAllGoals(sheep))) {
            if (!VanillaGoal.FLOAT.equals(goal.getKey())) {
                goals.removeGoal(sheep, goal);
            }
        }
    }

    static void set(Mob mob, Attribute attribute, ConfigurationSection section, String key, double fallback) {
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
        return new GreatEchoGolem(this, (Sheep) base, manager);
    }

    @Override
    public int experience() {
        return config.getInt("experience", 300);
    }
}
