package de.lamamc.sift.world.portal;

import de.lamamc.sift.core.portal.ShrineLayout.Role;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

/** Blocks of the three shrines: a sunken deepslate ruin with sculk, a blackstone shrine, a purpur shrine. */
enum ShrinePalette {
    OVERWORLD(true, "minecraft:cobbled_deepslate", Map.of(
            Role.FLOOR, "minecraft:deepslate_tiles",
            Role.FLOOR_ALT, "minecraft:cracked_deepslate_tiles",
            Role.EDGE, "minecraft:polished_deepslate",
            Role.PILLAR, "minecraft:deepslate_bricks",
            Role.PILLAR_TOP, "minecraft:sculk_catalyst",
            Role.WALL, "minecraft:cracked_deepslate_bricks",
            Role.DECOR, "minecraft:sculk")),
    NETHER(false, "minecraft:basalt[axis=y]", Map.of(
            Role.FLOOR, "minecraft:polished_blackstone_bricks",
            Role.FLOOR_ALT, "minecraft:cracked_polished_blackstone_bricks",
            Role.EDGE, "minecraft:polished_blackstone",
            Role.PILLAR, "minecraft:polished_basalt[axis=y]",
            Role.PILLAR_TOP, "minecraft:soul_lantern[hanging=false]",
            Role.WALL, "minecraft:chiseled_polished_blackstone",
            Role.DECOR, "minecraft:soul_soil")),
    END(false, "minecraft:end_stone", Map.of(
            Role.FLOOR, "minecraft:purpur_block",
            Role.FLOOR_ALT, "minecraft:end_stone_bricks",
            Role.EDGE, "minecraft:purpur_pillar[axis=y]",
            Role.PILLAR, "minecraft:purpur_pillar[axis=y]",
            Role.PILLAR_TOP, "minecraft:end_rod[facing=up]",
            Role.WALL, "minecraft:end_stone_bricks",
            Role.DECOR, "minecraft:end_stone"));

    final boolean ruined;
    private final String foundation;
    private final Map<Role, String> blocks;
    private Map<Role, BlockData> data;
    private BlockData foundationData;

    ShrinePalette(boolean ruined, String foundation, Map<Role, String> blocks) {
        this.ruined = ruined;
        this.foundation = foundation;
        this.blocks = blocks;
    }

    /** Parses the block data once (main thread, at start-up), later only read. */
    void prepare() {
        Map<Role, BlockData> parsed = new EnumMap<>(Role.class);
        blocks.forEach((role, block) -> parsed.put(role, Bukkit.createBlockData(block)));
        parsed.put(Role.FRAME, Bukkit.createBlockData("minecraft:reinforced_deepslate"));
        data = parsed;
        foundationData = Bukkit.createBlockData(foundation);
    }

    BlockData block(Role role) {
        return data.get(role);
    }

    BlockData foundation() {
        return foundationData;
    }
}
