package de.lamamc.sift.world.rift;

import de.lamamc.sift.Messages;
import de.lamamc.sift.core.portal.ReturnPoint;
import de.lamamc.sift.core.rift.RiftRules;
import de.lamamc.sift.item.SiftAdvancements;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.world.MainPortalBuilder;
import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.world.access.SiftGate;
import de.lamamc.sift.world.portal.PortalService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Rifts (alpha.22, config rifts.*), as in the original 1.1.1: they open on their own near players in the
 * Sift (every 4.5–5.5 min, 20–64 blocks away, 5 min open) and lead out to the player's respawn point. There
 * a counter rift opens that leads back until both close. Admins open rifts with the sift_rift item or
 * /sift rift open (in the Sift: out; elsewhere: into the Sift). Rifts into the Sift obey the time lock.
 * Rifts are not kept across restarts.
 */
public final class RiftManager implements Listener {

    static final NamespacedKey LIGHTS = new NamespacedKey("the_sift", "rift_lights");
    static final NamespacedKey DISPLAY = new NamespacedKey("the_sift", "rift_display");
    private static final Vector3f TINY = new Vector3f(0.01f, 0.01f, 0.01f);

    /** One open rift. {@code intoSift}: where it leads. */
    public static final class Rift {
        final int id;
        final World world;
        final int x;
        final int y;
        final int z;
        final boolean alongX;
        final boolean intoSift;
        final Rift origin;
        long expires;
        long closingSince = -1;
        UUID display;
        final List<Block> lights = new ArrayList<>();
        final double[] bounds;

        Rift(int id, World world, int x, int y, int z, boolean alongX, boolean intoSift, Rift origin, long expires) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.alongX = alongX;
            this.intoSift = intoSift;
            this.origin = origin;
            this.expires = expires;
            this.bounds = RiftRules.bounds(x, y, z, alongX);
        }

        public int id() {
            return id;
        }

        public World world() {
            return world;
        }

        public Location location() {
            return new Location(world, x + 0.5, y, z + 0.5);
        }

        public boolean intoSift() {
            return intoSift;
        }

        public boolean closing() {
            return closingSince >= 0;
        }

        public long expires() {
            return expires;
        }

        BoundingBox box() {
            return new BoundingBox(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        }
    }

    private final Plugin plugin;
    private final de.lamamc.sift.block.CustomBlockRegistry blocks;
    private final Messages messages;
    private final SiftAdvancements advancements;
    private final SiftGate gate;
    private final Logger logger;
    private final Random random = new Random();
    private final List<Rift> rifts = new ArrayList<>();
    private final Map<UUID, Long> nextNatural = new HashMap<>();
    /** Entities that came through a rift: ignored until they stop touching rifts and the time is up. */
    private final Map<UUID, Long> latched = new HashMap<>();
    private final Set<Long> activeLights = new HashSet<>();
    private int nextId = 1;
    private long naturalSearches;
    private long naturalOpened;
    private long naturalFailed;
    private long naturalUnloaded;
    private long orphansRemoved;
    private long tick;

    private final boolean natural;
    private final long firstDelay;
    private final long interval;
    private final long intervalRandom;
    private final long retry;
    private final int minDistance;
    private final int maxDistance;
    private final int attempts;
    private final long lifetime;
    private final int openTicks;
    private final boolean light;
    private final boolean ambient;
    private final boolean mobAvoidance;
    private final long cooldown;
    private final int reuseRadius;
    private final int counterMin;
    private final int counterMax;
    private final int counterStep;
    private final int counterAngles;
    private final boolean spawnProtection;
    private final boolean claims;
    private final int adminRadius;
    private final int adminHeight;

    public RiftManager(Plugin plugin, de.lamamc.sift.block.CustomBlockRegistry blocks, Messages messages, SiftAdvancements advancements,
                       SiftGate gate, ConfigurationSection config) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.messages = messages;
        this.advancements = advancements;
        this.gate = gate;
        this.logger = plugin.getLogger();
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        natural = c.getBoolean("natural.enabled", true);
        firstDelay = Math.max(20, c.getLong("natural.first-delay-ticks", 5400));
        interval = Math.max(20, c.getLong("natural.interval-ticks", 5400));
        intervalRandom = Math.max(0, c.getLong("natural.interval-random-ticks", 1200));
        retry = Math.max(20, c.getLong("natural.retry-ticks", 200));
        minDistance = Math.max(4, c.getInt("natural.min-distance", 20));
        maxDistance = Math.max(minDistance, c.getInt("natural.max-distance", 64));
        attempts = Math.max(1, c.getInt("natural.attempts", 48));
        lifetime = Math.max(40, c.getLong("lifetime-ticks", 6000));
        openTicks = Math.max(1, c.getInt("open-ticks", 16));
        light = c.getBoolean("light", true);
        ambient = c.getBoolean("ambient-sound", true);
        mobAvoidance = c.getBoolean("mob-avoidance", true);
        cooldown = Math.max(0, c.getLong("reentry-cooldown-ticks", 100));
        reuseRadius = Math.max(0, c.getInt("counter.reuse-radius", 64));
        counterMin = Math.max(1, c.getInt("counter.min-radius", 16));
        counterMax = Math.max(counterMin, c.getInt("counter.max-radius", 256));
        counterStep = Math.max(1, c.getInt("counter.step", 16));
        counterAngles = Math.max(1, c.getInt("counter.angles", 16));
        spawnProtection = c.getBoolean("counter.respect-spawn-protection", true);
        claims = c.getBoolean("counter.respect-claims", true);
        adminRadius = Math.max(0, c.getInt("admin.search-radius", 12));
        adminHeight = Math.max(0, c.getInt("admin.search-height", 6));
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 1L);
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                cleanLights(chunk);
            }
        }
    }

    public long lifetime() {
        return lifetime;
    }

    public List<Rift> rifts() {
        return List.copyOf(rifts);
    }

    // ---- opening and closing --------------------------------------------------------------------------------

    /** Opens a rift with its base at this block, or returns null if there is no room. */
    public Rift open(World world, int x, int y, int z, boolean alongX, boolean intoSift, Rift origin, long life) {
        return open(world, x, y, z, alongX, intoSift, origin, life, false);
    }

    /** {@code relaxed} (admin): no ground needed and entities in the way do not matter. */
    private Rift open(World world, int x, int y, int z, boolean alongX, boolean intoSift, Rift origin, long life, boolean relaxed) {
        BoundingBox wanted = new Rift(0, world, x, y, z, alongX, intoSift, null, 0).box();
        for (Rift other : rifts) {
            if (other.world.equals(world) && other.box().expand(1, RiftRules.HEIGHT, 1).overlaps(wanted)) {
                return null; // never a rift in, on top of or under another one (alpha.23.1)
            }
        }
        if (!RiftRules.fits(space(world), x, y, z, alongX, !relaxed) || !relaxed && !world.getNearbyEntities(
                new Rift(0, world, x, y, z, alongX, intoSift, null, 0).box(), e -> !(e instanceof Display)).isEmpty()) {
            return null;
        }
        Rift rift = new Rift(nextId++, world, x, y, z, alongX, intoSift, origin, tick + life);
        rifts.add(rift);
        if (light) {
            placeLights(rift);
        }
        spawnDisplay(rift);
        world.playSound(rift.location().add(0, 2, 0), "the_sift:entity.rift.appear", SoundCategory.AMBIENT, 2.0f, 1.0f);
        return rift;
    }

    /** Opens a rift in front of a player (admin item and command); null if there is no room. */
    public Rift openFor(Player player, Block base) {
        BlockFace facing = player.getFacing();
        boolean alongX = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
        return openNear(base.getWorld(), base.getX(), base.getY(), base.getZ(), alongX);
    }

    /**
     * Admin rift (item and command, alpha.23.1): the nearest spot within rifts.admin.search-radius where 9 × 5
     * blocks are open, without needing ground (hilly terrain). In the Sift it leads out, elsewhere in.
     */
    public Rift openNear(World world, int x, int y, int z, boolean alongX) {
        for (int[] p : RiftRules.nearby(x, y, z, adminRadius, adminHeight)) {
            for (boolean axis : new boolean[] {alongX, !alongX}) {
                Rift rift = open(world, p[0], p[1], p[2], axis, !SiftWorlds.isSift(world), null, lifetime, true);
                if (rift != null) {
                    return rift;
                }
            }
        }
        return null;
    }

    /**
     * Rift displays in the loaded chunks that belong to no open rift (left over, e.g. after an error); with
     * {@code remove} they are removed. Returns how many were found.
     */
    public int orphans(boolean remove) {
        Set<UUID> live = new HashSet<>();
        rifts.forEach(r -> {
            if (r.display != null) {
                live.add(r.display);
            }
        });
        int found = 0;
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(DISPLAY) && !live.contains(display.getUniqueId())) {
                    found++;
                    if (remove) {
                        display.remove();
                    }
                }
            }
        }
        return found;
    }

    /** Natural rifts: searches, opened, given up (no spot in all attempts), attempts on unloaded chunks; removed orphan displays. */
    public long[] naturalStats() {
        return new long[] {naturalSearches, naturalOpened, naturalFailed, naturalUnloaded, orphansRemoved};
    }

    public void close(Rift rift) {
        if (!rift.closing()) {
            rift.closingSince = tick;
            ItemDisplay display = display(rift);
            if (display != null) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(openTicks);
                Transformation t = display.getTransformation();
                display.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(), TINY, t.getRightRotation()));
            }
        }
    }

    private void remove(Rift rift) {
        ItemDisplay display = display(rift);
        if (display != null) {
            display.remove();
        }
        removeLights(rift);
    }

    /** Plugin disable: everything goes, lights included (rifts are not kept across restarts). */
    public void removeAll() {
        rifts.forEach(this::remove);
        rifts.clear();
    }

    // ---- looks ----------------------------------------------------------------------------------------------

    private void spawnDisplay(Rift rift) {
        ItemStack panel = ItemStack.of(Material.PAPER);
        panel.setData(io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL,
                new NamespacedKey("the_sift", rift.intoSift ? "rift_into_sift" : "rift_out_of_sift"));
        double[] b = rift.bounds;
        Location at = new Location(rift.world, (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2);
        float angle = rift.alongX ? 0f : (float) (Math.PI / 2);
        Vector3f full = new Vector3f(9f, 4.25f, 1f);
        ItemDisplay display = rift.world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(panel);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0), TINY, new AxisAngle4f()));
            d.getPersistentDataContainer().set(DISPLAY, PersistentDataType.INTEGER, rift.id);
        });
        rift.display = display.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid() && !rift.closing()) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(openTicks);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(angle, 0, 1, 0), full, new AxisAngle4f()));
            }
        }, 2L);
    }

    private ItemDisplay display(Rift rift) {
        Entity entity = rift.display == null ? null : Bukkit.getEntity(rift.display);
        return entity instanceof ItemDisplay d && d.isValid() ? d : null;
    }

    // ---- light blocks ---------------------------------------------------------------------------------------

    private void placeLights(Rift rift) {
        for (int offset : new int[] {-3, 0, 3}) {
            Block block = rift.world.getBlockAt(rift.alongX ? rift.x + offset : rift.x, rift.y + 2, rift.alongX ? rift.z : rift.z + offset);
            if (!block.getType().isAir()) {
                continue; // never over a block that is not ours
            }
            org.bukkit.block.data.type.Light data = (org.bukkit.block.data.type.Light) Material.LIGHT.createBlockData();
            data.setLevel(15);
            block.setBlockData(data, false);
            rift.lights.add(block);
            activeLights.add(block.getBlockKey());
            record(block, true);
        }
    }

    private void removeLights(Rift rift) {
        for (Block block : rift.lights) {
            activeLights.remove(block.getBlockKey());
            if (block.getChunk().isLoaded() && block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
            record(block, false);
        }
        rift.lights.clear();
    }

    /** Light positions are also kept in the chunk, so a crash leaves nothing behind (cleaned on load). */
    private static void record(Block block, boolean add) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] old = pdc.getOrDefault(LIGHTS, PersistentDataType.LONG_ARRAY, new long[0]);
        long key = block.getBlockKey();
        long[] next = add ? java.util.Arrays.copyOf(old, old.length + 1) : java.util.Arrays.stream(old).filter(k -> k != key).toArray();
        if (add) {
            next[old.length] = key;
        }
        if (next.length == 0) {
            pdc.remove(LIGHTS);
        } else {
            pdc.set(LIGHTS, PersistentDataType.LONG_ARRAY, next);
        }
    }

    private void cleanLights(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] keys = pdc.get(LIGHTS, PersistentDataType.LONG_ARRAY);
        if (keys == null) {
            return;
        }
        List<Long> keep = new ArrayList<>();
        for (long key : keys) {
            if (activeLights.contains(key)) {
                keep.add(key);
                continue;
            }
            Block block = chunk.getWorld().getBlockAtKey(key);
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
            }
        }
        if (keep.isEmpty()) {
            pdc.remove(LIGHTS);
        } else {
            pdc.set(LIGHTS, PersistentDataType.LONG_ARRAY, keep.stream().mapToLong(Long::longValue).toArray());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                cleanLights(chunk);
            }
        });
    }

    // ---- ticking --------------------------------------------------------------------------------------------

    private void tick() {
        tick++;
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (natural && sift != null && tick % 20 == 0) {
            naturalRifts(sift);
        }
        Set<UUID> touching = new HashSet<>();
        if (tick % 200 == 0) {
            int removed = orphans(true);
            if (removed > 0) {
                orphansRemoved += removed;
            }
        }
        // a snapshot: travelling opens counter rifts, commands close rifts (alpha.23.1)
        de.lamamc.sift.core.rift.RiftSweep.run(rifts, rift -> tickRift(rift, touching));
        // release entities that stopped touching rifts once their cooldown is over
        latched.entrySet().removeIf(e -> !touching.contains(e.getKey()) && e.getValue() <= tick);
    }

    /** One tick of one rift; false when it is gone. */
    private boolean tickRift(Rift rift, Set<UUID> touching) {
        if (rift.closing()) {
            if (tick - rift.closingSince > openTicks) {
                remove(rift);
                return false;
            }
            return true;
        }
        if (tick >= rift.expires - openTicks) {
            close(rift);
            return true;
        }
        if (!rift.world.isChunkLoaded(rift.x >> 4, rift.z >> 4)) {
            return true;
        }
        if (display(rift) == null && tick % 20 == 0) {
            spawnDisplay(rift); // the chunk was unloaded and the display with it
        }
        if (ambient && tick % 40 == 0) {
            Location center = rift.location().add(0, 2, 0);
            for (Player player : rift.world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= 32 * 32) {
                    player.playSound(center, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.BLOCKS, 1.0f, 1.0f);
                }
            }
        }
        if (mobAvoidance && tick % 20 == 0) {
            keepMobsAway(rift);
        }
        for (Entity entity : rift.world.getNearbyEntities(rift.box())) {
            if (!traveller(entity)) {
                continue;
            }
            touching.add(entity.getUniqueId());
            Long until = latched.get(entity.getUniqueId());
            if (until != null) {
                continue;
            }
            transfer(entity, rift);
        }
        return true;
    }

    private static boolean traveller(Entity entity) {
        if (entity instanceof Display || !entity.isValid() || entity.isInsideVehicle()) {
            return false;
        }
        return !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR);
    }

    private void naturalRifts(World sift) {
        Set<UUID> present = new HashSet<>();
        for (Player player : sift.getPlayers()) {
            if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            present.add(player.getUniqueId());
            Long due = nextNatural.get(player.getUniqueId());
            if (due == null) {
                nextNatural.put(player.getUniqueId(), tick + RiftRules.nextDelay(firstDelay, intervalRandom, random.nextDouble()));
                continue;
            }
            if (tick < due) {
                continue;
            }
            Rift rift = naturalNear(player);
            nextNatural.put(player.getUniqueId(), tick + (rift == null ? retry : RiftRules.nextDelay(interval, intervalRandom, random.nextDouble())));
        }
        nextNatural.keySet().retainAll(present);
    }

    private Rift naturalNear(Player player) {
        naturalSearches++;
        Location at = player.getLocation();
        for (int i = 0; i < attempts; i++) {
            int[] xz = RiftRules.around(at.getX(), at.getZ(), random.nextDouble() * Math.PI * 2,
                    minDistance + random.nextDouble() * (maxDistance - minDistance));
            if (!at.getWorld().isChunkLoaded(xz[0] >> 4, xz[1] >> 4)) {
                naturalUnloaded++;
                continue;
            }
            Rift rift = inColumn(at.getWorld(), xz[0], xz[1], random.nextBoolean(), false, null, lifetime);
            if (rift != null) {
                naturalOpened++;
                return rift;
            }
        }
        naturalFailed++;
        return null;
    }

    /** Tries the surface of a column (rising up to 2 blocks, both axes). */
    private Rift inColumn(World world, int x, int z, boolean alongX, boolean intoSift, Rift origin, long life) {
        int top = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        for (int rise = 0; rise <= 2; rise++) {
            for (boolean axis : new boolean[] {alongX, !alongX}) {
                Rift rift = open(world, x, top + rise, z, axis, intoSift, origin, life);
                if (rift != null) {
                    return rift;
                }
            }
        }
        return null;
    }

    /** Leaves and plants are no ground for a rift (alpha.23.1: a counter rift hung over tree tops). */
    private boolean plantOrLeaves(Block block) {
        Material type = block.getType();
        if (Tag.LEAVES.isTagged(type) || type == Material.BAMBOO || type == Material.CACTUS || type == Material.CHORUS_PLANT
                || type == Material.CHORUS_FLOWER || Tag.WART_BLOCKS.isTagged(type)) {
            return true;
        }
        var custom = blocks == null ? null : blocks.at(block);
        return custom != null && custom.id().equals("overgrown_willow_foliage");
    }

    private RiftRules.Space space(World world) {
        return new RiftRules.Space() {
            @Override
            public boolean open(int x, int y, int z) {
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type.isAir()) {
                    return true;
                }
                return !block.isLiquid() && !Tag.LEAVES.isTagged(type) && block.isReplaceable();
            }

            @Override
            public boolean solid(int x, int y, int z) {
                Block block = world.getBlockAt(x, y, z);
                return block.getType().isSolid() && !block.isReplaceable() && !plantOrLeaves(block);
            }

            @Override
            public boolean inside(int x, int y, int z) {
                return y >= world.getMinHeight() && y + 1 < world.getMaxHeight()
                        && world.getWorldBorder().isInside(new Location(world, x + 0.5, y, z + 0.5));
            }
        };
    }

    // ---- mobs keep away -------------------------------------------------------------------------------------

    private void keepMobsAway(Rift rift) {
        for (Entity entity : rift.world.getNearbyEntities(rift.box().expand(16))) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            var path = mob.getPathfinder().getCurrentPath();
            if (path == null) {
                continue;
            }
            double[] zone = RiftRules.dangerZone(rift.bounds, mob.getWidth(), mob.getHeight());
            if (RiftRules.contains(zone, mob.getX(), mob.getY(), mob.getZ())) {
                continue; // already next to it: let it walk away
            }
            for (Location point : path.getPoints()) {
                if (RiftRules.contains(zone, point.getX() + 0.5, point.getY(), point.getZ() + 0.5)) {
                    mob.getPathfinder().stopPathfinding();
                    break;
                }
            }
        }
    }

    // ---- travel ---------------------------------------------------------------------------------------------

    private void transfer(Entity entity, Rift rift) {
        if (entity.getPersistentDataContainer().has(MobManager.MOB_KEY)) {
            entity.getPassengers().stream()
                    .filter(p -> p.getPersistentDataContainer().has(MobManager.RIG_KEY))
                    .forEach(Entity::remove); // the display rig is rebuilt after the trip
        }
        if (!entity.getPassengers().isEmpty()) {
            return; // riders and their mounts are not carried along
        }
        UUID id = entity.getUniqueId();
        Location target;
        if (rift.intoSift) {
            if (gate != null && !gate.mayEnter(entity)) {
                if (entity instanceof Player player) {
                    gate.refuse(player);
                }
                latched.put(id, tick + cooldown);
                return;
            }
            Location here = entity.getLocation();
            entity.getPersistentDataContainer().set(PortalService.RETURN_POINT, PersistentDataType.STRING, new ReturnPoint(
                    here.getWorld().getKey().toString(), here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch()).serialize());
            Rift origin = rift.origin;
            if (origin != null && rifts.contains(origin) && !origin.closing()) {
                target = origin.location();
            } else {
                World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
                target = sift == null ? null : MainPortalBuilder.anchor(sift);
            }
        } else {
            target = exit(entity, rift);
            entity.getPersistentDataContainer().remove(PortalService.RETURN_POINT);
        }
        if (target == null) {
            latched.put(id, tick + cooldown);
            return;
        }
        Location here = entity.getLocation();
        target.setYaw(here.getYaw());
        target.setPitch(here.getPitch());
        latched.put(id, tick + cooldown);
        entity.teleportAsync(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                latched.put(id, tick + cooldown);
                if (entity instanceof Player player) {
                    player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.0f);
                    advancements.award(player, "the_sift/rifter");
                }
            }
        });
    }

    /** Where a rift out of the Sift leads: a counter rift near the player's respawn point. */
    private Location exit(Entity entity, Rift origin) {
        World main = Bukkit.getWorlds().getFirst();
        Location anchor = null;
        if (entity instanceof Player player) {
            Location respawn = player.getRespawnLocation();
            if (respawn != null && respawn.getWorld() != null && respawn.getWorld().equals(main)) {
                anchor = respawn;
            }
        }
        if (anchor == null) {
            anchor = main.getSpawnLocation();
        }
        for (Rift other : rifts) {
            if (other.intoSift && other.world.equals(main) && !other.closing() && other.expires - tick > 20
                    && Math.hypot(other.x - anchor.getX(), other.z - anchor.getZ()) <= reuseRadius) {
                other.expires = Math.max(other.expires, origin.expires);
                return other.location();
            }
        }
        Player player = entity instanceof Player p ? p : null;
        // a counter rift leads into the Sift: only while the Sift is open (alpha.42a.2)
        if (player != null && (gate == null || gate.state().open())) {
            for (int[] xz : RiftRules.ringColumns(anchor.getBlockX(), anchor.getBlockZ(), counterMin, counterMax, counterStep, counterAngles)) {
                if (!allowed(player, main, xz[0], xz[1])) {
                    continue;
                }
                main.getChunkAt(xz[0] >> 4, xz[1] >> 4); // loads the chunk (counter rifts are rare)
                Rift counter = inColumn(main, xz[0], xz[1], origin.alongX, true, origin, Math.max(20, origin.expires - tick));
                if (counter != null) {
                    return counter.location();
                }
            }
            player.sendMessage(messages.get(player, "rifts.no-exit"));
        }
        // no counter rift (or a mob or item): arrive at the anchor itself
        Location safe = anchor.clone();
        safe.setY(main.getHighestBlockYAt(safe, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        return safe;
    }

    /** Not in spawn protection, and the player may build there (claim plugins cancel the test event). */
    private boolean allowed(Player player, World world, int x, int z) {
        if (spawnProtection) {
            Location spawn = world.getSpawnLocation();
            int radius = Bukkit.getSpawnRadius();
            if (radius > 0 && Math.abs(x - spawn.getBlockX()) <= radius + RiftRules.HALF_WIDTH
                    && Math.abs(z - spawn.getBlockZ()) <= radius + RiftRules.HALF_WIDTH) {
                return false;
            }
        }
        if (!claims) {
            return true;
        }
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        Block block = world.getBlockAt(x, y, z);
        BlockPlaceEvent probe = new BlockPlaceEvent(block, block.getState(), block.getRelative(BlockFace.DOWN),
                ItemStack.of(Material.STONE), player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(probe);
        return !probe.isCancelled() && probe.canBuild();
    }

    // ---- item and jukebox -----------------------------------------------------------------------------------

    /** The sift_rift item: creative players and admins open a rift where they click. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String id = SiftItems.idOf(event.getItem());
        Player player = event.getPlayer();
        if ("music_disc_rift".equals(id) && event.getClickedBlock().getType() == Material.JUKEBOX
                && event.getClickedBlock().getState() instanceof Jukebox box && !box.hasRecord()) {
            Location at = event.getClickedBlock().getLocation();
            boolean near = rifts.stream().anyMatch(r -> !r.closing() && r.world.equals(at.getWorld())
                    && r.location().distanceSquared(at) <= 16 * 16);
            if (near) {
                advancements.award(player, "the_sift/rift_resonance");
            }
            return;
        }
        if (!"sift_rift".equals(id)) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE && !player.hasPermission("thesift.admin")) {
            return;
        }
        Block base = event.getClickedBlock().getRelative(event.getBlockFace());
        if (openFor(player, base) == null) {
            player.sendActionBar(messages.get(player, "rifts.no-space"));
        }
    }
}
