package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import de.lamamc.sift.block.BlockSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Sift rubble as in the original loot table (and vanilla gravel/flint): Silk Touch gives the rubble,
 * otherwise an ichor shard with 10 / 14.3 / 25 / 100 % for Fortune 0 / I / II / III, else the rubble.
 * Uses the shipped config.yml, so a changed default would show up here.
 */
class RubbleDropTest {

    private static final String RUBBLE = "the_sift:sift_rubble";
    private static final String SHARD = "the_sift:ichor_shard";
    private static BlockProfile rubble;

    @BeforeAll
    static void load() throws Exception {
        try (var in = RubbleDropTest.class.getResourceAsStream("/config.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            rubble = BlockSettings.load(config, Logger.getLogger("test")).profile("sift_rubble");
        }
    }

    private static Map<String, Integer> mine(HeldTool tool, boolean silk, int fortune, int rolls) {
        Random random = new Random(1234);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < rolls; i++) {
            List<ItemDrop> drops = BlockMining.mine(rubble, RUBBLE, tool, silk, fortune, random).drops();
            assertEquals(1, drops.size());
            counts.merge(drops.getFirst().item(), drops.getFirst().amount(), Integer::sum);
        }
        return counts;
    }

    @Test
    void pickaxeWithoutSilkTouchGivesShardsWithTenPercent() {
        Map<String, Integer> counts = mine(HeldTool.of(ToolType.PICKAXE, ToolTier.DIAMOND), false, 0, 20_000);
        int shards = counts.getOrDefault(SHARD, 0);
        assertTrue(shards > 1_800 && shards < 2_200, "shards " + shards);
        assertEquals(20_000, shards + counts.getOrDefault(RUBBLE, 0));
    }

    @Test
    void fortuneRaisesTheChance() {
        int fortune1 = mine(HeldTool.of(ToolType.SHOVEL, ToolTier.IRON), false, 1, 20_000).getOrDefault(SHARD, 0);
        int fortune2 = mine(HeldTool.of(ToolType.SHOVEL, ToolTier.IRON), false, 2, 20_000).getOrDefault(SHARD, 0);
        assertTrue(fortune1 > 2_600 && fortune1 < 3_100, "fortune I " + fortune1);
        assertTrue(fortune2 > 4_700 && fortune2 < 5_300, "fortune II " + fortune2);
        assertEquals(Map.of(SHARD, 1_000), mine(HeldTool.of(ToolType.PICKAXE, ToolTier.IRON), false, 3, 1_000));
    }

    @Test
    void silkTouchAlwaysGivesTheRubble() {
        assertEquals(Map.of(RUBBLE, 1_000), mine(HeldTool.of(ToolType.PICKAXE, ToolTier.IRON), true, 0, 1_000));
        // Silk Touch wins over Fortune III like the alternatives entry of the loot table
        assertEquals(Map.of(RUBBLE, 1_000), mine(HeldTool.of(ToolType.SHOVEL, ToolTier.IRON), true, 3, 1_000));
    }

    @Test
    void handAndAnyToolDropSomething() {
        int byHand = mine(HeldTool.HAND, false, 0, 10_000).values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(10_000, byHand);
    }
}
