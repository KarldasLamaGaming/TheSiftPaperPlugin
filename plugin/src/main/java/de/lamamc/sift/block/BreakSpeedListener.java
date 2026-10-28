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
 */
public final class BreakSpeedListener implements Listener {

    private static final NamespacedKey MODIFIER = new NamespacedKey("the_sift", "custom_block_break_speed");

    private final CustomBlockRegistry registry;
    private final BlockSettings settings;

    public BreakSpeedListener(CustomBlockRegistry registry, BlockSettings settings) {
        this.registry = registry;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        CustomBlock block = event.isCancelled() ? null : registry.at(event.getBlock());
        if (block == null) {
            clear(player);
            return;
        }
        BlockProfile profile = settings.profile(block.id());
        HeldTool held = HeldTools.of(event.getItemInHand());
        Material standIn = block.data().getMaterial();
        AttributeInstance efficiency = player.getAttribute(Attribute.MINING_EFFICIENCY);
        double factor = BreakTimeCalculator.speedFactor(profile, held, efficiency == null ? 0 : efficiency.getValue(),
                CustomBlockRegistry.standInTool(standIn), CustomBlockRegistry.standInHardness(standIn));
        apply(player, factor);
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

    private static void apply(Player player, double factor) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(MODIFIER);
        if (Math.abs(factor - 1.0) > 1e-6) {
            // ADD_SCALAR / MULTIPLY_SCALAR_1 multiplies the value by (1 + amount)
            speed.addTransientModifier(new AttributeModifier(MODIFIER, factor - 1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    public static void clear(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed != null) {
            speed.removeModifier(MODIFIER);
        }
    }
}
