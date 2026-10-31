package de.lamamc.sift.mob.blub;

import de.lamamc.sift.core.mob.BlubRules;
import de.lamamc.sift.core.mob.Pose;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * One Blub. Hops like the original's BlubMoveControl: on the ground it waits a few ticks after landing,
 * then jumps towards the next point of its path (walking is held at zero speed on land). Untamed Blubs
 * take a soul block (1 in 3 tames), get angry as a pack within 16 blocks when a player hurts one, and
 * stay angry for 30 s. Tamed Blubs sit on their owner's click and eat soul blocks or food when hurt.
 */
final class Blub extends SiftMob {

    private static final NamespacedKey HOLD_KEY = new NamespacedKey("the_sift", "blub_hop_hold");
    private static final NamespacedKey ANGER_TICKS = new NamespacedKey("the_sift", "blub_anger_ticks");
    private static final NamespacedKey ANGER_TARGET = new NamespacedKey("the_sift", "blub_anger_target");
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("the_sift", "the_sift/blub");
    private static final int AMBIENT_INTERVAL = 80;

    private final Wolf wolf;
    private final BlubKind blubKind;
    private final BlubRules rules;
    private int jumpDelay;
    private boolean wasOnGround = true;
    private int flappingTicks;
    private int nextFlap;
    private int nextIdleSound;
    private int ambientTime = -AMBIENT_INTERVAL;
    private int angerTicks;
    private UUID angerTarget;

    Blub(BlubKind kind, Wolf wolf, MobManager manager) {
        super(kind, wolf, manager);
        this.wolf = wolf;
        this.blubKind = kind;
        this.rules = kind.rules();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.nextFlap = 100 + random.nextInt(301);
        this.nextIdleSound = 160 + random.nextInt(321);
        var pdc = wolf.getPersistentDataContainer();
        if (!wolf.isTamed()) {
            angerTicks = pdc.getOrDefault(ANGER_TICKS, PersistentDataType.INTEGER, 0);
            String target = pdc.get(ANGER_TARGET, PersistentDataType.STRING);
            try {
                angerTarget = target == null ? null : UUID.fromString(target);
            } catch (IllegalArgumentException e) {
                angerTarget = null;
            }
        }
    }

    @Override
    protected String variant() {
        return wolf.isTamed() ? "tamed_blub" : "blub";
    }

    @Override
    protected void tick() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (flappingTicks > 0) {
            flappingTicks--;
        }
        LivingEntity target = wolf.getTarget();
        sounds(target, random);
        anger(target);
        hop();
        Vector velocity = wolf.getVelocity();
        double horizontal2 = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        boolean canFlap = flappingTicks == 0 && wolf.getTarget() == null && horizontal2 < 0.001;
        if (canFlap && --nextFlap <= 0) {
            flappingTicks = rules.flapTicks();
            nextFlap = rules.nextFlap(random);
        } else if (!canFlap && flappingTicks == 0) {
            nextFlap = Math.max(nextFlap, 40);
        }
        // body: happy (triggered) > sitting (rest pose, adjusted below) > hopping while moving
        if (!animations.isTriggered("body")) {
            if (wolf.isSitting()) {
                animations.stop("body");
            } else if (BlubRules.movingForAnimation(wolf.isOnGround(), horizontal2)) {
                animations.play("body", "hopping", BlubRules.hoppingAnimationSpeed(Math.sqrt(horizontal2)));
            } else {
                animations.stop("body");
            }
        }
        if (flappingTicks > 0) {
            animations.play("ears", "flapping", 1.0);
        } else {
            animations.stop("ears");
        }
    }

    private void sounds(LivingEntity target, ThreadLocalRandom random) {
        // vanilla ambient sound (getAmbientSound of the original) ...
        if (random.nextInt(1000) < ambientTime++) {
            ambientTime = -AMBIENT_INTERVAL;
            playSound(idleSound(), 1.0f, voicePitch(random));
        }
        // ... and the original's extra idle chirp
        if (--nextIdleSound <= 0) {
            if (target == null) {
                playSound(idleSound(), 0.75f, 0.92f + random.nextFloat() * 0.22f);
            }
            nextIdleSound = rules.nextIdleSound(random);
        }
    }

    private String idleSound() {
        return wolf.isInWater() ? "entity.blub.idle_water" : "entity.blub.idle_air";
    }

    private static float voicePitch(ThreadLocalRandom random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
    }

    private void anger(LivingEntity target) {
        if (wolf.isTamed() || angerTicks <= 0) {
            return;
        }
        if (target == null && angerTarget != null) {
            Player restored = Bukkit.getPlayer(angerTarget);
            if (restored != null && restored.isValid() && restored.getWorld().equals(wolf.getWorld()) && attackable(restored)) {
                wolf.setTarget(restored);
                target = restored;
            }
        }
        angerTicks--;
        if (angerTicks == 0 || target != null && target.isDead() || target instanceof Player player && !attackable(player)) {
            wolf.setTarget(null);
            angerTicks = 0;
            angerTarget = null;
        }
    }

    private static boolean attackable(Player player) {
        return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    /** Speed modifier of the goal that moves the Blub now (melee 1.2, tempt/follow 1.1, stroll 1.0). */
    private double speedModifier() {
        if (wolf.getTarget() != null) {
            return 1.2;
        }
        Entity owner = wolf.getOwner() instanceof Entity e ? e : null;
        if (owner != null && owner.getWorld().equals(wolf.getWorld()) && owner.getLocation().distanceSquared(wolf.getLocation()) > 100) {
            return 1.1;
        }
        var tempt = Bukkit.getMobGoals().getGoal(wolf, SoulBlockTemptGoal.KEY);
        return tempt instanceof SoulBlockTemptGoal goal && goal.active() ? 1.1 : 1.0;
    }

    private void hop() {
        boolean onGround = wolf.isOnGround();
        boolean swimming = wolf.isInWater() || wolf.isInLava();
        hold(onGround && !swimming);
        if (jumpDelay > 0) {
            jumpDelay--;
        }
        if (wolf.isSitting() || !onGround || swimming) {
            wasOnGround = onGround;
            return;
        }
        double speed = speedModifier();
        if (!wasOnGround) {
            jumpDelay = rules.landingDelay(speed);
        }
        wasOnGround = true;
        var path = wolf.getPathfinder().getCurrentPath();
        if (jumpDelay > 0 || path == null || path.getNextPoint() == null) {
            return;
        }
        Location next = path.getNextPoint();
        Location here = wolf.getLocation();
        Vector direction = new Vector(next.getX() + 0.5 - here.getX(), 0, next.getZ() + 0.5 - here.getZ());
        if (direction.lengthSquared() < 0.01) {
            return;
        }
        boolean climbing = next.getY() > here.getY() + 0.5;
        double movement = wolf.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
        direction.normalize().multiply(rules.hopSpeed(movement, speed));
        wolf.setVelocity(new Vector(direction.getX(), rules.jumpPower(speed, climbing), direction.getZ()));
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        wolf.setRotation(yaw, here.getPitch());
        wolf.setBodyYaw(yaw);
        wasOnGround = false;
    }

    /** Holds the walking speed at zero on land so the Blub moves only by hopping. */
    private void hold(boolean hold) {
        AttributeInstance speed = wolf.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = speed.getModifier(HOLD_KEY) != null;
        if (hold && !has) {
            speed.addTransientModifier(new AttributeModifier(HOLD_KEY, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY));
        } else if (!hold && has) {
            speed.removeModifier(HOLD_KEY);
        }
    }

    @Override
    protected void adjustPose(Pose pose) {
        var rig = kind.assets().rig();
        if (wolf.isSitting()) {
            int head = rig.bone("head");
            pose.translate(head, 0, -3.25f, 0.65f);
            pose.rotate(head, 17.5f, 0, 0);
            for (String leg : new String[] {"front_left_leg", "front_right_leg"}) {
                pose.rotate(rig.bone(leg), 42f, 0, 0);
                pose.translate(rig.bone(leg), 0, 0, -0.55f);
            }
            for (String leg : new String[] {"back_left_leg", "back_right_leg"}) {
                pose.rotate(rig.bone(leg), -68f, 0, 0);
                pose.translate(rig.bone(leg), 0, 0.55f, 0.75f);
            }
        }
        double max = wolf.getAttribute(Attribute.MAX_HEALTH).getValue();
        float droop = (float) ((1 - Math.max(0, Math.min(1, wolf.getHealth() / max))) * 72);
        pose.rotate(rig.bone("left_ear"), droop, 0, 0);
        pose.rotate(rig.bone("right_ear"), droop, 0, 0);
    }

    @Override
    protected boolean interact(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        boolean soulBlock = SoulBlockTemptGoal.isSoulBlock(held);
        boolean edible = held != null && !held.isEmpty() && held.hasData(DataComponentTypes.FOOD);
        double max = wolf.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (wolf.isTamed() && (soulBlock || edible) && wolf.getHealth() < max) {
            consume(player, held);
            wolf.heal(rules.feedHeal());
            return true;
        }
        if (!wolf.isTamed() && soulBlock && wolf.getTarget() == null && angerTicks <= 0) {
            consume(player, held);
            if (rules.rollTame(ThreadLocalRandom.current())) {
                tame(player);
            } else {
                wolf.playEffect(EntityEffect.TAMING_FAILED);
            }
            return true;
        }
        if (wolf.isTamed() && player.getUniqueId().equals(wolf.getOwnerUniqueId())) {
            wolf.setSitting(!wolf.isSitting());
            wolf.setJumping(false);
            wolf.getPathfinder().stopPathfinding();
            wolf.setTarget(null);
            return true;
        }
        return false;
    }

    private static void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
    }

    private void tame(Player player) {
        wolf.setOwner(player); // also tames it
        blubKind.restoreHealthAttribute(wolf);
        wolf.setRemoveWhenFarAway(false);
        wolf.getPathfinder().stopPathfinding();
        wolf.setTarget(null);
        angerTicks = 0;
        angerTarget = null;
        wolf.setSitting(false);
        Location look = wolf.getLocation();
        float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - look.getX()), player.getZ() - look.getZ()));
        wolf.setRotation(yaw, 0);
        wolf.setBodyYaw(yaw);
        playSound(idleSound(), 0.95f, 1.08f);
        animations.trigger("body", "happy");
        wolf.playEffect(EntityEffect.TAMING_SUCCEEDED);
        Advancement advancement = Bukkit.getAdvancement(ADVANCEMENT);
        if (advancement != null) {
            var progress = player.getAdvancementProgress(advancement);
            progress.getRemainingCriteria().forEach(progress::awardCriteria);
        }
    }

    @Override
    protected void hurt(EntityDamageEvent event) {
        if (wolf.getHealth() - event.getFinalDamage() > 0) {
            playSound("entity.blub.hurt", 1.0f, voicePitch(ThreadLocalRandom.current()));
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        LivingEntity attacker = attacker(byEntity.getDamager());
        if (attacker == null) {
            return;
        }
        if (!wolf.isTamed() && attacker instanceof Player player && attackable(player)) {
            // pack anger: every untamed Blub around joins
            double r = rules.angerRadius();
            for (Entity nearby : wolf.getNearbyEntities(r, r, r)) {
                if (manager.mob(nearby) instanceof Blub other && !other.wolf.isTamed() && !other.dying()) {
                    other.angerAt(player);
                }
            }
            angerAt(player);
        } else if (BlubTargets.canAttack(wolf, attacker)) {
            wolf.setTarget(attacker); // HurtByTargetGoal of the original
        }
    }

    private void angerAt(Player player) {
        wolf.setSitting(false);
        wolf.setTarget(player);
        angerTicks = rules.angerTicks();
        angerTarget = player.getUniqueId();
        wolf.getPathfinder().moveTo(player, 1.2);
    }

    private static LivingEntity attacker(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void attacked(LivingEntity target) {
        playSound("entity.blub.attack", 0.7f, 0.95f + ThreadLocalRandom.current().nextFloat() * 0.15f);
    }

    @Override
    protected void died() {
        playSound("entity.blub.death", 1.0f, voicePitch(ThreadLocalRandom.current()));
    }

    @Override
    protected void detached() {
        var pdc = wolf.getPersistentDataContainer();
        if (!wolf.isTamed() && angerTicks > 0) {
            pdc.set(ANGER_TICKS, PersistentDataType.INTEGER, angerTicks);
            if (angerTarget != null) {
                pdc.set(ANGER_TARGET, PersistentDataType.STRING, angerTarget.toString());
            }
        } else {
            pdc.remove(ANGER_TICKS);
            pdc.remove(ANGER_TARGET);
        }
    }
}
