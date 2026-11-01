package de.lamamc.sift.mob.darksniffer;

import de.lamamc.sift.core.mob.DarkSnifferRules;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PolarBear;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * One Dark Sniffer. Every 10 ticks it picks the nearer of the nearest survival player and the nearest
 * ordinary sniffer within its aggro range; it chases at 0.3, bites within reach, and when the target keeps
 * out of reach it sniffs and sends a sound wave that pulls it in. Without a target it wanders near home
 * and now and then digs a sculk block for sculkflower seeds. On Peaceful it does not attack.
 */
final class DarkSniffer extends SiftMob {

    private static final NamespacedKey HOME = new NamespacedKey("the_sift", "dark_sniffer_home");
    private static final NamespacedKey DIG_COOLDOWN = new NamespacedKey("the_sift", "dark_sniffer_dig_cooldown");
    private static final String LAYER = "main";
    private static final int AMBIENT_INTERVAL = 80;

    private final PolarBear bear;
    private final DarkSnifferKind snifferKind;
    private final DarkSnifferRules rules;
    private final ConfigurationSection config;
    private Location home;
    private int targetRefresh;
    private int pathRefresh;
    private int attackCooldown;
    private int sonicCooldown;
    private int outOfReach;
    private LivingEntity observed;
    private int windup;
    private DarkSnifferRules.Wave wave;
    private int waveAge;
    private LivingEntity sonicVictim;
    private final Set<UUID> pulled = new HashSet<>();
    private int digCooldown;
    private Block digTarget;
    private int digTicks = -1;
    private int digSearch;
    private int ambientTime = -AMBIENT_INTERVAL;
    private double walked;
    private Location last;

    DarkSniffer(DarkSnifferKind kind, PolarBear bear, MobManager manager) {
        super(kind, bear, manager);
        this.bear = bear;
        this.snifferKind = kind;
        this.rules = kind.rules();
        this.config = kind.config();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.targetRefresh = random.nextInt(10);
        this.pathRefresh = random.nextInt(6);
        var pdc = bear.getPersistentDataContainer();
        String text = pdc.get(HOME, PersistentDataType.STRING);
        if (text != null) {
            String[] p = text.split(",");
            home = new Location(bear.getWorld(), Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } else {
            home = bear.getLocation();
            pdc.set(HOME, PersistentDataType.STRING, home.getX() + "," + home.getY() + "," + home.getZ());
        }
        digCooldown = pdc.getOrDefault(DIG_COOLDOWN, PersistentDataType.INTEGER, 0);
    }

    @Override
    protected Sound.Source source() {
        return Sound.Source.HOSTILE;
    }

    static boolean attackable(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
        }
        return target instanceof Sniffer && !DarkSnifferKind.ID.equals(MobManager.kindOf(target));
    }

    private boolean valid(LivingEntity target) {
        return target != null && target.isValid() && !target.isDead() && target.getWorld().equals(bear.getWorld())
                && attackable(target) && target.getLocation().distanceSquared(bear.getLocation()) <= rules.aggroRange() * rules.aggroRange();
    }

    /** Timers and sounds every tick; the behaviour itself runs in the AI tick ({@link #aiTick}). */
    @Override
    protected void tick() {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (sonicCooldown > 0) {
            sonicCooldown--;
        }
        if (digCooldown > 0) {
            digCooldown--;
        }
        sounds();
        if (wave == null && windup <= 0 && digTicks < 0 && !animations.isTriggered(LAYER)) {
            Vector v = bear.getVelocity();
            boolean moving = v.getX() * v.getX() + v.getZ() * v.getZ() > 1.0E-4 || bear.getPathfinder().hasPath();
            if (moving) {
                animations.play(LAYER, "walk", Math.max(0.6, Math.min(2.0, Math.sqrt(v.getX() * v.getX() + v.getZ() * v.getZ()) * 12)));
            } else {
                animations.stop(LAYER);
            }
        }
    }

    void aiTick() {
        World world = bear.getWorld();
        if (wave != null || windup > 0) {
            sonicTick();
            return;
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            bear.setTarget(null);
            idle();
            return;
        }
        LivingEntity target = bear.getTarget();
        if (!valid(target)) {
            target = null;
            bear.setTarget(null);
        }
        if (--targetRefresh <= 0) {
            targetRefresh = 10;
            target = chooseTarget();
            bear.setTarget(target);
        }
        if (target == null) {
            observed = null;
            outOfReach = 0;
            idle();
            return;
        }
        stopDigging();
        speed(config.getDouble("chase-movement-speed", 0.3));
        if (observed != target) {
            observed = target;
            outOfReach = 0;
        }
        double d2 = target.getLocation().distanceSquared(bear.getLocation());
        outOfReach = rules.inReach(d2) ? 0 : Math.min(rules.outOfReachTicks(), outOfReach + 1);
        if (config.getBoolean("sonic.enabled", true) && rules.sonicReady(sonicCooldown, outOfReach, d2, bear.hasLineOfSight(target))) {
            beginSonic(target);
            return;
        }
        bear.lookAt(target, 30f, 30f);
        if (--pathRefresh <= 0 || !bear.getPathfinder().hasPath()) {
            pathRefresh = 6;
            bear.getPathfinder().moveTo(target, config.getDouble("chase-speed", 1.2));
        }
        if (attackCooldown <= 0 && rules.inReach(d2)) {
            bear.attack(target);
            attackCooldown = rules.attackCooldown();
            outOfReach = 0;
            animations.trigger(LAYER, "attack");
        }
    }

    private LivingEntity chooseTarget() {
        double range2 = rules.aggroRange() * rules.aggroRange();
        Player player = null;
        double playerD = -1;
        for (Player p : bear.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(bear.getLocation());
            if (d <= range2 && attackable(p) && !p.isDead() && (playerD < 0 || d < playerD)) {
                player = p;
                playerD = d;
            }
        }
        Sniffer sniffer = null;
        double snifferD = -1;
        if (config.getBoolean("attack-sniffers", true)) {
            double r = rules.aggroRange();
            for (Entity e : bear.getNearbyEntities(r, r, r)) {
                if (e instanceof Sniffer s && attackable(s) && !s.isDead()) {
                    double d = s.getLocation().distanceSquared(bear.getLocation());
                    if (d <= range2 && (snifferD < 0 || d < snifferD)) {
                        sniffer = s;
                        snifferD = d;
                    }
                }
            }
        }
        return switch (DarkSnifferRules.chooseTarget(playerD, snifferD)) {
            case 1 -> player;
            case 2 -> sniffer;
            default -> null;
        };
    }

    private void speed(double value) {
        var attribute = bear.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null && attribute.getBaseValue() != value) {
            attribute.setBaseValue(value);
        }
    }

    // ---- idle: wander, go home, dig ------------------------------------------------------------------

    private void idle() {
        speed(config.getDouble("attributes.movement-speed", 0.1));
        if (digTicks >= 0) {
            digTick();
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double homeRadius = config.getDouble("home-radius", 100);
        Location here = bear.getLocation();
        if (here.getWorld().equals(home.getWorld()) && here.distanceSquared(home) > homeRadius * homeRadius) {
            if (!bear.getPathfinder().hasPath()) {
                bear.getPathfinder().moveTo(home, 1.0);
            }
            return;
        }
        if (digTarget != null) {
            approachDig();
            return;
        }
        if (config.getBoolean("dig.enabled", true) && digCooldown <= 0 && --digSearch <= 0) {
            digSearch = config.getInt("dig.search-interval-ticks", 200);
            digTarget = findSculk();
            if (digTarget != null) {
                return;
            }
        }
        if (!bear.getPathfinder().hasPath() && random.nextInt(30) == 0) {
            Location to = here.clone().add(random.nextInt(25) - 12, 0, random.nextInt(25) - 12);
            bear.getPathfinder().moveTo(to, 1.0);
        }
    }

    /** A vanilla sculk block within 8 blocks with room above (the original only digs sculk). */
    private Block findSculk() {
        Location at = bear.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            Block block = at.getWorld().getBlockAt(at.getBlockX() + random.nextInt(-8, 9), at.getBlockY() + random.nextInt(-3, 2),
                    at.getBlockZ() + random.nextInt(-8, 9));
            if (block.getType() == Material.SCULK && block.getRelative(0, 1, 0).isPassable()) {
                return block;
            }
        }
        return null;
    }

    private void approachDig() {
        if (digTarget.getType() != Material.SCULK) {
            digTarget = null;
            return;
        }
        Location center = digTarget.getLocation().add(0.5, 1, 0.5);
        if (center.distanceSquared(bear.getLocation()) <= 6.25) {
            bear.getPathfinder().stopPathfinding();
            bear.lookAt(center.clone().add(0, -1, 0));
            digTicks = 0;
            animations.play(LAYER, "dig", 1.0);
            playSound(Key.key("minecraft", "entity.sniffer.digging"), 1.0f, 1.0f);
        } else if (!bear.getPathfinder().hasPath() && !bear.getPathfinder().moveTo(center, 1.0)) {
            digTarget = null;
        }
    }

    private void digTick() {
        bear.getPathfinder().stopPathfinding();
        digTicks++;
        if (digTarget == null || digTarget.getType() != Material.SCULK) {
            stopDigging();
            return;
        }
        if (digTicks % 4 == 0) {
            Location p = digTarget.getLocation().add(0.5, 1.05, 0.5);
            bear.getWorld().spawnParticle(Particle.BLOCK, p, 6, 0.3, 0.05, 0.3, 0, digTarget.getBlockData());
        }
        if (digTicks >= config.getInt("dig.duration-ticks", 80)) {
            ItemStack seeds = snifferKind.items().create("sculkflower_seeds", 1);
            if (seeds != null) {
                bear.getWorld().dropItemNaturally(digTarget.getLocation().add(0.5, 1.1, 0.5), seeds);
            }
            playSound(Key.key("minecraft", "entity.sniffer.drop_seed"), 1.0f, 1.0f);
            digCooldown = config.getInt("dig.cooldown-ticks", 9600);
            stopDigging();
        }
    }

    private void stopDigging() {
        if (digTicks >= 0) {
            animations.stop(LAYER);
        }
        digTicks = -1;
        digTarget = null;
    }

    // ---- sound wave ----------------------------------------------------------------------------------

    private void beginSonic(LivingEntity target) {
        sonicVictim = target;
        windup = rules.sonicWindup();
        wave = null;
        waveAge = 0;
        sonicCooldown = rules.sonicCooldown();
        outOfReach = 0;
        pulled.clear();
        bear.getPathfinder().stopPathfinding();
        bear.setVelocity(new Vector());
        bear.lookAt(target, 30f, 30f);
        animations.play(LAYER, "sniff", 1.0);
    }

    private void sonicTick() {
        LivingEntity victim = sonicVictim;
        if (victim == null || !victim.isValid() || victim.isDead() || !attackable(victim)) {
            cancelSonic();
            return;
        }
        bear.getPathfinder().stopPathfinding();
        bear.setVelocity(new Vector(0, bear.getVelocity().getY(), 0));
        bear.lookAt(victim, 30f, 30f);
        if (windup > 0) {
            if (--windup == 0) {
                launch(victim);
            }
            return;
        }
        double[] previous = wave.position(waveAge, rules.waveSpeed());
        waveAge++;
        double[] now = wave.position(waveAge, rules.waveSpeed());
        World world = bear.getWorld();
        world.spawnParticle(Particle.SONIC_BOOM, now[0], now[1], now[2], 1, 0, 0, 0, 0);
        BoundingBox span = BoundingBox.of(new Vector(previous[0], previous[1], previous[2]), new Vector(now[0], now[1], now[2]))
                .expand(rules.hitRadius() + 2);
        for (Entity entity : world.getNearbyEntities(span)) {
            if (entity instanceof LivingEntity living && entity != bear && !living.isDead() && !pulled.contains(entity.getUniqueId())) {
                BoundingBox b = living.getBoundingBox();
                if (rules.hits(new double[] {b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()}, previous, now)) {
                    pull(living);
                }
            }
        }
        if (waveAge >= wave.flightTicks()) {
            cancelSonic();
        }
    }

    private void launch(LivingEntity victim) {
        Location eye = bear.getEyeLocation();
        Location target = victim.getEyeLocation();
        Vector v = victim.getVelocity();
        wave = bear.hasLineOfSight(victim) ? rules.aim(new double[] {eye.getX(), eye.getY(), eye.getZ()},
                new double[] {target.getX(), target.getY(), target.getZ()}, new double[] {v.getX(), 0, v.getZ()}) : null;
        if (wave == null) {
            cancelSonic();
            return;
        }
        waveAge = 0;
        playSound(Key.key("the_sift", "entity.dark_sniffer.sniff"), 2.5f, 0.78f);
    }

    private void pull(LivingEntity victim) {
        if (!pulled.add(victim.getUniqueId())) {
            return;
        }
        Location s = bear.getLocation();
        Location v = victim.getLocation();
        double[] push = rules.pull(new double[] {s.getX(), s.getY(), s.getZ()}, new double[] {v.getX(), v.getY(), v.getZ()});
        victim.setVelocity(victim.getVelocity().add(new Vector(push[0], push[1], push[2])));
    }

    private void cancelSonic() {
        windup = 0;
        wave = null;
        waveAge = 0;
        sonicVictim = null;
        pulled.clear();
        outOfReach = 0;
        animations.stop(LAYER);
    }

    // ---- sounds, damage, persistence -----------------------------------------------------------------

    private void sounds() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(1000) < ambientTime++) {
            ambientTime = -AMBIENT_INTERVAL;
            playSound(Key.key("minecraft", "entity.sniffer.idle"), 1.0f, voice(random));
        }
        Location now = bear.getLocation();
        if (last != null && bear.isOnGround() && now.getWorld().equals(last.getWorld())) {
            walked += Math.hypot(now.getX() - last.getX(), now.getZ() - last.getZ());
            if (walked >= 1.6) {
                walked = 0;
                playSound(Key.key("minecraft", "entity.sniffer.step"), 0.15f, 1.0f);
            }
        }
        last = now;
    }

    private static float voice(ThreadLocalRandom random) {
        return (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
    }

    @Override
    protected void hurt(EntityDamageEvent event) {
        if (bear.getHealth() - event.getFinalDamage() > 0) {
            playSound(Key.key("minecraft", "entity.sniffer.hurt"), 1.0f, voice(ThreadLocalRandom.current()));
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            LivingEntity attacker = damager instanceof Projectile p && p.getShooter() instanceof LivingEntity s ? s
                    : damager instanceof LivingEntity l ? l : null;
            if (attacker instanceof Player player && attackable(player) && bear.getWorld().getDifficulty() != Difficulty.PEACEFUL) {
                bear.setTarget(player);
            }
        }
    }

    @Override
    protected void died() {
        playSound(Key.key("minecraft", "entity.sniffer.death"), 1.0f, voice(ThreadLocalRandom.current()));
    }

    @Override
    protected void detached() {
        bear.getPersistentDataContainer().set(DIG_COOLDOWN, PersistentDataType.INTEGER, digCooldown);
    }
}
