package de.lamamc.sift.mob.sifter;

import de.lamamc.sift.core.mob.SifterRules;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * One Sifter. Targets the nearest visible player within its follow range (like NearestAttackableTargetGoal,
 * checked on average every 10 ticks, forgotten after 60 ticks out of sight) and whoever hurts it. Its bite
 * stops it for 14 ticks: sound at tick 8, damage at tick 13 if the target is within 2.5 blocks and in sight.
 * On Peaceful it vanishes like a vanilla monster.
 */
final class Sifter extends SiftMob {

    private static final int AMBIENT_INTERVAL = 80;
    private static final int UNSEEN_MEMORY = 60;
    private static final Map<UUID, Sifter> BY_ID = new HashMap<>();

    private final Pig pig;
    private final SifterRules rules;
    private LivingEntity biteTarget;
    private int biteAge = -1;
    private int unseen;
    private int ambientTime = -AMBIENT_INTERVAL;
    private double walked;
    private Location last;

    Sifter(SifterKind kind, Pig pig, MobManager manager) {
        super(kind, pig, manager);
        this.pig = pig;
        this.rules = kind.rules();
        BY_ID.put(pig.getUniqueId(), this);
    }

    static Sifter of(Pig pig) {
        return BY_ID.get(pig.getUniqueId());
    }

    static boolean attackable(LivingEntity target) {
        return !(target instanceof Player player)
                || player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    boolean biting() {
        return biteAge >= 0;
    }

    void startBite(LivingEntity target) {
        biteTarget = target;
        biteAge = 0;
        pig.getPathfinder().stopPathfinding();
        animations.trigger("attack_controller", "attack");
    }

    @Override
    protected Sound.Source source() {
        return Sound.Source.HOSTILE;
    }

    @Override
    protected void tick() {
        if (pig.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
            pig.remove(); // like a vanilla monster
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        target(random);
        bite();
        sounds(random);
        Vector velocity = pig.getVelocity();
        double horizontal2 = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        if (!animations.isTriggered("attack_controller")) {
            if (horizontal2 > 0.001) {
                animations.play("attack_controller", "running", kind.config().getDouble("running-animation-speed", 2.5));
            } else {
                animations.stop("attack_controller");
            }
        }
    }

    private void target(ThreadLocalRandom random) {
        double range = pig.getAttribute(Attribute.FOLLOW_RANGE).getValue();
        LivingEntity target = pig.getTarget();
        if (target != null) {
            boolean lost = !target.isValid() || target.isDead() || !target.getWorld().equals(pig.getWorld()) || !attackable(target)
                    || target.getLocation().distanceSquared(pig.getLocation()) > range * range;
            unseen = pig.hasLineOfSight(target) ? 0 : unseen + 1;
            if (lost || unseen > UNSEEN_MEMORY) {
                pig.setTarget(null);
                unseen = 0;
            }
            return;
        }
        if (random.nextInt(10) != 0) {
            return;
        }
        Player best = null;
        double bestDistance = range * range;
        for (Player player : pig.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(pig.getLocation());
            if (d <= bestDistance && attackable(player) && !player.isDead() && pig.hasLineOfSight(player)) {
                best = player;
                bestDistance = d;
            }
        }
        if (best != null) {
            pig.setTarget(best);
        }
    }

    private void bite() {
        if (biteAge < 0) {
            return;
        }
        biteAge++;
        pig.getPathfinder().stopPathfinding();
        if (biteTarget != null && biteTarget.isValid()) {
            pig.lookAt(biteTarget, 30f, 30f);
        }
        switch (rules.step(biteAge)) {
            case SOUND -> playSound("entity.sifter.attack", 0.9f, 1.15f + ThreadLocalRandom.current().nextFloat() * 0.15f);
            case DAMAGE -> {
                LivingEntity target = biteTarget;
                if (target != null && target.isValid() && !target.isDead() && target.getWorld().equals(pig.getWorld())
                        && attackable(target) && rules.inReach(target.getLocation().distanceSquared(pig.getLocation()))
                        && pig.hasLineOfSight(target)) {
                    pig.attack(target);
                }
            }
            case END -> {
                biteAge = -1;
                biteTarget = null;
            }
            case NONE -> {
            }
        }
    }

    private void sounds(ThreadLocalRandom random) {
        if (random.nextInt(1000) < ambientTime++) {
            ambientTime = -AMBIENT_INTERVAL;
            playSound("entity.sifter.ambient", 1.0f, voicePitch(random));
        }
        // a quiet step per block walked on the ground (playStepSound of the original)
        Location now = pig.getLocation();
        if (last != null && pig.isOnGround() && now.getWorld().equals(last.getWorld())) {
            double dx = now.getX() - last.getX();
            double dz = now.getZ() - last.getZ();
            walked += Math.sqrt(dx * dx + dz * dz);
            if (walked >= 1) {
                walked -= 1;
                playSound("entity.sifter.step", 0.12f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
        last = now;
    }

    private static float voicePitch(ThreadLocalRandom random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
    }

    @Override
    protected void hurt(EntityDamageEvent event) {
        if (pig.getHealth() - event.getFinalDamage() > 0) {
            playSound("entity.sifter.hurt", 1.0f, voicePitch(ThreadLocalRandom.current()));
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            LivingEntity attacker = attacker(byEntity.getDamager());
            if (attacker != null && attacker != pig && attackable(attacker)) {
                pig.setTarget(attacker); // HurtByTargetGoal
            }
        }
    }

    private static LivingEntity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void died() {
        playSound("entity.sifter.death", 1.0f, voicePitch(ThreadLocalRandom.current()));
    }

    @Override
    protected void detached() {
        BY_ID.remove(pig.getUniqueId());
    }
}
