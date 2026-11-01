package de.lamamc.sift.mob.singer;

import de.lamamc.sift.core.mob.BarterTable;
import de.lamamc.sift.core.mob.SingerRules;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.portal.SiftPortal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * One Singer, in one of two modes. Called ({@code summoned}): immobile and invulnerable in the portal, plays
 * appear → sing → disappear; during the song sound waves wake the sleeping consoles one by one, then the
 * portal counts as awake. Staying ({@code roaming}, from an egg or command): appears, then wanders within
 * the leash radius of where it appeared (never into passages lower than its height), trades soul blocks and
 * sinks into the ground if it gets stuck.
 */
final class Singer extends SiftMob {

    static final NamespacedKey MODE = new NamespacedKey("the_sift", "singer_mode");
    static final NamespacedKey PORTAL = new NamespacedKey("the_sift", "singer_portal");
    private static final NamespacedKey HOME = new NamespacedKey("the_sift", "singer_home");
    private static final NamespacedKey ADVANCEMENT = new NamespacedKey("the_sift", "the_sift/pitch_perfect");
    private static final String LAYER = "singer_sequence";
    private static final int AMBIENT_INTERVAL = 80;
    private static final Color WAVE_COLOR = Color.fromRGB(0x2FD3C9);

    enum Phase { APPEAR, ROAMING, BARTER, DISAPPEAR }

    private record Wave(Block console, Vector start, Vector end, int total, int[] age) {
    }

    private final WanderingTrader trader;
    private final SingerKind singerKind;
    private final SingerRules rules;
    private final boolean summoned;
    private final String portalId;
    private final List<Wave> waves = new ArrayList<>();
    private List<Block> targets;
    private boolean unlocked;
    private int tick;
    private Phase phase = Phase.APPEAR;
    private int phaseTick;
    private Location home;
    private UUID barterPlayer;
    private Location barterTarget;
    private Item pickupTarget;
    private int ambientTime = -AMBIENT_INTERVAL;
    private Location stuckCheck;
    private int stuckTicks;

    Singer(SingerKind kind, WanderingTrader trader, MobManager manager) {
        super(kind, trader, manager);
        this.trader = trader;
        this.singerKind = kind;
        this.rules = kind.rules();
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        this.summoned = "summoned".equals(pdc.get(MODE, PersistentDataType.STRING));
        this.portalId = pdc.get(PORTAL, PersistentDataType.STRING);
        String homeText = pdc.get(HOME, PersistentDataType.STRING);
        if (homeText != null) {
            String[] p = homeText.split(",");
            home = new Location(trader.getWorld(), Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } else {
            home = trader.getLocation();
            pdc.set(HOME, PersistentDataType.STRING, home.getX() + "," + home.getY() + "," + home.getZ());
        }
        if (summoned) {
            if (pdc.has(new NamespacedKey("the_sift", "singer_started"))) {
                // a called Singer is a one-off performance: reloaded later, it is simply gone
                Bukkit.getScheduler().runTask(manager.plugin(), trader::remove);
            }
            pdc.set(new NamespacedKey("the_sift", "singer_started"), PersistentDataType.BOOLEAN, true);
        }
        immaterial(true);
        animations.play(LAYER, "appear", 1.0);
        appearEffects();
    }

    private void immaterial(boolean immaterial) {
        trader.setAI(!immaterial);
        trader.setGravity(!immaterial);
        trader.setCollidable(!immaterial);
        trader.setInvulnerable(summoned);
    }

    boolean roaming() {
        return !summoned && phase == Phase.ROAMING;
    }

    Location home() {
        return home;
    }

    @Override
    protected void tick() {
        tick++;
        tickWaves();
        if (summoned) {
            tickSequence();
        } else {
            tickStaying();
        }
    }

    // ---- called Singer -------------------------------------------------------------------------------

    private void tickSequence() {
        trader.setBodyYaw(trader.getLocation().getYaw());
        SingerRules.Phase now = rules.phase(tick);
        SingerRules.Phase before = rules.phase(tick - 1);
        if (now == SingerRules.Phase.SING && before != SingerRules.Phase.SING) {
            animations.play(LAYER, "sing", 1.0);
            Location at = trader.getLocation();
            at.getWorld().playSound(Sound.sound(Key.key("the_sift", "entity.singer.sing"), Sound.Source.NEUTRAL,
                    (float) kind.config().getDouble("song.volume", 1000), 1.0f), at.getX(), at.getY(), at.getZ());
            prepareTargets();
        }
        if (now == SingerRules.Phase.SING) {
            int wave = rules.waveAt(tick);
            if (targets != null && wave >= 0 && wave < targets.size()) {
                launch(targets.get(wave));
            }
        }
        if (now == SingerRules.Phase.DISAPPEAR && before != SingerRules.Phase.DISAPPEAR) {
            finishUnlock();
            animations.play(LAYER, "disappear", 1.0);
            playSound("entity.singer.dig", 4.0f, 1.0f);
        }
        if (now == SingerRules.Phase.APPEAR && tick < 90 || now == SingerRules.Phase.DISAPPEAR && tick - rules.appearTicks() - rules.singTicks() < 90) {
            digParticles();
        }
        if (now == SingerRules.Phase.GONE) {
            finishUnlock();
            trader.remove();
        }
    }

    private Optional<SiftPortal> portal() {
        return portalId == null ? Optional.empty() : singerKind.portals().registry().get(trader.getWorld(), portalId);
    }

    /** Sleeping consoles of the portal in the original's order. */
    private void prepareTargets() {
        targets = new ArrayList<>();
        Optional<SiftPortal> portal = portal();
        if (portal.isEmpty()) {
            return;
        }
        World world = trader.getWorld();
        List<double[]> positions = new ArrayList<>();
        for (int[] c : portal.get().consoles()) {
            Block console = world.getBlockAt(c[0], c[1], c[2]);
            if (singerKind.consoles().mode(console) == de.lamamc.sift.world.portal.SonorousConsoles.Mode.HORN) {
                positions.add(new double[] {c[0] + 0.5, c[1] + 0.5, c[2] + 0.5});
            }
        }
        Location at = trader.getLocation();
        for (double[] p : SingerRules.order(positions, new double[] {at.getX(), at.getY(), at.getZ()}, at.getYaw())) {
            targets.add(world.getBlockAt((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2])));
        }
    }

    private void launch(Block console) {
        Location at = trader.getLocation();
        Vector start = new Vector(at.getX(), at.getY() + rules.headHeight(), at.getZ());
        Vector end = console.getLocation().add(0.5, 0.5, 0.5).toVector();
        waves.add(new Wave(console, start, end, rules.travelTicks(start.distance(end)), new int[] {0}));
    }

    private void tickWaves() {
        World world = trader.getWorld();
        for (var it = waves.iterator(); it.hasNext(); ) {
            Wave wave = it.next();
            int age = ++wave.age()[0];
            Vector point = wave.start().clone().add(wave.end().clone().subtract(wave.start()).multiply(Math.min(1.0, age / (double) wave.total())));
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 4, 0.08, 0.08, 0.08, 0,
                    new Particle.DustOptions(WAVE_COLOR, 1.4f), true);
            if (age >= wave.total()) {
                singerKind.consoles().wakeConsole(wave.console());
                it.remove();
            }
        }
        if (summoned && !unlocked && targets != null && !targets.isEmpty() && waves.isEmpty()
                && rules.phase(tick) == SingerRules.Phase.SING && rules.waveAt(tick) < 0 && allWavesSent()) {
            finishUnlock();
        }
    }

    private boolean allWavesSent() {
        int sing = tick - rules.appearTicks();
        int last = rules.waveTicks()[Math.min(targets.size(), rules.waveTicks().length) - 1];
        return sing > last;
    }

    /** All consoles awake: the portal counts as unlocked, players nearby get the hint. */
    private void finishUnlock() {
        if (unlocked || targets == null) {
            return;
        }
        unlocked = true;
        Optional<SiftPortal> portal = portal();
        if (portal.isEmpty() || portal.get().state() != de.lamamc.sift.core.portal.PortalState.LOCKED) {
            return;
        }
        singerKind.consoles().markUnlocked(trader.getWorld(), portal.get());
        double r2 = 48 * 48;
        for (Player player : trader.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(trader.getLocation()) <= r2) {
                singerKind.consoles().sendHint(player, portal.get());
            }
        }
    }

    private void digParticles() {
        Block below = trader.getLocation().getBlock().getRelative(0, -1, 0);
        if (!below.getType().isAir()) {
            Location at = trader.getLocation();
            trader.getWorld().spawnParticle(Particle.BLOCK, at.getX(), at.getY(), at.getZ(), 30, 0.35, 0.05, 0.35, 0,
                    below.getBlockData());
        }
    }

    private void appearEffects() {
        if (summoned) {
            playSound("entity.singer.emerge", 4.0f, 1.0f);
        } else {
            playSound("entity.singer.emerge", 1.0f, 1.0f);
            Location at = trader.getLocation();
            at.getWorld().playSound(Sound.sound(Key.key("the_sift", "the_sift_portal_open"), Sound.Source.BLOCK, 1.0f, 1.0f),
                    at.getX(), at.getY(), at.getZ());
        }
    }

    @Override
    protected void detached() {
        if (summoned && tick >= rules.appearTicks()) {
            finishUnlock(); // the song was heard: do not leave the portal half awake
        }
    }

    // ---- staying Singer ------------------------------------------------------------------------------

    private void setPhase(Phase next) {
        phase = next;
        phaseTick = 0;
    }

    private void tickStaying() {
        phaseTick++;
        switch (phase) {
            case APPEAR -> {
                if (phaseTick < 90) {
                    digParticles();
                }
                if (phaseTick >= rules.appearTicks()) {
                    immaterial(false);
                    setPhase(Phase.ROAMING);
                }
            }
            case ROAMING -> {
                ambient();
                boolean walking = trader.getPathfinder().hasPath() || horizontalSpeed2() > 9.0E-4;
                animations.play(LAYER, walking ? "walk" : "idle", 1.0);
                stuck();
            }
            case BARTER -> tickBarter();
            case DISAPPEAR -> {
                if (phaseTick == 1) {
                    immaterial(true);
                    animations.play(LAYER, "disappear", 1.0);
                    playSound("entity.singer.dig", 1.0f, 1.0f);
                    Location at = trader.getLocation();
                    at.getWorld().playSound(Sound.sound(Key.key("the_sift", "the_sift_portal_close"), Sound.Source.BLOCK, 1.0f, 1.0f),
                            at.getX(), at.getY(), at.getZ());
                }
                if (phaseTick < 90) {
                    digParticles();
                }
                if (phaseTick >= rules.disappearTicks()) {
                    trader.remove();
                }
            }
        }
    }

    private double horizontalSpeed2() {
        Vector v = trader.getVelocity();
        return v.getX() * v.getX() + v.getZ() * v.getZ();
    }

    private void ambient() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(1000) < ambientTime++) {
            ambientTime = -AMBIENT_INTERVAL;
            playSound("entity.singer.idle", 1.0f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        }
    }

    /** Stuck: wants to walk but has not moved half a block in the configured time → sinks into the ground. */
    private void stuck() {
        int limit = kind.config().getInt("stuck-seconds", 10) * 20;
        Location now = trader.getLocation();
        if (stuckCheck == null || !trader.getPathfinder().hasPath() || now.distanceSquared(stuckCheck) > 0.25) {
            stuckCheck = now;
            stuckTicks = 0;
            return;
        }
        if (++stuckTicks >= limit) {
            setPhase(Phase.DISAPPEAR);
        }
    }

    boolean canBarter() {
        return !summoned && phase == Phase.ROAMING && !trader.isDead();
    }

    /** A dropped soul block within reach of the trade (8 × 3 × 8 blocks). */
    Item pickupTarget() {
        if (pickupTarget != null && (!pickupTarget.isValid() || !isSoulBlock(pickupTarget.getItemStack())
                || pickupTarget.getLocation().distanceSquared(trader.getLocation()) > 64)) {
            pickupTarget = null;
        }
        if (pickupTarget == null && tick % 10 == 0) {
            double best = Double.MAX_VALUE;
            for (Entity nearby : trader.getNearbyEntities(8, 3, 8)) {
                if (nearby instanceof Item item && item.isValid() && isSoulBlock(item.getItemStack())) {
                    double d = item.getLocation().distanceSquared(trader.getLocation());
                    if (d < best) {
                        best = d;
                        pickupTarget = item;
                    }
                }
            }
        }
        return pickupTarget;
    }

    void pickUp(Item item) {
        if (!canBarter()) {
            return;
        }
        Player owner = item.getThrower() == null ? null : Bukkit.getPlayer(item.getThrower());
        ItemStack stack = item.getItemStack();
        stack.subtract(1);
        if (stack.isEmpty()) {
            item.remove();
        } else {
            item.setItemStack(stack);
        }
        pickupTarget = null;
        beginBarter(owner, owner != null ? owner.getLocation() : item.getLocation());
    }

    static boolean isSoulBlock(ItemStack stack) {
        return "soul_block".equals(de.lamamc.sift.item.SiftItems.idOf(stack));
    }

    private void beginBarter(Player player, Location target) {
        barterPlayer = player == null ? null : player.getUniqueId();
        barterTarget = target;
        trader.getPathfinder().stopPathfinding();
        setPhase(Phase.BARTER);
        animations.play(LAYER, "barter_hold", 1.0);
        if (player != null) {
            face(player.getLocation());
        }
        playSound("entity.singer.happy", 1.0f, 1.0f);
    }

    private void tickBarter() {
        trader.getPathfinder().stopPathfinding();
        Player player = barterPlayer == null ? null : Bukkit.getPlayer(barterPlayer);
        if (player != null && player.getWorld().equals(trader.getWorld()) && player.getLocation().distanceSquared(trader.getLocation()) <= 256) {
            barterTarget = player.getLocation();
            face(barterTarget);
        }
        int hold = kind.config().getInt("barter.hold-ticks", 40);
        if (phaseTick >= hold) {
            reward(player);
            animations.play(LAYER, "idle", 1.0);
            setPhase(Phase.ROAMING);
        }
    }

    private void reward(Player player) {
        BarterTable.Reward reward = singerKind.barter().roll(ThreadLocalRandom.current());
        ItemStack stack = rewardStack(reward);
        if (stack != null) {
            Location at = trader.getLocation();
            Vector direction = (barterTarget == null ? at.getDirection() : barterTarget.toVector().subtract(at.toVector())).setY(0);
            if (direction.lengthSquared() < 0.001) {
                direction = at.getDirection().setY(0);
            }
            direction.normalize();
            Location drop = at.clone().add(direction.getX() * 0.6, 1.2, direction.getZ() * 0.6);
            Vector throwing = direction.clone().multiply(0.3).add(new Vector(0, 0.2, 0));
            trader.getWorld().dropItem(drop, stack, item -> item.setVelocity(throwing));
        }
        if (player != null) {
            Advancement advancement = Bukkit.getAdvancement(ADVANCEMENT);
            if (advancement != null) {
                var progress = player.getAdvancementProgress(advancement);
                progress.getRemainingCriteria().forEach(progress::awardCriteria);
            }
        }
    }

    private ItemStack rewardStack(BarterTable.Reward reward) {
        String id = reward.item();
        if (id.equals("#wool")) {
            List<Material> wool = new ArrayList<>(Tag.WOOL.getValues());
            return ItemStack.of(wool.get(ThreadLocalRandom.current().nextInt(wool.size())), reward.count());
        }
        if (id.startsWith("the_sift:")) {
            return singerKind.items().create(id.substring("the_sift:".length()), reward.count());
        }
        Material material = Material.matchMaterial(id);
        return material == null ? null : ItemStack.of(material, reward.count());
    }

    private void face(Location target) {
        Location at = trader.getLocation();
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - at.getX()), target.getZ() - at.getZ()));
        trader.setRotation(yaw, 0);
        trader.setBodyYaw(yaw);
    }

    @Override
    protected boolean interact(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (!isSoulBlock(held)) {
            return false;
        }
        if (canBarter()) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract(1);
            }
            beginBarter(player, player.getLocation());
        }
        return true;
    }

    @Override
    protected void hurt(EntityDamageEvent event) {
        if (!summoned && trader.getHealth() - event.getFinalDamage() > 0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            playSound("entity.singer.hurt", 1.0f, (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        }
    }

    @Override
    protected void died() {
        if (!summoned) {
            playSound("entity.singer.death", 1.0f, 1.0f);
        }
    }
}
