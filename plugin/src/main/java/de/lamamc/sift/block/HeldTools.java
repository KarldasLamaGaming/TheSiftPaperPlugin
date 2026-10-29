package de.lamamc.sift.block;

import de.lamamc.sift.core.HeldTool;
import de.lamamc.sift.core.ToolTier;
import de.lamamc.sift.core.ToolType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * Reads the tool kind and tier of a held item. Sift tools are built on netherite tools; their mining
 * speed comes from their tool component (Siftite: items.siftite.tools.mining-speed).
 */
public final class HeldTools {

    private HeldTools() {
    }

    public static HeldTool of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return HeldTool.HAND;
        }
        Material material = stack.getType();
        if (material == Material.SHEARS) {
            return new HeldTool(ToolType.SHEARS, 1.0, -1);
        }
        String name = material.name();
        ToolType type;
        if (name.endsWith("_PICKAXE")) {
            type = ToolType.PICKAXE;
        } else if (name.endsWith("_SHOVEL")) {
            type = ToolType.SHOVEL;
        } else if (name.endsWith("_AXE")) {
            type = ToolType.AXE;
        } else if (name.endsWith("_HOE")) {
            type = ToolType.HOE;
        } else if (name.endsWith("_SWORD")) {
            type = ToolType.SWORD;
        } else {
            return HeldTool.HAND;
        }
        HeldTool held = HeldTool.of(type, tier(name));
        Float componentSpeed = type == ToolType.SWORD ? null : correctToolSpeed(stack);
        return componentSpeed == null ? held : new HeldTool(type, componentSpeed, held.level());
    }

    /** The fastest speed of the tool component's rules that count as the correct tool, or null. */
    private static Float correctToolSpeed(ItemStack stack) {
        Tool tool = stack.getData(DataComponentTypes.TOOL);
        if (tool == null) {
            return null;
        }
        Float best = null;
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed() != null && rule.correctForDrops() == TriState.TRUE && (best == null || rule.speed() > best)) {
                best = rule.speed();
            }
        }
        return best;
    }

    private static ToolTier tier(String name) {
        if (name.startsWith("WOODEN_")) {
            return ToolTier.WOOD;
        }
        if (name.startsWith("STONE_")) {
            return ToolTier.STONE;
        }
        if (name.startsWith("COPPER_")) {
            return ToolTier.COPPER;
        }
        if (name.startsWith("IRON_")) {
            return ToolTier.IRON;
        }
        if (name.startsWith("GOLDEN_")) {
            return ToolTier.GOLD;
        }
        if (name.startsWith("DIAMOND_")) {
            return ToolTier.DIAMOND;
        }
        if (name.startsWith("NETHERITE_")) {
            return ToolTier.NETHERITE;
        }
        return ToolTier.NONE;
    }

    public static boolean silkTouch(ItemStack stack) {
        return level(stack, Enchantment.SILK_TOUCH) > 0;
    }

    public static int fortune(ItemStack stack) {
        return level(stack, Enchantment.FORTUNE);
    }

    private static int level(ItemStack stack, Enchantment enchantment) {
        return stack == null || stack.isEmpty() ? 0 : stack.getEnchantmentLevel(enchantment);
    }
}
