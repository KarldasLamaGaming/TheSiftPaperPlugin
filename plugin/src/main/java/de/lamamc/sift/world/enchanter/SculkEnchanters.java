package de.lamamc.sift.world.enchanter;

import de.lamamc.sift.core.enchanter.EnchantBoost;
import de.lamamc.sift.core.ichor.FluidIdentity;
import de.lamamc.sift.item.SiftItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Sculk Enchanter (phase 4, alpha.40, phase4.enchanter): right-click on a vanilla enchanting table with a Charoit-Kern
 * at enough experience levels turns it into one. Since alpha.40.1 it is an own block (custom block
 * {@code sculk_enchanter} on a mushroom-block state, block-style: block) with a slowly turning charoite crystal above
 * it; with block-style: table it stays a marked enchanting table (alpha.40). Its menu raises enchantments above their
 * vanilla maximum (rules in {@link EnchantBoost}). Where enchanters stand is kept in the chunk's data
 * ({@code the_sift:sculk_enchanters}) for the particles and the crystal; broken, it drops the Sculk Enchanter item.
 */
public final class SculkEnchanters implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "sculk_enchanters");
    public static final String ITEM = "sculk_enchanter";
    /** The custom block (same id as its item). */
    public static final String ID = "sculk_enchanter";
    public static final String CORE = "charoite_core";

    private final Plugin plugin;
    private final SiftItems items;
    private final int ritualLevels;
    private final boolean anvilKeepsBoost;
    private final EnchantBoost.Settings rules;
    private final de.lamamc.sift.block.CustomBlockRegistry blocks;
    /** block-style: block (own block, alpha.40.1) or table (marked enchanting table, alpha.40). */
    private final boolean blockStyle;
    private final boolean floatingCrystal;
    /** Enchanters in loaded chunks, per world (for the particles). */
    private final Map<String, Set<Long>> loaded = new HashMap<>();
    /** The floating crystal above each loaded enchanter (not persistent, rebuilt on load). */
    private final Map<String, Map<Long, org.bukkit.entity.ItemDisplay>> crystals = new HashMap<>();
    private long ticks;
    /** Why an enchanter cannot be used yet (temple enchanter before the victory): a messages key, or null. */
    private java.util.function.Function<Block, String> lock = b -> null;

    public void setLock(java.util.function.Function<Block, String> lock) {
        this.lock = lock;
    }

    public SculkEnchanters(Plugin plugin, SiftItems items, de.lamamc.sift.block.CustomBlockRegistry blocks, ConfigurationSection config) {
        this.plugin = plugin;
        this.items = items;
        this.blocks = blocks;
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
        blockStyle = !"table".equalsIgnoreCase(c.getString("block-style", "block")) && blocks.defaultState(ID) != null;
        floatingCrystal = c.getBoolean("floating-crystal", true);
        ritualLevels = Math.max(0, c.getInt("ritual-levels", 30));
        anvilKeepsBoost = c.getBoolean("anvil-keeps-boost", true);
        Map<String, Integer> perEnchantment = new HashMap<>();
        ConfigurationSection per = c.getConfigurationSection("max-bonus-per-enchantment");
        if (per != null) {
            per.getKeys(false).forEach(k -> perEnchantment.put(k, Math.max(0, per.getInt(k))));
        }
        rules = new EnchantBoost.Settings(Math.max(0, c.getInt("max-bonus", 2)), Math.max(1, c.getInt("max-boosted-per-item", 2)),
                Set.copyOf(c.getStringList("single-level")), Set.copyOf(c.getStringList("whitelist")), perEnchantment,
                List.of(new EnchantBoost.Cost(c.getInt("costs.plus-1.charoite", 8), c.getInt("costs.plus-1.levels", 15)),
                        new EnchantBoost.Cost(c.getInt("costs.plus-2.charoite", 16), c.getInt("costs.plus-2.levels", 30))));
    }

    public EnchantBoost.Settings rules() {
        return rules;
    }

    public SiftItems items() {
        return items;
    }

    Plugin plugin() {
        return plugin;
    }

    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                remember(chunk);
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::particles, 40L, 20L);
    }

    public boolean blockStyle() {
        return blockStyle;
    }

    /** Removes all crystals (plugin disable). */
    public void removeAll() {
        crystals.values().forEach(map -> map.values().forEach(org.bukkit.entity.Entity::remove));
        crystals.clear();
    }

    /** The block an enchanter is in this style (own block or enchanting table). */
    private boolean enchanterBlock(Block block) {
        if (blockStyle) {
            var custom = blocks.at(block);
            return custom != null && custom.id().equals(ID);
        }
        return block.getType() == Material.ENCHANTING_TABLE;
    }

    /**
     * Makes the block at this position an enchanter (ritual, /sift debug enchanter): the own block in block style,
     * an enchanting table in table style. Returns false if it was one already.
     */
    public boolean create(Block block) {
        boolean was = isEnchanter(block);
        if (blockStyle) {
            block.setBlockData(blocks.defaultState(ID).data(), false);
        } else {
            block.setType(Material.ENCHANTING_TABLE, false);
        }
        mark(block, true);
        return !was;
    }

    // ---- which tables are enchanters ---------------------------------------------------------------------------

    public boolean isEnchanter(Block block) {
        if (!enchanterBlock(block)) {
            return false;
        }
        if (blockStyle) {
            return true; // the own block is always an enchanter
        }
        int[] stored = block.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        int packed = FluidIdentity.pack(block.getX(), block.getY(), block.getZ());
        if (stored != null) {
            for (int p : stored) {
                if (p == packed) {
                    return true;
                }
            }
        }
        return false;
    }

    public void mark(Block block, boolean enchanter) {
        var pdc = block.getChunk().getPersistentDataContainer();
        int[] stored = pdc.get(KEY, PersistentDataType.INTEGER_ARRAY);
        Set<Integer> marks = new HashSet<>();
        if (stored != null) {
            for (int p : stored) {
                marks.add(p);
            }
        }
        int packed = FluidIdentity.pack(block.getX(), block.getY(), block.getZ());
        boolean changed = enchanter ? marks.add(packed) : marks.remove(packed);
        if (!changed) {
            return;
        }
        if (marks.isEmpty()) {
            pdc.remove(KEY);
        } else {
            pdc.set(KEY, PersistentDataType.INTEGER_ARRAY, marks.stream().mapToInt(Integer::intValue).toArray());
        }
        Set<Long> keys = loaded.computeIfAbsent(block.getWorld().getName(), w -> new HashSet<>());
        if (enchanter) {
            keys.add(block.getBlockKey());
            showCrystal(block.getWorld(), block.getBlockKey());
        } else {
            keys.remove(block.getBlockKey());
            hideCrystal(block.getWorld(), block.getBlockKey());
        }
    }

    // ---- the floating crystal -------------------------------------------------------------------------------

    private void showCrystal(World world, long key) {
        if (!floatingCrystal || !blockStyle) {
            return;
        }
        Map<Long, org.bukkit.entity.ItemDisplay> map = crystals.computeIfAbsent(world.getName(), w -> new HashMap<>());
        org.bukkit.entity.ItemDisplay existing = map.get(key);
        if (existing != null && existing.isValid()) {
            return;
        }
        ItemStack crystal = items.create(CORE, 1);
        if (crystal == null) {
            return;
        }
        Location at = new Location(world, Block.getBlockKeyX(key) + 0.5, Block.getBlockKeyY(key) + 1.55, Block.getBlockKeyZ(key) + 0.5);
        map.put(key, world.spawn(at, org.bukkit.entity.ItemDisplay.class, display -> {
            display.setPersistent(false); // never saved: rebuilt when the chunk loads
            display.setItemStack(crystal);
            display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            display.setTransformation(transformation(0));
            display.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true);
        }));
    }

    private void hideCrystal(World world, long key) {
        Map<Long, org.bukkit.entity.ItemDisplay> map = crystals.get(world.getName());
        org.bukkit.entity.ItemDisplay display = map == null ? null : map.remove(key);
        if (display != null) {
            display.remove();
        }
    }

    /** Slowly turning crystal: a quarter turn every 2 seconds, interpolated by the client. */
    private static org.bukkit.util.Transformation transformation(long step) {
        float angle = (float) (step % 4 * Math.PI / 2);
        return new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(angle, 0, 1, 0),
                new org.joml.Vector3f(0.5f, 0.5f, 0.5f), new org.joml.AxisAngle4f());
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // entities are not spawned inside the load event; the crystal comes a tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                remember(chunk);
            }
        });
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        Set<Long> keys = loaded.get(event.getWorld().getName());
        if (keys != null) {
            Chunk chunk = event.getChunk();
            for (long key : List.copyOf(keys)) {
                if (Block.getBlockKeyX(key) >> 4 == chunk.getX() && Block.getBlockKeyZ(key) >> 4 == chunk.getZ()) {
                    keys.remove(key);
                    hideCrystal(event.getWorld(), key);
                }
            }
        }
    }

    /**
     * Up to alpha.41.1 the enchanter block was a state that natural red mushroom caps also have; a marked enchanter
     * still in that state gets the current one (alpha.41.2). The mark makes sure it is an enchanter and not a cap.
     */
    private void migrate(Block block) {
        if (blockStyle && LEGACY_STATE.equals(block.getBlockData().getAsString())) {
            block.setBlockData(blocks.defaultState(ID).data(), false);
        }
    }

    static final String LEGACY_STATE = "minecraft:red_mushroom_block[down=false,east=false,north=false,south=true,up=false,west=false]";

    private void remember(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (stored == null) {
            return;
        }
        Set<Long> keys = loaded.computeIfAbsent(chunk.getWorld().getName(), w -> new HashSet<>());
        for (int p : stored) {
            long key = Block.getBlockKey((chunk.getX() << 4) + FluidIdentity.localX(p), FluidIdentity.y(p), (chunk.getZ() << 4) + FluidIdentity.localZ(p));
            migrate(chunk.getWorld().getBlockAt(Block.getBlockKeyX(key), Block.getBlockKeyY(key), Block.getBlockKeyZ(key)));
            keys.add(key);
            showCrystal(chunk.getWorld(), key);
        }
    }

    /** Sculk souls above the enchanters near players. */
    private void particles() {
        ticks++;
        if (ticks % 2 == 0) {
            for (var map : crystals.values()) {
                for (var display : map.values()) {
                    if (display.isValid()) {
                        display.setInterpolationDelay(0);
                        display.setInterpolationDuration(40);
                        display.setTransformation(transformation(ticks / 2));
                    }
                }
            }
        }
        for (var entry : loaded.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null || entry.getValue().isEmpty() || world.getPlayers().isEmpty()) {
                continue;
            }
            for (long key : List.copyOf(entry.getValue())) {
                Location at = new Location(world, Block.getBlockKeyX(key) + 0.5, Block.getBlockKeyY(key) + 1.1, Block.getBlockKeyZ(key) + 0.5);
                if (world.getNearbyPlayers(at, 24).isEmpty()) {
                    continue;
                }
                Block block = at.getBlock().getRelative(0, -1, 0);
                if (!enchanterBlock(block)) {
                    mark(block, false); // removed without a player (command, other plugin)
                    continue;
                }
                world.spawnParticle(Particle.SCULK_SOUL, at, 1, 0.25, 0.1, 0.25, 0.01);
            }
        }
    }

    // ---- ritual, placing, breaking, using ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (isEnchanter(block)) {
            if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) {
                return; // placing a block against it
            }
            event.setCancelled(true);
            String locked = lock.apply(block);
            if (locked != null) {
                player.sendActionBar(de.lamamc.sift.Messages.current().get(player, locked));
                return;
            }
            EnchanterMenu.open(this, player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (block.getType() != Material.ENCHANTING_TABLE || !CORE.equals(SiftItems.idOf(held))) {
            return;
        }
        event.setCancelled(true);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < ritualLevels) {
            player.sendActionBar(de.lamamc.sift.Messages.current().get(player, "enchanter.ritual-levels", "levels", String.valueOf(ritualLevels)));
            return;
        }
        if (!creative) {
            player.giveExpLevels(-ritualLevels);
            held.subtract();
        }
        create(block);
        Location at = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 40, 0.6, 0.5, 0.6, 0.05);
        block.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, at, 30, 0.6, 0.4, 0.6, 0.02);
        block.getWorld().playSound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.8f);
        block.getWorld().playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.6f);
        player.sendActionBar(de.lamamc.sift.Messages.current().get(player, "enchanter.ritual-done"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        boolean enchanterItem = ITEM.equals(SiftItems.idOf(event.getItemInHand()));
        if (enchanterItem && !blockStyle) {
            // table style: the item (a block item since alpha.40.1) places a marked enchanting table
            Bukkit.getScheduler().runTask(plugin, () -> create(placed));
            return;
        }
        if (enchanterItem && enchanterBlock(placed)) {
            mark(placed, true);
        } else if (placed.getType() == Material.ENCHANTING_TABLE) {
            mark(placed, false); // a plain table never inherits a stale mark of an enchanter removed without a player
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isEnchanter(block)) {
            return;
        }
        mark(block, false);
        if (blockStyle) {
            return; // the custom block listener drops the block item (blocks.sculk_enchanter)
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack drop = items.create(ITEM, 1);
            if (drop != null) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }
    }

    // ---- anvil: keep levels above the maximum ------------------------------------------------------------------

    /**
     * Vanilla's anvil cuts levels above the maximum when it combines two items; a boosted enchantment of either input
     * keeps its level in the result (phase4.enchanter.anvil-keeps-boost).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (!anvilKeepsBoost || result == null || result.isEmpty()) {
            return;
        }
        Map<Enchantment, Integer> keep = new HashMap<>();
        for (ItemStack input : new ItemStack[] {event.getInventory().getFirstItem(), event.getInventory().getSecondItem()}) {
            if (input == null) {
                continue;
            }
            input.getEnchantments().forEach((enchantment, level) -> {
                if (level > enchantment.getMaxLevel()) {
                    keep.merge(enchantment, level, Math::max);
                }
            });
        }
        if (keep.isEmpty()) {
            return;
        }
        ItemStack fixed = result.clone();
        keep.forEach((enchantment, level) -> {
            if (fixed.getEnchantments().containsKey(enchantment) && fixed.getEnchantmentLevel(enchantment) < level) {
                fixed.addUnsafeEnchantment(enchantment, level);
            }
        });
        event.setResult(fixed);
    }

    // ---- helpers for the menu ----------------------------------------------------------------------------------

    /** The item's enchantments by key (without namespace) and the vanilla maximum of each. */
    static Map<String, Integer> levels(ItemStack item) {
        Map<String, Integer> levels = new java.util.LinkedHashMap<>();
        item.getEnchantments().forEach((enchantment, level) -> levels.put(enchantment.getKey().getKey(), level));
        return levels;
    }

    static Map<String, Integer> vanillaMax(ItemStack item) {
        Map<String, Integer> max = new HashMap<>();
        item.getEnchantments().keySet().forEach(enchantment -> max.put(enchantment.getKey().getKey(), enchantment.getMaxLevel()));
        return max;
    }

    static Enchantment enchantment(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    static List<EnchantBoost.Option> options(EnchantBoost.Settings rules, ItemStack item) {
        if (item == null || item.isEmpty() || item.getType() == Material.ENCHANTED_BOOK) {
            return new ArrayList<>();
        }
        return EnchantBoost.options(rules, levels(item), vanillaMax(item));
    }
}
