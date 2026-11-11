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
 * Sculk Enchanter (phase 4, alpha.40, phase4.enchanter): an enchanting table that a ritual has changed. Right-click
 * on a vanilla enchanting table with a Charoit-Kern at enough experience levels makes it one; it keeps the look of
 * the table (sculk particles above it) and opens its own menu that raises enchantments above their vanilla maximum
 * (rules in {@link EnchantBoost}). Which tables are enchanters is kept in the chunk's data
 * ({@code the_sift:sculk_enchanters}); broken, it drops the Sculk Enchanter item, which places one again.
 */
public final class SculkEnchanters implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("the_sift", "sculk_enchanters");
    public static final String ITEM = "sculk_enchanter";
    public static final String CORE = "charoite_core";

    private final Plugin plugin;
    private final SiftItems items;
    private final int ritualLevels;
    private final boolean anvilKeepsBoost;
    private final EnchantBoost.Settings rules;
    /** Enchanters in loaded chunks, per world (for the particles). */
    private final Map<String, Set<Long>> loaded = new HashMap<>();

    public SculkEnchanters(Plugin plugin, SiftItems items, ConfigurationSection config) {
        this.plugin = plugin;
        this.items = items;
        ConfigurationSection c = config == null ? new org.bukkit.configuration.MemoryConfiguration() : config;
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

    // ---- which tables are enchanters ---------------------------------------------------------------------------

    public boolean isEnchanter(Block block) {
        if (block.getType() != Material.ENCHANTING_TABLE) {
            return false;
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
        } else {
            keys.remove(block.getBlockKey());
        }
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        remember(event.getChunk());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        Set<Long> keys = loaded.get(event.getWorld().getName());
        if (keys != null) {
            Chunk chunk = event.getChunk();
            keys.removeIf(key -> Block.getBlockKeyX(key) >> 4 == chunk.getX() && Block.getBlockKeyZ(key) >> 4 == chunk.getZ());
        }
    }

    private void remember(Chunk chunk) {
        int[] stored = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (stored == null) {
            return;
        }
        Set<Long> keys = loaded.computeIfAbsent(chunk.getWorld().getName(), w -> new HashSet<>());
        for (int p : stored) {
            keys.add(Block.getBlockKey((chunk.getX() << 4) + FluidIdentity.localX(p), FluidIdentity.y(p), (chunk.getZ() << 4) + FluidIdentity.localZ(p)));
        }
    }

    /** Sculk souls above the enchanters near players. */
    private void particles() {
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
                if (block.getType() != Material.ENCHANTING_TABLE) {
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
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.ENCHANTING_TABLE
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (isEnchanter(block)) {
            if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) {
                return; // placing a block against it
            }
            event.setCancelled(true);
            EnchanterMenu.open(this, player);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!CORE.equals(SiftItems.idOf(held))) {
            return;
        }
        event.setCancelled(true);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (!creative && player.getLevel() < ritualLevels) {
            player.sendActionBar(Component.text("Das Ritual braucht " + ritualLevels + " Erfahrungslevel.", NamedTextColor.RED));
            return;
        }
        if (!creative) {
            player.giveExpLevels(-ritualLevels);
            held.subtract();
        }
        mark(block, true);
        Location at = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.SCULK_SOUL, at, 40, 0.6, 0.5, 0.6, 0.05);
        block.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, at, 30, 0.6, 0.4, 0.6, 0.02);
        block.getWorld().playSound(at, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.5f, 0.8f);
        block.getWorld().playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.6f);
        player.sendActionBar(Component.text("Der Zaubertisch ist jetzt ein Sculk Enchanter.", NamedTextColor.DARK_AQUA));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.ENCHANTING_TABLE) {
            // a plain table never inherits a stale mark of an enchanter removed without a player
            mark(event.getBlockPlaced(), ITEM.equals(SiftItems.idOf(event.getItemInHand())));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isEnchanter(block)) {
            return;
        }
        mark(block, false);
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
