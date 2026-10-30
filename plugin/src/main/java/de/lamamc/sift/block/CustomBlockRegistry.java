package de.lamamc.sift.block;

import de.lamamc.sift.world.SiftWorlds;
import de.lamamc.sift.core.BreakTimeCalculator;
import de.lamamc.sift.core.ToolType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Custom blocks and the vanilla states standing in for them, read from block-mapping.json in the jar. */
public final class CustomBlockRegistry {

    /** Vanilla blocks whose states are repurposed. */
    public static final Set<Material> STAND_IN_BLOCKS = Set.of(Material.MUSHROOM_STEM, Material.BROWN_MUSHROOM_BLOCK,
            Material.RED_MUSHROOM_BLOCK, Material.TRIPWIRE, Material.CHORUS_PLANT);

    private final Map<String, CustomBlock> byState = new HashMap<>();
    private volatile java.util.function.BiPredicate<String, Boolean> worldRule;
    private final Map<String, CustomBlock> defaults = new LinkedHashMap<>();

    public CustomBlockRegistry(List<MappingEntry> entries) {
        for (MappingEntry entry : entries) {
            BlockData data = Bukkit.createBlockData(entry.vanillaState());
            CustomBlock block = new CustomBlock(entry.id(), entry.properties(), data);
            byState.put(data.getAsString(), block);
            defaults.putIfAbsent(entry.id(), block);
        }
    }

    /** The custom block shown by this block, or null for vanilla blocks. */
    public CustomBlock at(Block block) {
        CustomBlock custom = of(block.getBlockData());
        if (custom != null && worldRule != null && !worldRule.test(custom.id(), SiftWorlds.isSift(block.getWorld()))) {
            return null; // e.g. a natural mushroom block outside the Sift
        }
        return custom;
    }

    /** Which custom blocks count in which world (id, in the Sift); null = all everywhere. */
    public void restrictWorlds(java.util.function.BiPredicate<String, Boolean> rule) {
        this.worldRule = rule;
    }

    public CustomBlock of(BlockData data) {
        if (!STAND_IN_BLOCKS.contains(data.getMaterial())) {
            return null;
        }
        return byState.get(data.getAsString());
    }

    /** The state of a custom block with these properties (e.g. axis=x), or null. */
    public CustomBlock state(String id, Map<String, String> properties) {
        for (CustomBlock block : byState.values()) {
            if (block.id().equals(id) && block.properties().equals(properties)) {
                return block;
            }
        }
        return null;
    }

    /** The state a placed block item gets (the first entry of the block in the mapping). */
    public CustomBlock defaultState(String id) {
        return defaults.get(id);
    }

    /** The vanilla states (BlockData#getAsString) of all states of a custom block. */
    public Set<String> statesOf(String id) {
        Set<String> states = new java.util.HashSet<>();
        byState.forEach((state, block) -> {
            if (block.id().equals(id)) {
                states.add(state);
            }
        });
        return states;
    }

    public Set<String> ids() {
        return defaults.keySet();
    }

    /** Tool that mines the vanilla stand-in fast, for the break speed compensation. */
    public static ToolType standInTool(Material material) {
        return material == Material.TRIPWIRE ? ToolType.NONE : ToolType.AXE;
    }

    public static double standInHardness(Material material) {
        return switch (material) {
            case MUSHROOM_STEM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK -> BreakTimeCalculator.MUSHROOM_BLOCK_HARDNESS;
            case CHORUS_PLANT -> BreakTimeCalculator.CHORUS_PLANT_HARDNESS;
            default -> 0.0;
        };
    }
}
