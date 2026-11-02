package de.lamamc.sift.mob.echogolem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.mob.EchoGolemRules;
import de.lamamc.sift.core.mob.SoulFailureMemory;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.gen.SoulCanyonLandmarks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sheep;

/**
 * Echo Golem (EchoGolemEntity of the original 1.1.1): a peaceful golem that walks the Sift, climbs into soul
 * canyons, picks up a soul block and carries it out, to a Singer or, once bonded with an echo shard, to its
 * player. Carried by an invisible sheep scaled ×1.28 (hitbox 1.15 × 1.66 like the original's 1.15 × 1.65) with
 * the original's step height of 1.5, so it walks up and down the canyon ramps.
 */
public final class EchoGolemKind extends MobKind {

    public static final String ID = "echo_golem";

    /** The Singer side of the soul exchange (set by the Singer module). */
    public interface SingerLink {
        /** An existing soul Singer near the golem that can take its block, or a newly called one; null if none. */
        SiftMob request(EchoGolem golem);

        /** The golem offers its block to this Singer (it comes over, the golem bows, the block changes hands). */
        void offer(SiftMob singer, EchoGolem golem);
    }

    private final EchoGolemRules rules;
    private final SiftItems items;
    private final CustomBlockRegistry blocks;
    private final Supplier<SoulCanyonLandmarks> landmarks;
    private final SoulFailureMemory failures = new SoulFailureMemory();
    /** Soul blocks a golem is going for (packed position → golem), so two golems do not take the same one. */
    private final Map<Long, UUID> reservations = new HashMap<>();
    private SingerLink singers;
    /** Counters for /sift debug golems: soul blocks picked up, handed to Singers, to players, dropped. */
    public final java.util.concurrent.atomic.AtomicLong taken = new java.util.concurrent.atomic.AtomicLong();
    public final java.util.concurrent.atomic.AtomicLong toSingers = new java.util.concurrent.atomic.AtomicLong();
    public final java.util.concurrent.atomic.AtomicLong toPlayers = new java.util.concurrent.atomic.AtomicLong();
    public final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private GolemLight light;
    private MobManager manager;
    private long pathTick = -1;
    private int pathsThisTick;
    private long exitTick = -1;

    public EchoGolemKind(ConfigurationSection config, SiftItems items, CustomBlockRegistry blocks, Supplier<SoulCanyonLandmarks> landmarks) {
        super(ID, config);
        this.items = items;
        this.blocks = blocks;
        this.landmarks = landmarks;
        EchoGolemRules d = EchoGolemRules.defaults();
        ConfigurationSection t = section(config, "timings");
        ConfigurationSection s = section(config, "search");
        ConfigurationSection v = section(config, "delivery");
        ConfigurationSection sp = section(config, "speeds");
        EchoGolemRules.Speeds ds = d.speeds();
        this.rules = new EchoGolemRules(t.getInt("pickup-take", d.pickupTakeTick()), t.getInt("pickup-end", d.pickupEndTick()),
                t.getInt("bow-give", d.bowGiveTick()), t.getInt("bow-end", d.bowEndTick()), t.getInt("bow-timeout", d.bowTimeout()),
                t.getInt("sit", d.sitTicks()), t.getInt("ignore-canyon", d.ignoreCanyonTicks()),
                t.getInt("blocked-canyon", d.blockedCanyonTicks()), t.getInt("empty-canyon", d.emptyCanyonTicks()),
                s.getInt("canyon-interval", d.landmarkInterval()), s.getDouble("canyon-range", d.landmarkRange()),
                s.getDouble("distant-canyon", d.distantCanyon()), s.getInt("scan-interval", d.scanInterval()),
                s.getDouble("scan-radius", d.scanRadius()), s.getDouble("scan-height", d.scanHeight()),
                s.getDouble("target-scan-radius", d.targetScanRadius()), s.getDouble("target-scan-height", d.targetScanHeight()),
                s.getDouble("hand-reach", d.handReach()), s.getDouble("approach-offset", d.approachOffset()),
                v.getInt("singer-interval", d.singerInterval()), v.getDouble("singer-search", d.singerSearch()),
                v.getDouble("owner-range", d.ownerRange()), v.getDouble("bow-distance", d.bowDistance()),
                v.getDouble("patrol-leash", d.patrolLeash()), v.getInt("patrol-min", d.patrolMin()), v.getInt("patrol-max", d.patrolMax()),
                new EchoGolemRules.Speeds(sp.getDouble("explore", ds.explore()), sp.getDouble("approach", ds.approach()),
                        sp.getDouble("enter", ds.enter()), sp.getDouble("descend", ds.descend()), sp.getDouble("soul", ds.soul()),
                        sp.getDouble("exit", ds.exit()), sp.getDouble("owner", ds.owner()), sp.getDouble("singer", ds.singer()),
                        sp.getDouble("patrol", ds.patrol()), sp.getDouble("patrol-return", ds.patrolReturn())));
    }

    static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    EchoGolemRules rules() {
        return rules;
    }

    SiftItems items() {
        return items;
    }

    CustomBlockRegistry blocks() {
        return blocks;
    }

    SoulCanyonLandmarks landmarks() {
        return landmarks.get();
    }

    SoulFailureMemory failures() {
        return failures;
    }

    Map<Long, UUID> reservations() {
        return reservations;
    }

    MobManager manager() {
        return manager;
    }

    SingerLink singers() {
        return singers;
    }

    public void singers(SingerLink link) {
        this.singers = link;
    }

    GolemLight light() {
        return light;
    }

    public void light(GolemLight light) {
        this.light = light;
    }

    /** At most one new path per server tick for all golems (MAX_NEW_PATHS_PER_SERVER_TICK of the original). */
    boolean takePathBudget(long serverTick) {
        if (pathTick != serverTick) {
            pathTick = serverTick;
            pathsThisTick = 0;
        }
        if (pathsThisTick >= Math.max(1, config.getInt("paths-per-tick", 1))) {
            return false;
        }
        pathsThisTick++;
        return true;
    }

    /** One exit search path per server tick, apart from the normal budget (MAX_CRITICAL_EXIT_PATHS_PER_SERVER_TICK). */
    boolean takeExitBudget(long serverTick) {
        if (exitTick == serverTick) {
            return false;
        }
        exitTick = serverTick;
        return true;
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
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        set(sheep, Attribute.MAX_HEALTH, attributes, "max-health", 34.0);
        set(sheep, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.23);
        set(sheep, Attribute.FOLLOW_RANGE, attributes, "follow-range", 128.0);
        set(sheep, Attribute.STEP_HEIGHT, attributes, "step-height", 1.5);
        set(sheep, Attribute.KNOCKBACK_RESISTANCE, attributes, "knockback-resistance", 0.25);
        set(sheep, Attribute.SCALE, config, "base-scale", 1.28);
        if (fresh) {
            sheep.setAdult();
            sheep.setHealth(sheep.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        sheep.setAgeLock(true);
        sheep.setBreed(false);
        sheep.setSheared(true);
        sheep.setRemoveWhenFarAway(false); // CREATURE that never despawns, like the original
        var goals = Bukkit.getMobGoals();
        for (Goal<Sheep> goal : List.copyOf(goals.getAllGoals(sheep))) {
            if (!VanillaGoal.FLOAT.equals(goal.getKey())) {
                goals.removeGoal(sheep, goal);
            }
        }
        goals.addGoal(sheep, 2, new EchoGolemGoal(sheep, this));
    }

    private static void set(Mob mob, Attribute attribute, ConfigurationSection section, String key, double fallback) {
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
        this.manager = manager;
        return new EchoGolem(this, (Sheep) base, manager);
    }
}
