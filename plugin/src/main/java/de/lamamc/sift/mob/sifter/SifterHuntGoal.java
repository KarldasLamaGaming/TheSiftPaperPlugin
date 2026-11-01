package de.lamamc.sift.mob.sifter;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.util.BoundingBox;

/**
 * Vanilla MeleeAttackGoal for the Sifter (speed 1.18, no pursuit without sight): runs to the target,
 * re-plans the path every 4–11 ticks, and when in melee range and the cooldown is over starts a bite (the
 * damage comes later, see {@link Sifter}). Stands still while a bite runs.
 */
final class SifterHuntGoal implements Goal<Pig> {

    static final GoalKey<Pig> KEY = GoalKey.of(Pig.class, new NamespacedKey("the_sift", "sifter_hunt"));

    private final Pig pig;
    private final SifterKind kind;
    private final double speed;
    private int repath;
    private int cooldown;

    SifterHuntGoal(Pig pig, SifterKind kind, double speed) {
        this.pig = pig;
        this.kind = kind;
        this.speed = speed;
    }

    private Sifter sifter() {
        return Sifter.of(pig);
    }

    private boolean valid(LivingEntity target) {
        return target != null && target.isValid() && !target.isDead() && target.getWorld().equals(pig.getWorld())
                && Sifter.attackable(target);
    }

    @Override
    public boolean shouldActivate() {
        return valid(pig.getTarget()) && sifter() != null;
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void start() {
        repath = 0;
        pig.getPathfinder().moveTo(pig.getTarget(), speed);
    }

    @Override
    public void stop() {
        pig.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick() {
        LivingEntity target = pig.getTarget();
        Sifter sifter = sifter();
        if (target == null || sifter == null) {
            return;
        }
        pig.lookAt(target, 30f, 30f);
        if (cooldown > 0) {
            cooldown--;
        }
        if (sifter.biting()) {
            pig.getPathfinder().stopPathfinding();
            return;
        }
        if (--repath <= 0) {
            repath = 4 + java.util.concurrent.ThreadLocalRandom.current().nextInt(8);
            pig.getPathfinder().moveTo(target, speed);
        }
        if (cooldown <= 0 && inMeleeRange(target) && pig.hasLineOfSight(target)) {
            cooldown = kind.rules().cooldownTicks();
            sifter.startBite(target);
        }
    }

    private boolean inMeleeRange(LivingEntity target) {
        return kind.rules().inMeleeRange(box(pig.getBoundingBox()), box(target.getBoundingBox()));
    }

    private static double[] box(BoundingBox b) {
        return new double[] {b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()};
    }

    @Override
    public GoalKey<Pig> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }
}
