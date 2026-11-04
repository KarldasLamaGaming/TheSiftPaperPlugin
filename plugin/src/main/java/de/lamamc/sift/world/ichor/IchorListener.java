package de.lamamc.sift.world.ichor;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import de.lamamc.sift.core.ichor.IchorRules;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.world.SiftWorlds;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Ichor behaviour (alpha.21, config ichor.*). In the Sift all water is ichor: regeneration for living
 * beings in it, drag for mobs and items, no infinite sources, lava forms Sift stone, buckets and bottles
 * filled there become ichor bucket and ichor bottle, and a faint mist rises from sources near players.
 */
public final class IchorListener implements Listener {

    private static final Color[] MIST = {Color.fromRGB(0x4adce8), Color.fromRGB(0xd36be0), Color.fromRGB(0xf2c96b)};

    private final Plugin plugin;
    private final SiftItems items;
    private final CustomBlockRegistry blocks;
    private final Random random = new Random();
    private final boolean regeneration;
    private final int regenAmplifier;
    private final int regenInterval;
    private final int regenDuration;
    private final boolean drag;
    private final int dragInterval;
    private final double dragHorizontal;
    private final double dragVertical;
    private final boolean sourceConversion;
    private final boolean lava;
    private final boolean bucket;
    private final boolean bottles;
    private final boolean mist;
    private final boolean falseAlarm;
    private final int mistInterval;
    private final int mistRadius;
    private final int mistSamples;
    private long ticks;

    public IchorListener(Plugin plugin, SiftItems items, CustomBlockRegistry blocks, ConfigurationSection config) {
        this.plugin = plugin;
        this.items = items;
        this.blocks = blocks;
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        regeneration = c.getBoolean("regeneration.enabled", true);
        regenAmplifier = Math.max(0, c.getInt("regeneration.amplifier", 0));
        regenInterval = Math.max(1, c.getInt("regeneration.interval-ticks", 10));
        regenDuration = IchorRules.regenerationDuration(c.getInt("regeneration.duration-ticks", IchorRules.REGENERATION_TICKS), regenInterval);
        drag = c.getBoolean("drag-mobs.enabled", true);
        dragInterval = Math.max(1, c.getInt("drag-mobs.interval-ticks", 2));
        dragHorizontal = IchorRules.drag(c.getDouble("drag-mobs.horizontal", IchorRules.DRAG_HORIZONTAL), dragInterval);
        dragVertical = IchorRules.drag(c.getDouble("drag-mobs.vertical", IchorRules.DRAG_VERTICAL), dragInterval);
        sourceConversion = c.getBoolean("source-conversion", false);
        lava = c.getBoolean("lava-results", true);
        bucket = c.getBoolean("bucket", true);
        bottles = c.getBoolean("bottles", true);
        mist = c.getBoolean("mist.enabled", true);
        falseAlarm = c.getBoolean("false-alarm", true);
        mistInterval = Math.max(1, c.getInt("mist.interval-ticks", 20));
        mistRadius = Math.max(2, c.getInt("mist.radius", 16));
        mistSamples = Math.max(0, c.getInt("mist.samples", 24));
    }

    public void start() {
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift != null) {
            applyGameRule(sift);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (SiftWorlds.isSift(event.getWorld())) {
            applyGameRule(event.getWorld());
        }
    }

    private void applyGameRule(World sift) {
        sift.setGameRule(GameRules.WATER_SOURCE_CONVERSION, sourceConversion);
    }

    private void tick() {
        ticks++;
        World sift = Bukkit.getWorld(SiftWorlds.DIMENSION);
        if (sift == null) {
            return;
        }
        boolean regen = regeneration && ticks % regenInterval == 0;
        boolean slow = drag && ticks % dragInterval == 0;
        if (regen || slow) {
            for (Entity entity : sift.getEntities()) {
                if (!entity.isInWater() || !entity.isValid()) {
                    continue;
                }
                if (regen && entity instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDuration, regenAmplifier, true, false, true));
                }
                if (slow && (entity instanceof Item || entity instanceof LivingEntity && !(entity instanceof Player))) {
                    Vector v = entity.getVelocity();
                    entity.setVelocity(new Vector(v.getX() * dragHorizontal, v.getY() * dragVertical, v.getZ() * dragHorizontal));
                }
            }
        }
        if (mist && mistSamples > 0 && ticks % mistInterval == 0) {
            for (Player player : sift.getPlayers()) {
                mist(player);
            }
        }
    }

    /** Dust in the colours of the original's ichor mist over a few sources around the player. */
    private void mist(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        for (int i = 0; i < mistSamples; i++) {
            int x = at.getBlockX() + random.nextInt(mistRadius * 2 + 1) - mistRadius;
            int z = at.getBlockZ() + random.nextInt(mistRadius * 2 + 1) - mistRadius;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = at.getBlockY() + 8; y >= at.getBlockY() - 8; y--) {
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.WATER) {
                    if (block.getBlockData() instanceof Levelled level && level.getLevel() == 0
                            && block.getRelative(0, 1, 0).getType().isAir()) {
                        player.spawnParticle(Particle.DUST, x + random.nextDouble(), y + 1.05, z + random.nextDouble(), 1,
                                0.15, 0.05, 0.15, 0, new Particle.DustOptions(MIST[random.nextInt(MIST.length)], 0.8f));
                    }
                    break;
                }
                if (!block.getType().isAir()) {
                    break;
                }
            }
        }
    }

    /** Lava and ichor: flowing lava makes cobbled siftslate, lava onto ichor makes siftslate. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (!lava || !SiftWorlds.isSift(event.getBlock().getWorld())) {
            return;
        }
        String formed = event.getNewState().getType().getKey().getKey();
        String sift = IchorRules.lavaResult(formed);
        CustomBlock block = sift == null ? null : blocks.defaultState(sift);
        if (block != null) {
            event.getNewState().setBlockData(block.data());
        }
    }

    /** A bucket filled in the Sift holds ichor. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!bucket || !SiftWorlds.isSift(event.getBlock().getWorld())) {
            return;
        }
        ItemStack result = event.getItemStack();
        if (result != null && result.getType() == Material.WATER_BUCKET && SiftItems.idOf(result) == null) {
            event.setItemStack(items.create("ichor_bucket", 1));
        }
    }

    /**
     * Glass bottle at an ichor source or ichor cauldron: ichor bottle (cauldron −1). Ichor bottle on a
     * cauldron: +1 level, glass bottle back. Empty bucket on a full ichor cauldron: ichor bucket.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!bottles && !bucket || event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = event.getItem();
        if (hand == null || held == null) {
            return;
        }
        boolean inSift = SiftWorlds.isSift(player.getWorld());
        Block clicked = event.getClickedBlock();
        String id = SiftItems.idOf(held);
        if (bottles && "ichor_bottle".equals(id) && clicked != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            int level = clicked.getType() == Material.CAULDRON ? 0
                    : clicked.getType() == Material.WATER_CAULDRON && clicked.getBlockData() instanceof Levelled l ? l.getLevel() : -1;
            if (level >= 0 && level < 3) {
                event.setCancelled(true);
                Levelled water = (Levelled) Material.WATER_CAULDRON.createBlockData();
                water.setLevel(level + 1);
                clicked.setBlockData(water);
                exchange(player, hand, held, ItemStack.of(Material.GLASS_BOTTLE));
                sound(player, clicked.getLocation(), "the_sift:item.ichor_bottle.empty");
            }
            return;
        }
        if (!inSift || id != null) {
            return;
        }
        if (bottles && held.getType() == Material.GLASS_BOTTLE) {
            if (clicked != null && clicked.getType() == Material.WATER_CAULDRON && event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && clicked.getBlockData() instanceof Levelled l) {
                event.setCancelled(true);
                if (l.getLevel() <= 1) {
                    clicked.setType(Material.CAULDRON);
                } else {
                    l.setLevel(l.getLevel() - 1);
                    clicked.setBlockData(l);
                }
                exchange(player, hand, held, items.create("ichor_bottle", 1));
                sound(player, clicked.getLocation(), "the_sift:item.ichor_bottle.fill");
                return;
            }
            Block source = sourceInView(player);
            if (source != null && (clicked == null || clicked.getType() != Material.WATER_CAULDRON)) {
                event.setCancelled(true);
                exchange(player, hand, held, items.create("ichor_bottle", 1));
                sound(player, source.getLocation(), "the_sift:item.ichor_bottle.fill");
            }
            return;
        }
        if (bucket && held.getType() == Material.BUCKET && clicked != null && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && clicked.getType() == Material.WATER_CAULDRON && clicked.getBlockData() instanceof Levelled l
                && l.getLevel() == l.getMaximumLevel()) {
            event.setCancelled(true);
            clicked.setType(Material.CAULDRON);
            exchange(player, hand, held, items.create("ichor_bucket", 1));
            sound(player, clicked.getLocation(), "the_sift:item.ichor_bucket.fill");
        }
    }

    /** The ichor source the player looks at within reach, or null. */
    private static Block sourceInView(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE) == null
                ? 4.5 : player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE).getValue(), FluidCollisionMode.SOURCE_ONLY);
        Block block = hit == null ? null : hit.getHitBlock();
        return block != null && block.getType() == Material.WATER ? block : null;
    }

    /** One of the held stack becomes {@code result} (in hand if it was the last one, else into the inventory). */
    private static void exchange(Player player, EquipmentSlot hand, ItemStack held, ItemStack result) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            if (!player.getInventory().containsAtLeast(result, 1)) {
                player.getInventory().addItem(result);
            }
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, result);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, held);
        player.getInventory().addItem(result).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    private static void sound(Player player, Location at, String key) {
        player.getWorld().playSound(at, key, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    /**
     * Advancement „False Alarm“ (IchorSnowballAdvancementMixin): a player's ichor snowball hits something within 16
     * blocks of a living warden (alpha.26).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSnowballHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!falseAlarm || !(event.getEntity() instanceof org.bukkit.entity.Snowball ball)
                || !(ball.getShooter() instanceof Player player) || !"ichor_snowball".equals(SiftItems.idOf(ball.getItem()))) {
            return;
        }
        Location impact = ball.getLocation(); // where the snowball hit
        boolean warden = !impact.getWorld().getNearbyEntities(org.bukkit.util.BoundingBox.of(impact, 16, 16, 16),
                e -> e instanceof org.bukkit.entity.Warden w && !w.isDead() && w.isValid()).isEmpty();
        if (warden) {
            var advancement = Bukkit.getAdvancement(new org.bukkit.NamespacedKey("the_sift", "the_sift/false_alarm"));
            if (advancement != null) {
                var progress = player.getAdvancementProgress(advancement);
                progress.getRemainingCriteria().forEach(progress::awardCriteria);
            }
        }
    }

    /** Like the original: a baby sniffer born in or right above ichor does not survive (Sift only). */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Sniffer sniffer && !sniffer.isAdult() && SiftWorlds.isSift(sniffer.getWorld())) {
            Block at = sniffer.getLocation().getBlock();
            if (at.getType() == Material.WATER || at.getRelative(0, -1, 0).getType() == Material.WATER) {
                event.setCancelled(true);
            }
        }
    }
}
