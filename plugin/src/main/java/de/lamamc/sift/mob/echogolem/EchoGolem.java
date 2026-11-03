package de.lamamc.sift.mob.echogolem;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.core.mob.EchoGolemMission;
import de.lamamc.sift.core.mob.EchoGolemMission.Phase;
import de.lamamc.sift.core.mob.EchoGolemMission.State;
import de.lamamc.sift.core.mob.EchoGolemRules;
import de.lamamc.sift.core.mob.GolemBinding;
import de.lamamc.sift.core.mob.GolemHandover;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.gen.SoulCanyonLandmarks;
import de.lamamc.sift.world.gen.SoulCanyonLandmarks.Pos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * One Echo Golem. While searching it explores along a slowly drifting heading and every couple of seconds looks
 * for the nearest canyon with soul blocks; it walks to the canyon's approach point, enters, descends to the
 * midpoint and goes for the nearest free soul block. With the block in its hands it climbs out (midpoint, then
 * entrance, otherwise any reachable surface spot) and delivers it: unbonded to a Singer it calls, bonded to its
 * player. Any hit or a right-click makes it drop the block and sit for a while. The original's 2000 lines of
 * route recovery are simplified to the same phases, thresholds and fallbacks.
 */
public final class EchoGolem extends SiftMob {

    private static final NamespacedKey STATE = new NamespacedKey("the_sift", "echo_golem_state");
    /** The bonded player, like the original's NBT fields bonded_player / bonded_player_name. */
    static final NamespacedKey BONDED_PLAYER = new NamespacedKey("the_sift", "bonded_player");
    static final NamespacedKey BONDED_PLAYER_NAME = new NamespacedKey("the_sift", "bonded_player_name");
    private static final NamespacedKey FRIEND = new NamespacedKey("the_sift", "the_sift/friend_of_the_sift");
    private static final NamespacedKey FATHER = new NamespacedKey("the_sift", "the_sift/like_father_and_son");
    private static final String MISSION = "mission";
    private static final String SOUL = "soul";
    private static final String SOUL_ID = SoulCanyonLandmarks.SOUL_BLOCK_ID;

    private final Sheep sheep;
    private final EchoGolemKind golemKind;
    private final EchoGolemRules rules;
    private final EchoGolemMission mission;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    private boolean hasSoul;
    private Pos soulTarget;
    private int soulPathFailures;
    private Pos cluster;
    private Pos entrance;
    private Pos midpoint;
    private boolean entered;
    private boolean midpointPassed;
    private int entryAttempts;
    private int depthAttempts;
    private int emptyChecks;
    private final Map<Pos, Long> unavailable = new HashMap<>();
    private Pos ignored;
    private long ignoredUntil;

    private double heading;
    private int turnTimer;
    private Location exploreTarget;
    private int exploreAge;

    private Location navGoal;
    private int routeFailures;
    private int repathCooldown;
    private Location sample;
    private int stalls;

    private int scanCooldown;
    private long nextPhysicalScan;
    private int canyonCooldown;
    private boolean scannedThisTick;

    private SiftMob singer;
    private int singerCooldown;
    private UUID owner;
    private String ownerName;
    private Location anchor;
    private Location patrolTarget;
    private int patrolCooldown;
    private Location exitOverride;
    private long nextExitSearch;
    private int exitCursor;
    private long nextPath;

    private double walked;
    private Location last;
    /** For /sift debug golems: what happened at the last handover and the last drop, with time and reason. */
    private String lastHandover;
    private String lastRelease;

    EchoGolem(EchoGolemKind kind, Sheep sheep, MobManager manager) {
        super(kind, sheep, manager);
        this.sheep = sheep;
        this.golemKind = kind;
        this.rules = kind.rules();
        this.mission = new EchoGolemMission(rules);
        heading = random.nextDouble() * Math.PI * 2;
        turnTimer = 900 + random.nextInt(901);
        scanCooldown = 1 + random.nextInt(20);
        canyonCooldown = 1 + random.nextInt(45);
        load(sheep.getPersistentDataContainer());
    }

    // ---- public API for the Singer and the light ----------------------------------------------------------

    public boolean hasSoul() {
        return hasSoul;
    }

    /** Bonded golems bring their block to their player, never to a Singer. */
    public boolean canInteractWithSinger() {
        return owner == null;
    }

    public Sheep entity() {
        return sheep;
    }

    /** The Singer is close enough: stop and bow. */
    public void beginBow() {
        stop();
        mission.set(State.BOW);
        animations.trigger(MISSION, "bow");
    }

    /** At the Singer's tick 22: hand over the block. False when there is nothing to hand over. */
    public boolean transferSoulToSinger() {
        if (!hasSoul) {
            return false;
        }
        hasSoul = false;
        golemKind.toSingers.incrementAndGet();
        award(FATHER, 16);
        playSound("entity.echo_golem.item_drop", 1.0f, 0.92f);
        singer = null;
        forgetCanyon(rules.ignoreCanyonTicks());
        anchor = null;
        mission.set(Phase.EXPLORE);
        if (mission.state() != State.BOW) {
            mission.set(State.SEARCH);
        }
        return true;
    }

    /** At the Singer's tick 48 (or when the exchange broke off). */
    public void finishBow() {
        if (hasSoul) {
            mission.startCarry(onSurface());
        } else {
            mission.resumeSearch();
        }
    }

    // ---- ticks --------------------------------------------------------------------------------------------

    @Override
    protected void tick() {
        animate();
        sounds();
        GolemLight light = golemKind.light();
        if (light != null) {
            light.update(this, hasSoul);
        }
    }

    @Override
    protected String variant() {
        return hasSoul && golemKind.config().getBoolean("glow.model", true) ? "echo_golem_glow" : "echo_golem";
    }

    private void animate() {
        animations.play(SOUL, hasSoul ? "soul_visible" : "soul_hidden", 1.0);
        if (animations.isTriggered(MISSION)) {
            return;
        }
        Vector v = sheep.getVelocity();
        boolean moving = v.getX() * v.getX() + v.getZ() * v.getZ() > 0.0012 || sheep.getPathfinder().hasPath();
        switch (mission.state()) {
            case SIT -> animations.play(MISSION, "sit", 1.0);
            case CARRY -> animations.play(MISSION, moving ? "carry_walk" : "carry_idle", 1.0);
            case SEARCH -> animations.play(MISSION, moving ? "walk" : "idle", 1.0);
            default -> animations.play(MISSION, hasSoul ? "carry_idle" : "idle", 1.0);
        }
    }

    private void sounds() {
        Location now = sheep.getLocation();
        if (last != null && sheep.isOnGround() && now.getWorld().equals(last.getWorld())) {
            walked += Math.hypot(now.getX() - last.getX(), now.getZ() - last.getZ());
            if (walked >= 1.4) {
                walked = 0;
                playSound("entity.echo_golem.step", 0.62f, 0.94f + random.nextFloat() * 0.12f);
            }
        }
        last = now;
    }

    void aiTick() {
        long now = now();
        scannedThisTick = false;
        if (ignored != null && now >= ignoredUntil) {
            ignored = null;
        }
        Player deliveryTo = deliveryPlayer();
        boolean ownerInReach = deliveryTo != null && deliveryTo.getLocation().distanceSquared(sheep.getLocation()) <= 4.5 * 4.5;
        EchoGolemMission.Step step = mission.tick(hasSoul, owner != null, ownerInReach);
        switch (mission.state()) {
            case SEARCH -> search();
            case PICKUP -> pickup(step);
            case CARRY -> carry();
            case BOW -> bow(step);
            case SIT -> {
                stop();
                if (step == EchoGolemMission.Step.END) {
                    mission.resumeSearch();
                }
            }
        }
        watchdog();
        assistStep();
    }

    // ---- search -------------------------------------------------------------------------------------------

    private void search() {
        if (hasSoul) {
            mission.startCarry(onSurface());
            return;
        }
        if (soulTarget != null && !isSoul(soulTarget)) {
            golemKind.failures().forget(pack(soulTarget));
            releaseTarget();
            scanCooldown = 2 + random.nextInt(5);
        }
        if (--scanCooldown <= 0) {
            scanCooldown = rules.scanInterval();
            scannedThisTick = true;
            boolean hasTarget = soulTarget != null;
            Pos found = findSoul(hasTarget ? rules.targetScanRadius() : rules.scanRadius(),
                    hasTarget ? rules.targetScanHeight() : rules.scanHeight());
            if (found != null && (soulTarget == null
                    || !found.equals(soulTarget) && distance2(found) + 1 < distance2(soulTarget))) {
                claim(found);
                depthAttempts = 0;
                emptyChecks = 0;
                mission.set(Phase.SEEK_SOUL);
                return;
            }
        }
        if (soulTarget != null) {
            mission.set(Phase.SEEK_SOUL);
        }
        switch (mission.phase()) {
            case EXPLORE -> exploreRoute();
            case APPROACH_CANYON -> approachCanyon();
            case ENTER_CANYON -> enterCanyon();
            case DESCEND_CANYON -> descendCanyon();
            case SEEK_SOUL -> seekSoul();
            case LEAVE_EMPTY_CANYON -> leaveEmptyCanyon();
            default -> mission.set(entered && cluster != null ? Phase.SEEK_SOUL : Phase.EXPLORE);
        }
    }

    private void exploreRoute() {
        if (--canyonCooldown <= 0) {
            canyonCooldown = rules.landmarkInterval();
            if (acquireCanyon()) {
                mission.set(Phase.APPROACH_CANYON);
                return;
            }
        }
        explore(rules.speeds().explore());
    }

    /** The nearest active canyon (soul blocks loaded and present) that is not avoided; distant ones only steer. */
    private boolean acquireCanyon() {
        SoulCanyonLandmarks landmarks = golemKind.landmarks();
        if (landmarks == null) {
            return false;
        }
        Location at = sheep.getLocation();
        long now = now();
        unavailable.values().removeIf(until -> until <= now);
        Pos best = null;
        double bestDistance = rules.landmarkRange() * rules.landmarkRange();
        for (Pos c : landmarks.all(SoulCanyonLandmarks.Kind.CLUSTER)) {
            if (unavailable.containsKey(c) || c.equals(ignored)) {
                continue;
            }
            double d = c.horizontalDistanceSquared(at.getX(), at.getZ());
            if (d < bestDistance && activeCanyon(landmarks, c)) {
                bestDistance = d;
                best = c;
            }
        }
        if (best == null) {
            return false;
        }
        Pos foundEntrance = landmarks.entranceOf(best);
        if (foundEntrance == null) {
            unavailable.put(best, now + rules.blockedCanyonTicks());
            return false;
        }
        Location approach = approachPoint(best, foundEntrance);
        if (horizontal2(approach) > rules.distantCanyon() * rules.distantCanyon()) {
            heading = Math.atan2(approach.getZ() - at.getZ(), approach.getX() - at.getX());
            exploreTarget = null;
            return false;
        }
        cluster = best;
        entrance = foundEntrance;
        midpoint = landmarks.midpointOf(best);
        entered = false;
        midpointPassed = false;
        entryAttempts = 0;
        depthAttempts = 0;
        emptyChecks = 0;
        resetRoute();
        return true;
    }

    private boolean activeCanyon(SoulCanyonLandmarks landmarks, Pos c) {
        long now = now();
        for (Pos soul : landmarks.near(SoulCanyonLandmarks.Kind.SOUL_BLOCK, c.x() + 0.5, c.y(), c.z() + 0.5, 38, 28)) {
            if (isSoul(soul) && !golemKind.failures().rejected(pack(soul), now)) {
                return true;
            }
        }
        return false;
    }

    /** Seven blocks outside the entrance, away from the souls, on the ground (the entrance itself when close). */
    private Location approachPoint(Pos c, Pos e) {
        World world = sheep.getWorld();
        double dx = e.x() - c.x();
        double dz = e.z() - c.z();
        double length = Math.hypot(dx, dz);
        if (length < 0.01) {
            return center(e);
        }
        int x = (int) Math.round(e.x() + dx / length * rules.approachOffset());
        int z = (int) Math.round(e.z() + dz / length * rules.approachOffset());
        int y = world.isChunkLoaded(x >> 4, z >> 4) ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : e.y();
        Location point = new Location(world, x + 0.5, y, z + 0.5);
        return horizontal2(point) <= 16 ? center(e) : point;
    }

    private void approachCanyon() {
        if (cluster == null || entrance == null) {
            clearRoute();
            return;
        }
        Location approach = approachPoint(cluster, entrance);
        if (near(approach, 3.5, 5.0)) {
            mission.set(Phase.ENTER_CANYON);
            resetRoute();
            return;
        }
        navigate(approach, rules.speeds().approach(), 2.0, true);
        if (routeFailures >= 8) {
            abandonCanyon(rules.blockedCanyonTicks());
        }
    }

    private void enterCanyon() {
        if (entrance == null) {
            clearRoute();
            return;
        }
        if (near(center(entrance), 3.5, 4.0)) {
            entered = true;
            entryAttempts = 0;
            mission.set(midpoint == null ? Phase.SEEK_SOUL : Phase.DESCEND_CANYON);
            resetRoute();
            return;
        }
        navigate(center(entrance), rules.speeds().enter(), 2.0, false);
        if (routeFailures >= 12) {
            if (++entryAttempts >= 2) {
                abandonCanyon(rules.blockedCanyonTicks());
            } else {
                mission.set(Phase.APPROACH_CANYON);
                resetRoute();
            }
        }
    }

    private void descendCanyon() {
        if (midpoint == null || near(center(midpoint), 3.0, 4.0)) {
            mission.set(Phase.SEEK_SOUL);
            resetRoute();
            return;
        }
        navigate(center(midpoint), rules.speeds().descend(), 2.0, false);
        if (routeFailures >= 12) {
            if (++entryAttempts >= 2) {
                markUnavailable(rules.blockedCanyonTicks());
                mission.set(Phase.LEAVE_EMPTY_CANYON);
            } else {
                entered = false;
                mission.set(Phase.APPROACH_CANYON);
            }
            resetRoute();
        }
    }

    private void seekSoul() {
        if (soulTarget != null) {
            if (canReachWithHands(soulTarget)) {
                stop();
                mission.set(State.PICKUP);
                animations.trigger(MISSION, "pickup");
                return;
            }
            Location above = new Location(sheep.getWorld(), soulTarget.x() + 0.5, soulTarget.y() + 1, soulTarget.z() + 0.5);
            if (!navigate(above, rules.speeds().soul(), Math.sqrt(3.4), false) && navGoal == null) {
                if (++soulPathFailures >= 3) {
                    reject(soulTarget, 100);
                }
            }
            if (routeFailures >= 8) {
                reject(soulTarget, 200);
            }
            return;
        }
        if (cluster == null) {
            mission.set(Phase.EXPLORE);
            return;
        }
        if (!entered) {
            mission.set(Phase.APPROACH_CANYON);
            return;
        }
        Location center = new Location(sheep.getWorld(), cluster.x() + 0.5, cluster.y() + 1, cluster.z() + 0.5);
        if (near(center, 11, 10)) {
            if (scannedThisTick && ++emptyChecks >= 3) {
                markUnavailable(rules.emptyCanyonTicks());
                mission.set(Phase.LEAVE_EMPTY_CANYON);
                resetRoute();
            }
            return;
        }
        navigate(center, rules.speeds().enter(), 3.0, true);
        if (routeFailures >= 12) {
            if (++depthAttempts >= 3) {
                markUnavailable(rules.emptyCanyonTicks());
                mission.set(Phase.LEAVE_EMPTY_CANYON);
            } else {
                entered = false;
                mission.set(Phase.APPROACH_CANYON);
            }
            resetRoute();
        }
    }

    private void leaveEmptyCanyon() {
        if (onSurface()) {
            clearRoute();
            return;
        }
        climbOut(0.86);
    }

    // ---- soul blocks --------------------------------------------------------------------------------------

    /** Nearest free soul block: indexed ones plus, every 2 s, a scan of the blocks around it. */
    private Pos findSoul(double h, double v) {
        SoulCanyonLandmarks landmarks = golemKind.landmarks();
        Location at = sheep.getLocation();
        List<Pos> candidates = new ArrayList<>();
        if (landmarks != null) {
            candidates.addAll(landmarks.near(SoulCanyonLandmarks.Kind.SOUL_BLOCK, at.getX(), at.getY(), at.getZ(), h, v));
        }
        long now = now();
        if (now >= nextPhysicalScan) {
            nextPhysicalScan = now + 40;
            physicalScan(candidates, (int) Math.min(h, 12), (int) Math.min(v, 8));
        }
        Pos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Pos pos : candidates) {
            if (!isSoul(pos)) {
                golemKind.failures().forget(pack(pos));
                continue;
            }
            if (reservedByOther(pos) || golemKind.failures().rejected(pack(pos), now)) {
                continue;
            }
            double d = distance2(pos);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos;
            }
        }
        return best;
    }

    private void physicalScan(List<Pos> into, int radius, int height) {
        World world = sheep.getWorld();
        Location at = sheep.getLocation();
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        int found = 0;
        for (int r = 0; r <= radius && found < 20; r++) {
            for (int dx = -r; dx <= r && found < 20; dx++) {
                for (int dz = -r; dz <= r && found < 20; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || dx * dx + dz * dz > radius * radius) {
                        continue;
                    }
                    if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) {
                        continue;
                    }
                    for (int i = 0; i <= height * 2; i++) {
                        int dy = (i + 1) / 2 * (i % 2 == 0 ? 1 : -1);
                        int y = cy + dy;
                        if (world.getBlockAt(cx + dx, y, cz + dz).getType() == Material.MUSHROOM_STEM && isSoul(cx + dx, y, cz + dz)) {
                            Pos pos = new Pos(cx + dx, y, cz + dz);
                            if (!into.contains(pos)) {
                                into.add(pos);
                                found++;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSoul(Pos pos) {
        return isSoul(pos.x(), pos.y(), pos.z());
    }

    private boolean isSoul(int x, int y, int z) {
        World world = sheep.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        CustomBlock custom = golemKind.blocks().of(world.getBlockData(x, y, z));
        return custom != null && SOUL_ID.equals(custom.id());
    }

    private void claim(Pos pos) {
        releaseTarget();
        soulTarget = pos;
        soulPathFailures = 0;
        golemKind.reservations().put(pack(pos), sheep.getUniqueId());
        resetRoute();
    }

    private void releaseTarget() {
        if (soulTarget != null) {
            golemKind.reservations().remove(pack(soulTarget), sheep.getUniqueId());
        }
        soulTarget = null;
        soulPathFailures = 0;
    }

    private boolean reservedByOther(Pos pos) {
        UUID holder = golemKind.reservations().get(pack(pos));
        if (holder == null || holder.equals(sheep.getUniqueId())) {
            return false;
        }
        Entity entity = Bukkit.getEntity(holder);
        if (entity != null && golemKind.manager().mob(entity) instanceof EchoGolem other && pos.equals(other.soulTarget)) {
            return true;
        }
        golemKind.reservations().remove(pack(pos));
        return false;
    }

    private void reject(Pos pos, int ticks) {
        golemKind.failures().fail(pack(pos), now(), ticks);
        releaseTarget();
        scanCooldown = 2 + random.nextInt(5);
        resetRoute();
    }

    /** Gap between body and block at most the hand reach, and a free line from the body to the block's top. */
    private boolean canReachWithHands(Pos pos) {
        BoundingBox body = sheep.getBoundingBox();
        BoundingBox block = new BoundingBox(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1);
        double gx = Math.max(0, Math.max(block.getMinX() - body.getMaxX(), body.getMinX() - block.getMaxX()));
        double gy = Math.max(0, Math.max(block.getMinY() - body.getMaxY(), body.getMinY() - block.getMaxY()));
        double gz = Math.max(0, Math.max(block.getMinZ() - body.getMaxZ(), body.getMinZ() - block.getMaxZ()));
        if (gx * gx + gy * gy + gz * gz > rules.handReach() * rules.handReach()) {
            return false;
        }
        Vector target = new Vector(pos.x() + 0.5, pos.y() + 0.85, pos.z() + 0.5);
        Vector from = new Vector(Math.clamp(target.getX(), body.getMinX() + 0.01, body.getMaxX() - 0.01),
                Math.clamp(target.getY(), body.getMinY() + 0.1, body.getMaxY() - 0.1),
                Math.clamp(target.getZ(), body.getMinZ() + 0.01, body.getMaxZ() - 0.01));
        Vector direction = target.clone().subtract(from);
        double length = direction.length();
        if (length < 0.01) {
            return true;
        }
        RayTraceResult hit = sheep.getWorld().rayTraceBlocks(from.toLocation(sheep.getWorld()), direction.normalize(), length,
                FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null
                || hit.getHitBlock().getX() == pos.x() && hit.getHitBlock().getY() == pos.y() && hit.getHitBlock().getZ() == pos.z();
    }

    private void pickup(EchoGolemMission.Step step) {
        stop();
        if (soulTarget != null) {
            face(center(soulTarget));
        }
        if (step == EchoGolemMission.Step.TAKE_SOUL) {
            if (soulTarget == null || !isSoul(soulTarget) || !mayTake()) {
                if (soulTarget != null && isSoul(soulTarget)) {
                    reject(soulTarget, rules.blockedCanyonTicks()); // not allowed to take it here
                } else {
                    releaseTarget();
                }
                mission.set(State.SEARCH);
                mission.set(entered && cluster != null ? Phase.SEEK_SOUL : Phase.EXPLORE);
                return;
            }
            golemKind.failures().forget(pack(soulTarget));
            sheep.getWorld().getBlockAt(soulTarget.x(), soulTarget.y(), soulTarget.z()).setType(Material.AIR, false);
            SoulCanyonLandmarks landmarks = golemKind.landmarks();
            if (landmarks != null) {
                landmarks.forget(SoulCanyonLandmarks.Kind.SOUL_BLOCK, soulTarget.x(), soulTarget.y(), soulTarget.z());
            }
            releaseTarget();
            hasSoul = true;
            golemKind.taken.incrementAndGet();
            if (anchor == null) {
                anchor = entrance != null ? center(entrance) : sheep.getLocation();
            }
            playSound("entity.echo_golem.item_get", 1.0f, 0.96f);
        } else if (step == EchoGolemMission.Step.START_CARRY) {
            midpointPassed = !needsMidpoint();
            if (hasSoul) {
                mission.startCarry(onSurface());
            } else {
                mission.resumeSearch();
            }
        }
    }

    /** Taking blocks: only in the Sift, switch pickup.enabled, and the gamerule mob_griefing like every Sift mob. */
    private boolean mayTake() {
        World world = sheep.getWorld();
        Boolean griefing = world.getGameRuleValue(GameRules.MOB_GRIEFING);
        return golemKind.config().getBoolean("pickup.enabled", true) && SiftWorlds.isSift(world) && (griefing == null || griefing);
    }

    // ---- carrying and delivering --------------------------------------------------------------------------

    private void carry() {
        if (!hasSoul) {
            mission.resumeSearch();
            return;
        }
        if (mission.phase() != Phase.EXIT_CANYON && mission.phase() != Phase.DELIVER) {
            mission.set(onSurface() ? Phase.DELIVER : Phase.EXIT_CANYON);
        }
        if (mission.phase() == Phase.EXIT_CANYON) {
            if (onSurface()) {
                entered = false;
                mission.set(Phase.DELIVER);
                resetRoute();
                return;
            }
            if (midpoint != null && !midpointPassed) {
                if (near(center(midpoint), 3.0, 4.0)) {
                    midpointPassed = true;
                    resetRoute();
                } else {
                    navigate(center(midpoint), 0.84, 2.0, false);
                    if (routeFailures >= 12) {
                        midpointPassed = true;
                        resetRoute();
                    }
                    return;
                }
            }
            climbOut(rules.speeds().exit());
            return;
        }
        if (owner != null) {
            deliverToOwner();
        } else {
            deliverToSinger();
        }
    }

    /** Out of the canyon: over the entrance, otherwise to any reachable open surface spot, otherwise upwards. */
    private void climbOut(double speed) {
        if (entrance == null && golemKind.landmarks() != null) {
            Location at = sheep.getLocation();
            entrance = golemKind.landmarks().nearest(SoulCanyonLandmarks.Kind.ENTRANCE, at.getX(), at.getY(), at.getZ(), 112);
        }
        if (exitOverride != null) {
            if (navigate(exitOverride, speed, 2.0, false) && routeFailures < 12) {
                return;
            }
            exitOverride = null;
        }
        if (entrance != null && routeFailures < 6) {
            navigate(center(entrance), speed, 2.0, false);
            return;
        }
        Location exit = surfaceExit();
        if (exit != null) {
            exitOverride = exit;
            resetRoute();
            return;
        }
        // emergency ascent: walk out along the heading, upwards
        Location at = sheep.getLocation();
        double x = at.getX() + Math.cos(heading) * 18;
        double z = at.getZ() + Math.sin(heading) * 18;
        World world = sheep.getWorld();
        if (world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            int y = Math.max(at.getBlockY() + 8, world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            navigate(new Location(world, x, y, z), 0.84, 4.0, true);
        }
        if (routeFailures >= 12) {
            heading += (random.nextBoolean() ? 1 : -1) * (0.65 + random.nextDouble() * 0.55);
            routeFailures = 6;
        }
    }

    /**
     * Any reachable open surface spot 8–28 blocks away (findReachableSurfaceExit of the original): one candidate
     * per try, a try every 5 ticks per golem with the separate exit budget, cycling through the 32 candidates.
     */
    private Location surfaceExit() {
        long now = now();
        if (now < nextExitSearch || !golemKind.takeExitBudget(now)) {
            return null;
        }
        nextExitSearch = now + 5;
        World world = sheep.getWorld();
        Location at = sheep.getLocation();
        double minY = entrance != null ? entrance.y() - 2 : at.getY() + 2;
        int[] radii = {8, 14, 20, 28};
        for (int tries = 0; tries < 32; tries++) {
            int index = exitCursor++ % 32;
            int radius = radii[index / 8];
            double angle = heading + (index % 8) * Math.PI / 4;
            int x = (int) Math.floor(at.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(at.getZ() + Math.sin(angle) * radius);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            if (y < minY) {
                continue;
            }
            Location spot = new Location(world, x + 0.5, y, z + 0.5);
            var path = sheep.getPathfinder().findPath(spot);
            if (path != null && path.canReachFinalPoint() && path.getFinalPoint() != null
                    && path.getFinalPoint().distanceSquared(spot) <= 4) {
                return spot;
            }
            return null; // one path per try
        }
        return null;
    }

    private void deliverToOwner() {
        Player player = deliveryPlayer();
        if (player != null && player.getLocation().distanceSquared(sheep.getLocation()) <= rules.ownerRange() * rules.ownerRange()) {
            patrolTarget = null;
            double d2 = player.getLocation().distanceSquared(sheep.getLocation());
            if (d2 <= rules.bowDistance() * rules.bowDistance() && sheep.isOnGround()
                    && Math.abs(player.getLocation().getY() - sheep.getLocation().getY()) <= 1.25) {
                stop();
                face(player.getLocation());
                mission.set(State.BOW);
                animations.trigger(MISSION, "bow");
                return;
            }
            navigate(player.getLocation(), rules.speeds().owner(), 2.5, true);
            return;
        }
        patrol();
    }

    /** A bonded golem whose player is far away walks around its anchor (the canyon entrance). */
    private void patrol() {
        Location home = anchor != null ? anchor : entrance != null ? center(entrance) : sheep.getLocation();
        if (horizontal2(home) > rules.patrolLeash() * rules.patrolLeash()) {
            navigate(home, rules.speeds().patrolReturn(), 4.0, true);
            return;
        }
        if (--patrolCooldown <= 0 || patrolTarget == null || !sheep.getPathfinder().hasPath()) {
            patrolCooldown = 50 + random.nextInt(51);
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = rules.patrolMin() + random.nextInt(Math.max(1, rules.patrolMax() - rules.patrolMin() + 1));
            int x = (int) Math.floor(home.getX() + Math.cos(angle) * radius);
            int z = (int) Math.floor(home.getZ() + Math.sin(angle) * radius);
            World world = sheep.getWorld();
            patrolTarget = world.isChunkLoaded(x >> 4, z >> 4)
                    ? new Location(world, x + 0.5, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + 0.5)
                    : home;
            navGoal = null;
        }
        navigate(patrolTarget, rules.speeds().patrol(), 3.0, true);
    }

    private void deliverToSinger() {
        if (singer != null && (singer.dying() || !singer.base().isValid())) {
            singer = null;
        }
        EchoGolemKind.SingerLink link = golemKind.singers();
        if (singer == null && link != null && --singerCooldown <= 0) {
            singerCooldown = rules.singerInterval();
            singer = link.request(this);
        }
        if (singer != null && link != null) {
            link.offer(singer, this);
            navigate(singer.base().getLocation(), rules.speeds().singer(), 2.0, true);
            return;
        }
        explore(rules.speeds().explore());
    }

    private void bow(EchoGolemMission.Step step) {
        stop();
        Player player = ownerPlayer();
        if (owner != null && player != null) {
            face(player.getLocation());
        } else if (singer != null && singer.base().isValid()) {
            face(singer.base().getLocation());
        }
        switch (step) {
            case GIVE_TO_OWNER -> {
                if (!handOver(player, "Verbeugung")) {
                    mission.startCarry(onSurface()); // kept: look for the player again or patrol
                }
            }
            case RESUME_CARRY -> mission.startCarry(onSurface());
            case END -> {
                if (hasSoul) {
                    mission.startCarry(onSurface());
                } else {
                    mission.resumeSearch();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Hands the soul block to the bonded player (bow at tick 26, or the owner's right-click): checks first
     * ({@link GolemHandover}), then a fresh soul block into the inventory, the rest in front of the player's feet
     * (owned by them, hint in the action bar), and only then the advancement, the sound and losing the load.
     * Returns false when the golem keeps the block.
     */
    private boolean handOver(Player player, String how) {
        GolemHandover.Result result = GolemHandover.decide(hasSoul, player != null && player.isOnline(),
                player != null && player.getUniqueId().equals(owner), player != null && player.getWorld().equals(sheep.getWorld()),
                player == null || !player.getWorld().equals(sheep.getWorld()) ? Double.MAX_VALUE
                        : player.getLocation().distance(sheep.getLocation()), 4.5, mode(player));
        if (result != GolemHandover.Result.GIVE) {
            lastHandover = stamp() + result.reason() + " (" + how + ")";
            return false;
        }
        ItemStack soul = golemKind.items().create(SOUL_ID, 1);
        if (soul == null) {
            lastHandover = stamp() + "Seelenblock-Item fehlt, behalten (" + how + ")";
            return false;
        }
        var rest = player.getInventory().addItem(soul);
        for (ItemStack left : rest.values()) {
            player.getWorld().dropItem(player.getLocation(), left, item -> item.setOwner(player.getUniqueId()));
        }
        if (!rest.isEmpty() && golemKind.messages() != null) {
            player.sendActionBar(golemKind.messages().get("mobs.echo_golem.inventory-full"));
        }
        hasSoul = false;
        golemKind.toPlayers.incrementAndGet();
        award(player, FRIEND);
        playSound("entity.echo_golem.item_drop", 1.0f, 1.04f);
        forgetCanyon(rules.ignoreCanyonTicks());
        anchor = null;
        patrolTarget = null;
        lastHandover = stamp() + GolemHandover.outcome(!rest.isEmpty()) + " an " + player.getName() + " (" + how + ")";
        return true;
    }

    private static GolemHandover.Mode mode(Player player) {
        if (player == null) {
            return GolemHandover.Mode.SURVIVAL;
        }
        return switch (player.getGameMode()) {
            case CREATIVE -> GolemHandover.Mode.CREATIVE;
            case SPECTATOR -> GolemHandover.Mode.SPECTATOR;
            case ADVENTURE -> GolemHandover.Mode.ADVENTURE;
            default -> GolemHandover.Mode.SURVIVAL;
        };
    }

    private static String stamp() {
        return java.time.LocalTime.now().withNano(0) + " ";
    }

    /** The bonded player when a delivery to them is possible: online, same world, survival or adventure. */
    private Player deliveryPlayer() {
        Player player = ownerPlayer();
        return player != null && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) ? player : null;
    }

    private Player ownerPlayer() {
        if (owner == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(owner);
        return player != null && player.getWorld().equals(sheep.getWorld()) && player.getGameMode() != GameMode.SPECTATOR ? player : null;
    }

    // ---- players ------------------------------------------------------------------------------------------

    @Override
    protected boolean interact(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        boolean shard = held.getType() == Material.ECHO_SHARD && de.lamamc.sift.item.SiftItems.idOf(held) == null;
        GolemBinding.Result result = GolemBinding.decide(shard, owner != null, player.getUniqueId().equals(owner), hasSoul,
                golemKind.rightClickGives());
        switch (result) {
            case BIND -> {
                bond(player);
                if (GolemBinding.consumesShard(result, player.getGameMode() == GameMode.CREATIVE)) {
                    held.subtract(1);
                }
                return true;
            }
            case ALREADY_BONDED -> {
                return true; // nothing happens, the shard is kept
            }
            case GIVE -> {
                if (handOver(player, "Rechtsklick")) {
                    stop();
                    mission.resumeSearch();
                }
                return true; // kept (e.g. creative): nothing else happens
            }
            case RELEASE_SOUL -> {
                releaseSoul("Rechtsklick von " + player.getName());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Binds the golem to the player (in any state); saved at once, under the original's field names. */
    private void bond(Player player) {
        owner = player.getUniqueId();
        ownerName = player.getName();
        singer = null;
        patrolTarget = null;
        patrolCooldown = 0;
        var pdc = sheep.getPersistentDataContainer();
        pdc.set(BONDED_PLAYER, PersistentDataType.STRING, owner.toString());
        pdc.set(BONDED_PLAYER_NAME, PersistentDataType.STRING, ownerName);
        Location at = sheep.getLocation();
        sheep.getWorld().spawnParticle(Particle.HEART, at.getX(), at.getY() + sheep.getHeight() * 0.7, at.getZ(), 7, 0.45, 0.55,
                0.45, 0.08);
        playSound(net.kyori.adventure.key.Key.key("minecraft", "entity.allay.item_given"), 1.0f, 0.9f);
        if (mission.state() == State.BOW) {
            finishBow();
        }
    }

    /** Every hit makes it drop the block (releaseCarriedSoul in hurtServer of the original). */
    @Override
    protected void hurt(EntityDamageEvent event) {
        String by = event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent e ? " durch " + e.getDamager().getName() : "";
        releaseSoul("Schaden " + event.getCause() + by);
        stop();
        playSound("entity.echo_golem.hurt", 1.0f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    protected void died() {
        playSound("entity.echo_golem.death", 1.0f, 1.0f);
        if (golemKind.light() != null) {
            golemKind.light().detached(sheep.getUniqueId());
        }
        releaseTarget();
    }

    private void releaseSoul(String reason) {
        if (!hasSoul) {
            return;
        }
        lastRelease = stamp() + reason;
        hasSoul = false;
        golemKind.dropped.incrementAndGet();
        ItemStack soul = golemKind.items().create(SOUL_ID, 1);
        if (soul != null) {
            sheep.getWorld().dropItem(sheep.getLocation().add(0, 0.8, 0), soul);
        }
        playSound("entity.echo_golem.item_drop", 1.0f, 1.0f);
        stop();
        singer = null;
        anchor = null;
        patrolTarget = null;
        forgetCanyon(rules.ignoreCanyonTicks());
        mission.set(State.SIT);
        mission.set(Phase.EXPLORE);
        animations.stop(MISSION);
    }

    private void award(NamespacedKey key, double radius) {
        Location at = sheep.getLocation();
        for (Player player : sheep.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead() && player.getLocation().distanceSquared(at) <= radius * radius) {
                award(player, key);
            }
        }
    }

    private static void award(Player player, NamespacedKey key) {
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement != null) {
            var progress = player.getAdvancementProgress(advancement);
            progress.getRemainingCriteria().forEach(progress::awardCriteria);
        }
    }

    // ---- canyon bookkeeping -------------------------------------------------------------------------------

    private void forgetCanyon(int ticks) {
        if (cluster != null) {
            ignored = cluster;
            ignoredUntil = now() + ticks;
        }
        clearRoute();
    }

    private void markUnavailable(int ticks) {
        if (cluster != null) {
            unavailable.put(cluster, now() + ticks);
            ignored = cluster;
            ignoredUntil = now() + ticks;
        }
        releaseTarget();
    }

    private void abandonCanyon(int ticks) {
        markUnavailable(ticks);
        clearRoute();
        heading += (random.nextBoolean() ? 1 : -1) * (1.75 + random.nextDouble() * 0.7);
    }

    private void clearRoute() {
        cluster = null;
        entrance = null;
        midpoint = null;
        entered = false;
        midpointPassed = false;
        exitOverride = null;
        canyonCooldown = 0;
        mission.set(Phase.EXPLORE);
        resetRoute();
    }

    private boolean needsMidpoint() {
        if (midpoint == null || entrance == null) {
            return false;
        }
        Location at = sheep.getLocation();
        double toEntrance = entrance.horizontalDistanceSquared(at.getX(), at.getZ());
        double midToEntrance = entrance.horizontalDistanceSquared(midpoint.x() + 0.5, midpoint.z() + 0.5);
        return toEntrance > midToEntrance + 16;
    }

    private boolean onSurface() {
        Location at = sheep.getLocation();
        World world = sheep.getWorld();
        boolean sky = world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) < at.getBlockY() + 1;
        double toEntrance = entrance == null ? 0 : Math.sqrt(entrance.horizontalDistanceSquared(at.getX(), at.getZ()));
        return EchoGolemRules.onSurface(at.getY(), sky, entrance == null ? 0 : entrance.y(), toEntrance, entrance != null);
    }

    // ---- movement -----------------------------------------------------------------------------------------

    private void explore(double speed) {
        if (--turnTimer <= 0) {
            heading += (random.nextDouble() - 0.5) * 0.38;
            turnTimer = 900 + random.nextInt(901);
            exploreTarget = null;
        }
        exploreAge++;
        boolean reached = exploreTarget != null && near(exploreTarget, 4.0, 7.0);
        boolean failed = exploreTarget != null && (routeFailures >= 6 || exploreAge > 1200);
        if (exploreTarget == null || reached || failed) {
            if (failed) {
                heading += (random.nextBoolean() ? 1 : -1) * (0.58 + random.nextDouble() * 0.38);
            }
            exploreTarget = chooseExploreTarget();
            exploreAge = 0;
            resetRoute();
        }
        if (exploreTarget != null) {
            navigate(exploreTarget, speed, 4.0, true);
        }
    }

    private Location chooseExploreTarget() {
        World world = sheep.getWorld();
        Location at = sheep.getLocation();
        for (int distance : new int[] {42, 34, 26}) {
            for (double offset : new double[] {0, 0.18, -0.18, 0.38, -0.38, 0.62, -0.62}) {
                double angle = heading + offset;
                int x = (int) Math.floor(at.getX() + Math.cos(angle) * distance);
                int z = (int) Math.floor(at.getZ() + Math.sin(angle) * distance);
                if (world.isChunkLoaded(x >> 4, z >> 4)) {
                    return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + 0.5);
                }
            }
        }
        heading += Math.PI / 2;
        return null;
    }

    /**
     * Walks towards a target. Keeps the current path while it leads to the same goal, plans a new one at most
     * every 10–16 ticks and only with the shared path budget; a path that ends farther than {@code accept} from
     * the target counts as a failure unless partial paths are allowed (long walks over unknown ground).
     */
    private boolean navigate(Location target, double speed, double accept, boolean partial) {
        var pathfinder = sheep.getPathfinder();
        if (navGoal != null && navGoal.getWorld().equals(target.getWorld()) && navGoal.distanceSquared(target) <= 4 && pathfinder.hasPath()) {
            return true;
        }
        if (--repathCooldown > 0) {
            return pathfinder.hasPath();
        }
        if (now() < nextPath || !golemKind.takePathBudget(now())) {
            repathCooldown = 1 + (sheep.getEntityId() & 1);
            return pathfinder.hasPath();
        }
        nextPath = now() + 4; // personal path interval of the original
        repathCooldown = 10 + random.nextInt(7);
        Location goal = target;
        if (partial && horizontal2(target) > 1600) {
            goal = waypoint(target);
        }
        var path = pathfinder.findPath(goal);
        if (path == null || path.getFinalPoint() == null) {
            routeFailures++;
            repathCooldown = 4;
            navGoal = null;
            return false;
        }
        boolean reaches = path.canReachFinalPoint() && path.getFinalPoint().distanceSquared(goal) <= accept * accept;
        // a path that ends short of the goal but brings the golem clearly closer is progress, not a failure
        boolean closer = path.getFinalPoint().distanceSquared(goal) + 4 < sheep.getLocation().distanceSquared(goal);
        if (!reaches && !partial && !closer) {
            routeFailures++;
            repathCooldown = 4;
            navGoal = null;
            return false;
        }
        pathfinder.moveTo(path, speed);
        navGoal = target.clone();
        routeFailures = reaches || closer ? Math.max(0, routeFailures - 1) : routeFailures + 1;
        return true;
    }

    /** A loaded ground point 24 (or 18, 12) blocks towards a far target. */
    private Location waypoint(Location target) {
        World world = sheep.getWorld();
        Location at = sheep.getLocation();
        double angle = Math.atan2(target.getZ() - at.getZ(), target.getX() - at.getX());
        for (int distance : new int[] {24, 18, 12}) {
            int x = (int) Math.floor(at.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(at.getZ() + Math.sin(angle) * distance);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + 0.5);
            }
        }
        return target;
    }

    /** Every 20 ticks: wanting to move but not moving twice in a row counts as four route failures and replans. */
    private void watchdog() {
        if (sheep.getTicksLived() % 20 != 0) {
            return;
        }
        Location now = sheep.getLocation();
        boolean wantsToMove = navGoal != null && (mission.state() == State.SEARCH || mission.state() == State.CARRY);
        if (sample == null || !wantsToMove || !now.getWorld().equals(sample.getWorld()) || now.distanceSquared(sample) >= 0.16) {
            stalls = 0;
        } else if (++stalls >= 2) {
            stalls = 0;
            sheep.getPathfinder().stopPathfinding();
            navGoal = null;
            routeFailures += 4;
            repathCooldown = 0;
            if (mission.phase() == Phase.EXPLORE || mission.phase() == Phase.DELIVER) {
                heading += (random.nextBoolean() ? 1 : -1) * (1.75 + random.nextDouble() * 0.7);
                exploreTarget = null;
            }
        }
        sample = now;
    }

    /** Against a wall while walking: a small hop now and then (assistLocalStep of the original). */
    private void assistStep() {
        if (sheep.isOnGround() && sheep.getPathfinder().hasPath() && (sheep.getTicksLived() + sheep.getEntityId()) % 6 == 0) {
            Location at = sheep.getLocation();
            Vector look = new Vector(-Math.sin(Math.toRadians(at.getYaw())), 0, Math.cos(Math.toRadians(at.getYaw())));
            if (!at.clone().add(look.multiply(0.9)).getBlock().isPassable()) {
                Vector v = sheep.getVelocity();
                sheep.setVelocity(v.setY(Math.max(v.getY(), 0.34)));
            }
        }
    }

    private void resetRoute() {
        routeFailures = 0;
        repathCooldown = 0;
        navGoal = null;
        stalls = 0;
        sheep.getPathfinder().stopPathfinding();
    }

    private void stop() {
        sheep.getPathfinder().stopPathfinding();
        navGoal = null;
    }

    private void face(Location target) {
        Location at = sheep.getLocation();
        float yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - at.getX()), target.getZ() - at.getZ()));
        sheep.setRotation(yaw, 0);
        sheep.setBodyYaw(yaw);
    }

    private boolean near(Location target, double horizontal, double vertical) {
        Location at = sheep.getLocation();
        return target.getWorld().equals(at.getWorld()) && horizontal2(target) <= horizontal * horizontal
                && Math.abs(target.getY() - at.getY()) <= vertical;
    }

    private double horizontal2(Location target) {
        Location at = sheep.getLocation();
        double dx = target.getX() - at.getX();
        double dz = target.getZ() - at.getZ();
        return dx * dx + dz * dz;
    }

    private double distance2(Pos pos) {
        Location at = sheep.getLocation();
        return pos.distanceSquared(at.getX(), at.getY(), at.getZ());
    }

    private Location center(Pos pos) {
        return new Location(sheep.getWorld(), pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }

    private long now() {
        return sheep.getWorld().getGameTime();
    }

    private static long pack(Pos pos) {
        return SoulCanyonLandmarks.pack(pos.x(), pos.y(), pos.z());
    }

    // ---- saving -------------------------------------------------------------------------------------------

    @Override
    protected void detached() {
        if (golemKind.light() != null) {
            golemKind.light().detached(sheep.getUniqueId());
        }
        releaseTarget();
        StringBuilder s = new StringBuilder();
        s.append("state=").append(mission.state()).append(";phase=").append(mission.phase()).append(";soul=").append(hasSoul)
                .append(";entered=").append(entered).append(";mid_passed=").append(midpointPassed)
                .append(";heading=").append(heading);
        put(s, "cluster", cluster);
        put(s, "entrance", entrance);
        put(s, "midpoint", midpoint);
        if (owner != null) {
            sheep.getPersistentDataContainer().set(BONDED_PLAYER, PersistentDataType.STRING, owner.toString());
            sheep.getPersistentDataContainer().set(BONDED_PLAYER_NAME, PersistentDataType.STRING, ownerName == null ? "" : ownerName);
        }
        if (anchor != null) {
            s.append(";anchor=").append(anchor.getBlockX()).append(',').append(anchor.getBlockY()).append(',').append(anchor.getBlockZ());
        }
        sheep.getPersistentDataContainer().set(STATE, PersistentDataType.STRING, s.toString());
    }

    private static void put(StringBuilder s, String key, Pos pos) {
        if (pos != null) {
            s.append(';').append(key).append('=').append(pos.x()).append(',').append(pos.y()).append(',').append(pos.z());
        }
    }

    private void load(PersistentDataContainer pdc) {
        String bonded = pdc.get(BONDED_PLAYER, PersistentDataType.STRING);
        if (bonded != null) {
            try {
                owner = UUID.fromString(bonded);
                ownerName = pdc.get(BONDED_PLAYER_NAME, PersistentDataType.STRING);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        String text = pdc.get(STATE, PersistentDataType.STRING);
        if (text == null) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                values.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        try {
            hasSoul = Boolean.parseBoolean(values.get("soul"));
            entered = Boolean.parseBoolean(values.get("entered"));
            midpointPassed = Boolean.parseBoolean(values.get("mid_passed"));
            heading = values.containsKey("heading") ? Double.parseDouble(values.get("heading")) : heading;
            cluster = pos(values.get("cluster"));
            entrance = pos(values.get("entrance"));
            midpoint = pos(values.get("midpoint"));
            if (owner == null && values.containsKey("owner")) { // alpha.19 kept it in the state string
                owner = UUID.fromString(values.get("owner"));
                ownerName = values.get("owner_name");
            }
            Pos a = pos(values.get("anchor"));
            anchor = a == null ? null : center(a);
        } catch (RuntimeException e) {
            return; // broken state: start over
        }
        if (hasSoul) {
            mission.restore(State.CARRY, entrance == null ? Phase.DELIVER : Phase.EXIT_CANYON);
        } else if (cluster != null && entrance != null) {
            mission.restore(State.SEARCH, entered ? Phase.SEEK_SOUL : Phase.APPROACH_CANYON);
        }
    }

    private static Pos pos(String text) {
        if (text == null) {
            return null;
        }
        String[] p = text.split(",");
        return new Pos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    @Override
    public String debugState() {
        return String.format(Locale.ROOT, "%s/%s, Seele %s, Ziel %s, Schlucht %s, Eingang %s%s, Weg-Fehler %d%s",
                mission.state(), mission.phase(), hasSoul ? "getragen" : "nein",
                soulTarget == null ? "-" : soulTarget.x() + " " + soulTarget.y() + " " + soulTarget.z(),
                cluster == null ? "-" : cluster.x() + " " + cluster.y() + " " + cluster.z(),
                entrance == null ? "-" : entrance.x() + " " + entrance.y() + " " + entrance.z(),
                entered ? " (drin)" : "", routeFailures,
                owner == null ? ", ungebunden" : ", gebunden an " + (ownerName == null ? owner : ownerName))
                + (lastHandover == null ? "" : "; letzte Übergabe: " + lastHandover)
                + (lastRelease == null ? "" : "; zuletzt fallen gelassen: " + lastRelease);
    }
}
