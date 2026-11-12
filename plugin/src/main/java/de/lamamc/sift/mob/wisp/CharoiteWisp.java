package de.lamamc.sift.mob.wisp;

import de.lamamc.sift.core.mob.CharoiteWispRules;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * One Charoite Wisp. Every 10 ticks it picks the nearest survival player in range, hovers at its distance (circling
 * slowly, backing off when too close) and shoots a charoite shard when ready and in sight. Shards are item displays it
 * moves itself: slow, turning a few degrees per tick towards the target, gone on a block or after their lifetime. A hit
 * makes it blink a few blocks away (if a free spot is found), then it waits for the cooldown.
 */
final class CharoiteWisp extends SiftMob {

    private static final String IDLE = "idle";
    private static final String ACTION = "action";
    private static final int SHOOT_DELAY = 8;
    private static final Particle.DustOptions VIOLET = new Particle.DustOptions(Color.fromRGB(155, 107, 214), 0.9f);

    private final Bee bee;
    private final CharoiteWispKind wispKind;
    private final CharoiteWispRules rules;
    private final List<Shard> shards = new ArrayList<>();
    private Player target;
    private int targetRefresh;
    private int pathRefresh;
    private int shootCooldown;
    private int shootIn = -1;
    private int blinkCooldown;
    private boolean blinkPending;
    private double strafe;
    private int strafeSign = 1;

    /** A shard in flight. */
    private static final class Shard {
        final ItemDisplay display;
        final Player target;
        Location at;
        double[] direction;
        int age;

        Shard(ItemDisplay display, Player target, Location at, double[] direction) {
            this.display = display;
            this.target = target;
            this.at = at;
            this.direction = direction;
        }
    }

    CharoiteWisp(CharoiteWispKind kind, Bee bee, MobManager manager) {
        super(kind, bee, manager);
        this.bee = bee;
        this.wispKind = kind;
        this.rules = kind.rules();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.targetRefresh = random.nextInt(10);
        this.shootCooldown = rules.shootCooldown() / 2 + random.nextInt(Math.max(1, rules.shootCooldown() / 2));
        this.strafe = random.nextDouble() * 360;
        this.strafeSign = random.nextBoolean() ? 1 : -1;
    }

    @Override
    protected Sound.Source source() {
        return Sound.Source.HOSTILE;
    }

    @Override
    public String debugState() {
        return "Ziel " + (target == null ? "-" : target.getName()) + ", Splitter in der Luft " + shards.size() + ", Schuss in "
                + shootCooldown + " Ticks, Blinken in " + blinkCooldown + " Ticks";
    }

    private static boolean attackable(Player p) {
        return p.isValid() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    @Override
    protected void tick() {
        animations.play(IDLE, "idle", 1.0);
        shootCooldown = Math.max(0, shootCooldown - 1);
        blinkCooldown = Math.max(0, blinkCooldown - 1);
        moveShards();
        if (blinkPending) {
            blinkPending = false;
            blink();
        }
        if (bee.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            target = null;
            return;
        }
        if (--targetRefresh <= 0 || target != null && (!attackable(target) || target.getWorld() != bee.getWorld())) {
            targetRefresh = 10;
            target = nearest();
        }
        if (target == null) {
            // idle: drift to about 1.5 blocks above the floor and stay there (no gravity)
            Location here = bee.getLocation();
            boolean low = !here.clone().subtract(0, 1.5, 0).getBlock().isPassable();
            bee.setVelocity(bee.getVelocity().multiply(0.8).setY(low ? 0.06 : bee.getVelocity().getY() * 0.8));
            return;
        }
        bee.lookAt(target);
        Location self = bee.getLocation();
        double d2 = self.distanceSquared(target.getLocation());
        if (--pathRefresh <= 0 || rules.tooClose(d2)) {
            pathRefresh = 10;
            strafe += 12 * strafeSign;
            if (ThreadLocalRandom.current().nextInt(12) == 0) {
                strafeSign = -strafeSign;
            }
            double[] want = rules.desired(new double[] {self.getX(), self.getY(), self.getZ()},
                    new double[] {target.getX(), target.getY(), target.getZ()}, strafe);
            Location goal = new Location(bee.getWorld(), want[0], want[1], want[2]);
            if (!goal.getBlock().isPassable()) {
                goal = target.getLocation().add(0, rules.hoverHeight(), 0);
            }
            if (!bee.getPathfinder().moveTo(goal, 1.0)) {
                Vector toward = goal.toVector().subtract(self.toVector());
                if (toward.lengthSquared() > 1) {
                    bee.setVelocity(toward.normalize().multiply(0.25));
                }
            }
        }
        if (shootIn < 0 && rules.canShoot(shootCooldown, d2, bee.hasLineOfSight(target))) {
            shootIn = SHOOT_DELAY;
            shootCooldown = rules.shootCooldown();
            animations.trigger(ACTION, "attack");
            playSound(Key.key("minecraft", "block.amethyst_block.resonate"), 1.0f, 1.4f);
        }
        if (shootIn >= 0 && --shootIn < 0) {
            shoot();
        }
    }

    private Player nearest() {
        Player best = null;
        double bestD = rules.aggroRange() * rules.aggroRange();
        for (Player p : bee.getWorld().getPlayers()) {
            if (!attackable(p)) {
                continue;
            }
            double d = p.getLocation().distanceSquared(bee.getLocation());
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ---- shards --------------------------------------------------------------------------------------

    private void shoot() {
        if (target == null || !attackable(target)) {
            return;
        }
        Location from = bee.getLocation().add(0, bee.getHeight() * 0.6, 0);
        Location eye = target.getEyeLocation().subtract(0, 0.3, 0);
        double[] dir = {eye.getX() - from.getX(), eye.getY() - from.getY(), eye.getZ() - from.getZ()};
        double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (len < 1e-6) {
            return;
        }
        dir = new double[] {dir[0] / len, dir[1] / len, dir[2] / len};
        Location spawnAt = from.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        ItemDisplay display = bee.getWorld().spawn(spawnAt, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(wispKind.shardItem());
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.55f, 0.55f, 0.55f),
                    new AxisAngle4f()));
        });
        shards.add(new Shard(display, target, from, dir));
        playSound(Key.key("minecraft", "block.amethyst_block.break"), 1.2f, 1.6f);
    }

    private void moveShards() {
        World world = bee.getWorld();
        for (Iterator<Shard> it = shards.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            if (!s.display.isValid() || ++s.age > rules.shardLifetime()) {
                fade(s);
                it.remove();
                continue;
            }
            if (s.target.isValid() && !s.target.isDead() && s.target.getWorld() == world) {
                Location eye = s.target.getEyeLocation().subtract(0, 0.3, 0);
                s.direction = rules.steer(s.direction, new double[] {eye.getX() - s.at.getX(), eye.getY() - s.at.getY(),
                    eye.getZ() - s.at.getZ()});
            }
            Vector dir = new Vector(s.direction[0], s.direction[1], s.direction[2]);
            Location next = s.at.clone().add(dir.clone().multiply(rules.shardSpeed()));
            Player hit = null;
            for (Player p : world.getPlayers()) {
                if (!attackable(p) || p.getLocation().distanceSquared(s.at) > 16) {
                    continue;
                }
                BoundingBox box = p.getBoundingBox().expand(rules.shardHitRadius());
                RayTraceResult r = box.rayTrace(s.at.toVector(), dir, rules.shardSpeed());
                if (r != null || box.contains(s.at.toVector())) {
                    hit = p;
                    break;
                }
            }
            if (hit != null) {
                hit.damage(rules.shardDamage(), DamageSource.builder(DamageType.MOB_PROJECTILE).withCausingEntity(bee)
                        .withDirectEntity(bee).build());
                world.playSound(Sound.sound(Key.key("minecraft", "block.amethyst_cluster.break"), Sound.Source.HOSTILE, 1f, 1.3f),
                        next.getX(), next.getY(), next.getZ());
                fade(s);
                it.remove();
                continue;
            }
            Block block = next.getBlock();
            if (!block.isPassable() && block.getBoundingBox().contains(next.toVector())) {
                fade(s);
                it.remove();
                continue;
            }
            s.at = next;
            Location to = next.clone();
            to.setYaw((float) (s.age * 25 % 360));
            to.setPitch(0);
            s.display.teleport(to);
            world.spawnParticle(Particle.DUST, next, 1, 0.02, 0.02, 0.02, 0, VIOLET);
        }
    }

    private void fade(Shard s) {
        if (s.display.isValid()) {
            s.display.getWorld().spawnParticle(Particle.DUST, s.display.getLocation(), 6, 0.15, 0.15, 0.15, 0, VIOLET);
            s.display.remove();
        }
    }

    private void clearShards() {
        shards.forEach(this::fade);
        shards.clear();
    }

    // ---- blink ---------------------------------------------------------------------------------------

    @Override
    protected void hurt(EntityDamageEvent event) {
        playSound(Key.key("minecraft", "block.amethyst_block.hit"), 1.2f, 1.3f);
        if (blinkCooldown <= 0 && !dying()) {
            blinkPending = true; // not inside the damage event
        }
    }

    private void blink() {
        if (bee.isDead() || !bee.isValid()) {
            return;
        }
        Location here = bee.getLocation();
        for (double[] o : rules.blinkOffsets(ThreadLocalRandom.current(), 10)) {
            Location to = here.clone().add(o[0], o[1], o[2]);
            Block feet = to.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (!feet.isPassable() || !head.isPassable() || feet.isLiquid() || !to.getWorld().getWorldBorder().isInside(to)) {
                continue;
            }
            World world = bee.getWorld();
            world.spawnParticle(Particle.REVERSE_PORTAL, here.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
            world.spawnParticle(Particle.DUST, here.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0, VIOLET);
            to.setYaw(here.getYaw());
            if (teleportSelf(to)) { // passengers (the display rig) stay on since Paper 1.21.10
                world.spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
                animations.trigger(ACTION, "blink");
                playSound(Key.key("minecraft", "entity.enderman.teleport"), 0.8f, 1.7f);
                blinkCooldown = rules.blinkCooldown();
                pathRefresh = 0;
            }
            return;
        }
    }

    @Override
    protected void died() {
        clearShards();
        Location at = bee.getLocation().add(0, 0.5, 0);
        bee.getWorld().spawnParticle(Particle.DUST, at, 24, 0.4, 0.4, 0.4, 0, VIOLET);
        playSound(Key.key("minecraft", "block.amethyst_cluster.break"), 1.2f, 0.8f);
    }

    @Override
    protected void detached() {
        clearShards();
    }
}
