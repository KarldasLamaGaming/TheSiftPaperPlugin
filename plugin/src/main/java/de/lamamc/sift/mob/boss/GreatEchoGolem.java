package de.lamamc.sift.mob.boss;

import de.lamamc.sift.core.boss.GolemBoss;
import de.lamamc.sift.core.boss.GolemBoss.Phase;
import de.lamamc.sift.core.mob.DarkSnifferRules;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.RigPart;
import de.lamamc.sift.mob.SiftMob;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * One Great Echo Golem. It falls down the temple shaft and lands with a stomp, then stays in the arena: it walks after the
 * nearest fighter, punches within reach, stomps now and then (a red ring warns a second before), and from the sound phase
 * on sends a sound wave at a fighter further away. Only fighters standing in the arena are targets.
 */
public final class GreatEchoGolem extends SiftMob {

    private static final String MISSION = "mission";
    private static final String SOUL = "soul";
    private static final int MELEE_TICKS = 12;
    private static final int MELEE_HIT = 8;
    private static final int DESCENT_TIMEOUT = 160;

    private enum Action { NONE, MELEE, SLAM, SONIC }

    private final Sheep sheep;
    private final GreatEchoGolemKind golemKind;
    private final GolemBoss.Attacks attacks;
    private final DarkSnifferRules wave = new DarkSnifferRules(48, 0, 0, 0, 0, 0, 0, 1.4, 1.0, 6.0, 10.0, 0);
    private boolean landed;
    private Action action = Action.NONE;
    private int actionTicks;
    private int meleeCooldown;
    private int slamCooldown;
    private int sonicCooldown;
    private int targetRefresh;
    private int pathRefresh;
    private Player target;
    private DarkSnifferRules.Wave flying;
    private int waveAge;
    private final Set<UUID> waveHit = new HashSet<>();
    private boolean moving;
    private Location last;
    private Phase shownPhase;

    GreatEchoGolem(GreatEchoGolemKind kind, Sheep sheep, MobManager manager) {
        super(kind, sheep, manager);
        this.sheep = sheep;
        this.golemKind = kind;
        this.attacks = kind.attacks();
        this.slamCooldown = attacks.slamInterval(Phase.ARMOR) / 2;
        this.sonicCooldown = attacks.sonicInterval(Phase.SOUND) / 2;
    }

    @Override
    protected Sound.Source source() {
        return Sound.Source.HOSTILE;
    }

    public boolean landed() {
        return landed;
    }

    private BossArena arena() {
        return golemKind.arena(sheep.getUniqueId());
    }

    private Phase phase() {
        BossArena arena = arena();
        GolemBoss.Fight fight = arena == null ? null : arena.fight();
        return fight == null ? Phase.ARMOR : fight.phase();
    }

    @Override
    protected Color tint() {
        return golemKind.tint(phase() == Phase.RAGE);
    }

    @Override
    protected List<RigPart> parts() {
        return golemKind.parts();
    }

    @Override
    protected boolean partVisible(int index) {
        if (index < golemKind.plateCount()) {
            BossArena arena = arena();
            GolemBoss.Fight fight = arena == null ? null : arena.fight();
            return fight == null || index < fight.plates();
        }
        return true;
    }

    @Override
    protected boolean partGlows(int index) {
        if (golemKind.hasCore() && index == golemKind.parts().size() - 1) {
            Phase p = phase();
            return p == Phase.SOUND || p == Phase.RAGE;
        }
        return super.partGlows(index);
    }

    @Override
    public String debugState() {
        BossArena arena = arena();
        GolemBoss.Fight fight = arena == null ? null : arena.fight();
        return (landed ? "gelandet" : "fällt") + ", Aktion " + action + (fight == null ? ""
                : String.format(java.util.Locale.ROOT, ", Phase %s, Leben %.0f/%.0f, Platten %d", fight.phase(), fight.health(),
                fight.maxHealth(), fight.plates()))
                + ", Ziel " + (target == null ? "-" : target.getName()) + ", Abklingzeiten Schlag " + meleeCooldown + " Stampfer "
                + slamCooldown + " Schall " + sonicCooldown;
    }

    @Override
    protected void tick() {
        BossArena arena = arena();
        if (arena == null) {
            sheep.remove(); // a golem without its fight (should not happen: it is never saved)
            return;
        }
        animations.play(SOUL, "soul_hidden", 1.0);
        if (!landed) {
            descend(arena);
            return;
        }
        GolemBoss.Fight fight = arena.fight();
        if (fight == null || fight.phase() == Phase.DEAD) {
            return;
        }
        Phase phase = fight.phase();
        if (phase != shownPhase) {
            if (shownPhase != null) {
                roar(phase);
            }
            shownPhase = phase;
        }
        if (fight.takeSoulCall()) {
            arena.soulCall(this);
        }
        meleeCooldown = Math.max(0, meleeCooldown - 1);
        slamCooldown = Math.max(0, slamCooldown - 1);
        sonicCooldown = Math.max(0, sonicCooldown - 1);
        if (!leash(arena)) {
            return;
        }
        if (age % 10 == 0) {
            aura(phase);
        }
        List<Player> fighters = arena.fighters();
        if (--targetRefresh <= 0 || target == null || !fighters.contains(target)) {
            targetRefresh = 10;
            target = nearest(fighters);
        }
        switch (action) {
            case MELEE -> meleeTick();
            case SLAM -> slamTick(fighters, phase);
            case SONIC -> sonicTick(fighters, phase);
            case NONE -> choose(fighters, phase, arena);
        }
        animate();
    }

    // ---- descent and arena ---------------------------------------------------------------------------

    private void descend(BossArena arena) {
        animations.play(MISSION, "idle", 1.0);
        if (age > 3 && sheep.isOnGround() || age > DESCENT_TIMEOUT) {
            if (!sheep.isOnGround()) {
                teleportSelf(arena.landing());
            }
            landed = true;
            impact(arena.fighters(), 0, 1.0);
            arena.landed(this);
        }
    }

    /** Keeps the golem in the arena; false when it was put back (no action this tick). */
    private boolean leash(BossArena arena) {
        GolemBoss.Arena bounds = arena.bounds();
        Location at = sheep.getLocation();
        double d = bounds.distance(at.getX(), at.getZ());
        if (d > bounds.radius() + 3 || Math.abs(at.getY() - bounds.floorY()) > 6) {
            teleportSelf(arena.landing());
            cancelAction();
            return false;
        }
        if (d > bounds.radius() - 1.5 && action == Action.NONE && (target == null || bounds.distance(target.getX(), target.getZ())
                > bounds.radius() - 1)) {
            sheep.getPathfinder().moveTo(arena.landing(), 1.0);
        }
        return true;
    }

    private Player nearest(List<Player> fighters) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : fighters) {
            double d = p.getLocation().distanceSquared(sheep.getLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private double horizontal(Location a) {
        Location b = sheep.getLocation();
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private void choose(List<Player> fighters, Phase phase, BossArena arena) {
        if (target == null) {
            if (--pathRefresh <= 0) {
                pathRefresh = 20;
                if (horizontal(arena.landing()) > 2) {
                    sheep.getPathfinder().moveTo(arena.landing(), attacks.speed(phase));
                }
            }
            return;
        }
        double d = horizontal(target.getLocation());
        int sonic = attacks.sonicInterval(phase);
        if (sonic >= 0 && sonicCooldown <= 0 && d > attacks.meleeReach() + 2) {
            beginSonic();
            return;
        }
        if (slamCooldown <= 0 && fighters.stream().anyMatch(p -> horizontal(p.getLocation()) <= attacks.slamRadius())) {
            beginSlam();
            return;
        }
        if (meleeCooldown <= 0 && d <= attacks.meleeReach() && Math.abs(target.getY() - sheep.getY()) < 3) {
            action = Action.MELEE;
            actionTicks = 0;
            sheep.getPathfinder().stopPathfinding();
            sheep.lookAt(target);
            animations.trigger(MISSION, "pickup");
            return;
        }
        if (--pathRefresh <= 0) {
            pathRefresh = 8;
            sheep.getPathfinder().moveTo(target, attacks.speed(phase));
        }
    }

    private void cancelAction() {
        action = Action.NONE;
        actionTicks = 0;
        flying = null;
        waveHit.clear();
    }

    private void animate() {
        Location now = sheep.getLocation();
        moving = last != null && last.getWorld() == now.getWorld() && last.distanceSquared(now) > 0.0004;
        last = now;
        if (action == Action.NONE && !animations.isTriggered(MISSION)) {
            animations.play(MISSION, moving ? "walk" : "idle", 1.0);
            if (moving && age % 18 == 0) {
                playSound(Key.key("minecraft", "entity.iron_golem.step"), 1.4f, 0.5f);
            }
        }
    }

    // ---- melee ----------------------------------------------------------------------------------------

    private void meleeTick() {
        actionTicks++;
        if (target != null) {
            sheep.lookAt(target);
        }
        if (actionTicks == MELEE_HIT && target != null && horizontal(target.getLocation()) <= attacks.meleeReach() + 0.75) {
            hurt(target, attacks.meleeDamage(), DamageType.MOB_ATTACK);
            Vector push = target.getLocation().toVector().subtract(sheep.getLocation().toVector()).setY(0);
            if (push.lengthSquared() > 1e-6) {
                target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.9).setY(0.35)));
            }
            playSound(Key.key("minecraft", "entity.iron_golem.attack"), 1.6f, 0.55f);
        }
        if (actionTicks >= MELEE_TICKS) {
            meleeCooldown = attacks.meleeCooldown();
            cancelAction();
        }
    }

    private void hurt(Player player, double amount, DamageType type) {
        player.damage(amount, DamageSource.builder(type).withCausingEntity(sheep).withDirectEntity(sheep).build());
    }

    // ---- stomp ----------------------------------------------------------------------------------------

    private void beginSlam() {
        action = Action.SLAM;
        actionTicks = 0;
        sheep.getPathfinder().stopPathfinding();
        animations.trigger(MISSION, "bow");
        playSound(Key.key("minecraft", "entity.warden.roar"), 1.2f, 1.4f);
    }

    private void slamTick(List<Player> fighters, Phase phase) {
        actionTicks++;
        if (actionTicks % 4 == 1) {
            ring(attacks.slamRadius(), new Particle.DustOptions(Color.fromRGB(230, 60, 70), 1.6f));
        }
        if (actionTicks >= attacks.slamWindup()) {
            impact(fighters, 1.0, 1.0);
            slamCooldown = attacks.slamInterval(phase);
            cancelAction();
        }
    }

    /** A stomp: damage (scaled by {@code damageFactor}) and a throw for fighters within the stomp radius. */
    private void impact(List<Player> fighters, double damageFactor, double throwFactor) {
        Location at = sheep.getLocation();
        World world = sheep.getWorld();
        for (Player p : fighters) {
            double d = horizontal(p.getLocation());
            if (d > attacks.slamRadius() || Math.abs(p.getY() - at.getY()) > 2.5 || !p.isOnGround() && damageFactor > 0) {
                continue;
            }
            double dmg = attacks.slamDamageAt(d) * damageFactor;
            if (dmg > 0) {
                hurt(p, dmg, DamageType.MOB_ATTACK);
            }
            Vector out = p.getLocation().toVector().subtract(at.toVector()).setY(0);
            out = out.lengthSquared() < 1e-6 ? new Vector(0, 0, 0) : out.normalize().multiply(0.7 * throwFactor);
            p.setVelocity(p.getVelocity().add(out.setY(0.75 * throwFactor)));
        }
        ring(attacks.slamRadius() * 0.5, null);
        ring(attacks.slamRadius(), null);
        world.spawnParticle(Particle.BLOCK, at, 80, attacks.slamRadius() / 2, 0.2, attacks.slamRadius() / 2, 0,
                Material.DEEPSLATE_BRICKS.createBlockData());
        playSound(Key.key("minecraft", "entity.generic.explode"), 1.6f, 0.55f);
        playSound(Key.key("minecraft", "entity.warden.attack_impact"), 2.0f, 0.6f);
    }

    private void ring(double radius, Particle.DustOptions dust) {
        Location at = sheep.getLocation().add(0, 0.2, 0);
        int points = (int) Math.max(12, radius * 8);
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            Location p = at.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (dust != null) {
                sheep.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, dust);
            } else {
                sheep.getWorld().spawnParticle(Particle.SOUL, p, 1, 0, 0.05, 0, 0.02);
            }
        }
    }

    // ---- sound wave -----------------------------------------------------------------------------------

    private void beginSonic() {
        action = Action.SONIC;
        actionTicks = 0;
        flying = null;
        waveHit.clear();
        sheep.getPathfinder().stopPathfinding();
        animations.play(MISSION, "carry_idle", 1.0);
        playSound(Key.key("minecraft", "entity.warden.sonic_charge"), 2.0f, 0.7f);
    }

    private void sonicTick(List<Player> fighters, Phase phase) {
        actionTicks++;
        if (flying == null) {
            if (target != null) {
                sheep.lookAt(target);
            }
            Location eye = sheep.getEyeLocation();
            sheep.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, eye, 3, 0.6, 0.4, 0.6, 0.01);
            if (actionTicks < attacks.sonicWindup()) {
                return;
            }
            if (target == null) {
                cancelAction();
                return;
            }
            Location t = target.getEyeLocation();
            Vector v = target.getVelocity();
            flying = wave.aim(new double[] {eye.getX(), eye.getY() - 0.8, eye.getZ()}, new double[] {t.getX(), t.getY(), t.getZ()},
                    new double[] {v.getX(), 0, v.getZ()});
            waveAge = 0;
            sonicCooldown = attacks.sonicInterval(phase);
            if (flying == null) {
                cancelAction();
                return;
            }
            playSound(Key.key("minecraft", "entity.warden.sonic_boom"), 2.5f, 0.75f);
            return;
        }
        double[] previous = flying.position(waveAge, wave.waveSpeed());
        waveAge++;
        double[] now = flying.position(waveAge, wave.waveSpeed());
        sheep.getWorld().spawnParticle(Particle.SONIC_BOOM, now[0], now[1], now[2], 1, 0, 0, 0, 0);
        for (Player p : fighters) {
            if (waveHit.contains(p.getUniqueId())) {
                continue;
            }
            BoundingBox b = p.getBoundingBox();
            if (wave.hits(new double[] {b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()}, previous, now)) {
                waveHit.add(p.getUniqueId());
                hurt(p, attacks.sonicDamage(), DamageType.SONIC_BOOM);
                Vector dir = new Vector(flying.direction()[0], 0, flying.direction()[2]);
                if (dir.lengthSquared() > 1e-6) {
                    p.setVelocity(p.getVelocity().add(dir.normalize().multiply(1.2).setY(0.3)));
                }
            }
        }
        if (waveAge >= flying.flightTicks()) {
            animations.stop(MISSION);
            cancelAction();
        }
    }

    // ---- phases ---------------------------------------------------------------------------------------

    private void roar(Phase phase) {
        cancelAction();
        playSound(Key.key("minecraft", "entity.warden.roar"), 2.5f, phase == Phase.RAGE ? 0.6f : 0.8f);
        Location at = sheep.getLocation().add(0, sheep.getHeight() * 0.6, 0);
        sheep.getWorld().spawnParticle(phase == Phase.RAGE ? Particle.ANGRY_VILLAGER : Particle.SOUL_FIRE_FLAME, at, 30,
                sheep.getWidth() / 2, sheep.getHeight() / 3, sheep.getWidth() / 2, 0.02);
        if (phase == Phase.SOUND) {
            // the armour is gone: its plates shatter visibly
            sheep.getWorld().spawnParticle(Particle.BLOCK, at, 60, 1.2, 1.2, 1.2, 0, Material.SOUL_SOIL.createBlockData());
        }
    }

    /** Souls rise from the golem once its armour is gone; red dust in its rage. */
    private void aura(Phase phase) {
        if (phase == Phase.ARMOR) {
            return;
        }
        Location head = sheep.getLocation().add(0, sheep.getHeight() * 0.85, 0);
        sheep.getWorld().spawnParticle(Particle.SOUL, head, 3, sheep.getWidth() / 3, 0.4, sheep.getWidth() / 3, 0.02);
        if (phase == Phase.RAGE) {
            sheep.getWorld().spawnParticle(Particle.DUST, head, 6, sheep.getWidth() / 2, 0.8, sheep.getWidth() / 2, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 50, 70), 1.4f));
        }
    }

    /** A plate broke off (called by the fight): sound and splinters. */
    public void plateBroken() {
        Location at = sheep.getLocation().add(0, sheep.getHeight() * 0.5, 0);
        sheep.getWorld().spawnParticle(Particle.BLOCK, at, 25, 1.0, 1.0, 1.0, 0, Material.SOUL_SOIL.createBlockData());
        playSound(Key.key("minecraft", "block.glass.break"), 1.5f, 0.6f);
        playSound("entity.echo_golem.hurt", 1.5f, 0.6f);
    }

    /** An arrow or trident bounced off the plates. */
    public void bounced() {
        playSound(Key.key("minecraft", "item.shield.block"), 1.5f, 0.7f);
    }

    /** A hit that took health. */
    public void wounded() {
        playSound("entity.echo_golem.hurt", 1.6f, 0.55f);
    }

    public Sheep sheep() {
        return sheep;
    }
}
