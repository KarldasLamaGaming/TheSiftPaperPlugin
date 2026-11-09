package de.lamamc.sift.block;

import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.BreakTimeCalculator;
import de.lamamc.sift.core.HeldTool;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Makes custom blocks take as long to mine as the block they show: while a player digs one, a temporary
 * block break speed modifier scales the speed of the stand-in block (a mushroom stem breaks in a few
 * ticks) to the custom block's hardness. Client and server both use the attribute, so the crack
 * animation and the actual break match.
 *
 * <p>The modifier has to be in place <em>before</em> digging starts: Paper decides an instant break from the
 * stand-in's progress before BlockDamageEvent, and the client predicts it from its own attribute. A mushroom
 * stem (hardness 0.2) breaks instantly with an iron or better axe, so applying the factor only in
 * BlockDamageEvent let every Sift block on a mushroom stem be mined instantly with such an axe (alpha.35). The
 * factor is therefore set for the block the player looks at (every few ticks) and again on the left click,
 * which Paper fires before computing the progress.
 */
public final class BreakSpeedListener implements Listener {

    private static final NamespacedKey MODIFIER = new NamespacedKey("the_sift", "custom_block_break_speed");

    private final CustomBlockRegistry registry;
    private final BlockSettings settings;
    /** Last factor sent per player (no attribute packet when nothing changed). */
    private final java.util.Map<java.util.UUID, Double> applied = new java.util.HashMap<>();

    public BreakSpeedListener(CustomBlockRegistry registry, BlockSettings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    /** Sets the factor for the block each player looks at, every {@code interval} ticks (0 = off). */
    public void start(org.bukkit.plugin.Plugin plugin, int interval) {
        if (interval > 0) {
            org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::target, interval, interval);
        }
    }

    private void target() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
            org.bukkit.block.Block looked = player.getTargetBlockExact((int) Math.ceil(range == null ? 4.5 : range.getValue()),
                    org.bukkit.FluidCollisionMode.NEVER);
            CustomBlock block = looked == null ? null : registry.at(looked);
            if (block == null) {
                clear(player);
            } else {
                apply(player, factor(player, block, player.getInventory().getItemInMainHand()));
            }
        }
    }

    /** Left click on a block: Paper fires this before it computes the progress and decides an instant break. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        CustomBlock block = registry.at(event.getClickedBlock());
        if (block != null) {
            apply(event.getPlayer(), factor(event.getPlayer(), block, event.getPlayer().getInventory().getItemInMainHand()));
        }
    }

    private double factor(Player player, CustomBlock block, org.bukkit.inventory.ItemStack tool) {
        BlockProfile profile = settings.profile(block.id());
        HeldTool held = HeldTools.of(tool);
        Material standIn = block.data().getMaterial();
        AttributeInstance efficiency = player.getAttribute(Attribute.MINING_EFFICIENCY);
        return BreakTimeCalculator.speedFactor(profile, held, efficiency == null ? 0 : efficiency.getValue(),
                CustomBlockRegistry.standInTool(standIn), CustomBlockRegistry.standInHardness(standIn));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        CustomBlock block = event.isCancelled() ? null : registry.at(event.getBlock());
        if (block == null) {
            clear(player);
            return;
        }
        apply(player, factor(player, block, event.getItemInHand()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        clear(event.getPlayer());
    }

    // the factor was computed for the tool in hand when digging started
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clear(event.getPlayer());
    }

    // a modifier left over from a crash or kick is saved with the player; drop it on join
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private void apply(Player player, double factor) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        Double last = applied.put(player.getUniqueId(), factor);
        if (last != null && Math.abs(last - factor) < 1e-9) {
            return; // unchanged
        }
        speed.removeModifier(MODIFIER);
        if (Math.abs(factor - 1.0) > 1e-6) {
            // ADD_SCALAR / MULTIPLY_SCALAR_1 multiplies the value by (1 + amount)
            speed.addTransientModifier(new AttributeModifier(MODIFIER, factor - 1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    public void clear(Player player) {
        applied.remove(player.getUniqueId());
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed != null && speed.getModifier(MODIFIER) != null) {
            speed.removeModifier(MODIFIER);
        }
    }

    /** Removes the modifier without a listener instance (plugin disable). */
    public static void clearAll(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER);
        }
    }
}
