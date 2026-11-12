package de.lamamc.sift.mob.boss;

import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;

/**
 * Soul helper (alpha.42b): an Echo Golem called by the Great Echo Golem when its armour falls. It fetches souls from the
 * soul blocks in the arena floor and brings them to the golem, each one a new plate. A hit makes it drop the soul (it
 * fades, no item). Never saved, removed when the golem rages or the fight ends.
 */
public final class SoulHelperKind extends MobKind {

    public static final String ID = "soul_helper";
    /** UUID of the golem a helper works for (base mob PDC). */
    public static final NamespacedKey BOSS = new NamespacedKey("the_sift", "boss_helper");

    private Function<UUID, BossArena> arenas = id -> null;

    public SoulHelperKind(ConfigurationSection config) {
        super(ID, "echo_golem", config);
    }

    /** Fight by the golem's UUID; set by the temple module. */
    public void arenas(Function<UUID, BossArena> arenas) {
        this.arenas = arenas;
    }

    /** Pause after each soul brought (ticks), so a group has time to stop the helpers. */
    int restTicks() {
        return Math.max(0, config.getInt("rest-ticks", 60));
    }

    BossArena arena(UUID golem) {
        return golem == null ? null : arenas.apply(golem);
    }

    @Override
    public boolean limited() {
        return false;
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return Sheep.class;
    }

    @Override
    public List<String> variants() {
        return List.of("echo_golem", "echo_golem_glow");
    }

    @Override
    public String[] layers() {
        return new String[] {"mission", "soul"};
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        Sheep sheep = (Sheep) base;
        GreatEchoGolemKind.set(sheep, Attribute.MAX_HEALTH, config, "max-health", 34.0);
        GreatEchoGolemKind.set(sheep, Attribute.MOVEMENT_SPEED, config, "movement-speed", 0.2);
        GreatEchoGolemKind.set(sheep, Attribute.STEP_HEIGHT, null, "", 1.5);
        GreatEchoGolemKind.set(sheep, Attribute.SCALE, null, "", 1.28);
        if (fresh) {
            sheep.setAdult();
            sheep.setHealth(sheep.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        sheep.setAgeLock(true);
        sheep.setBreed(false);
        sheep.setSheared(true);
        sheep.setPersistent(false);
        sheep.setRemoveWhenFarAway(false);
        var goals = Bukkit.getMobGoals();
        for (Goal<Sheep> goal : List.copyOf(goals.getAllGoals(sheep))) {
            if (!VanillaGoal.FLOAT.equals(goal.getKey())) {
                goals.removeGoal(sheep, goal);
            }
        }
    }

    @Override
    public SiftMob create(Mob base, MobManager manager) {
        return new SoulHelper(this, (Sheep) base, manager);
    }

    @Override
    public int experience() {
        return 0;
    }
}
