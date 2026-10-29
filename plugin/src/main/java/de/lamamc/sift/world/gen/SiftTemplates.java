package de.lamamc.sift.world.gen;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

/**
 * Structure templates of the datapack, decoded once on the main thread so populators (which run on
 * worldgen threads) only read them. Blocks of custom stand-in states keep their Sift id and properties,
 * so they can be rotated without vanilla turning mushroom faces.
 */
public final class SiftTemplates {

    /** A template block; {@code custom} is null for vanilla blocks, {@code loot} only for containers. */
    public record Block(int x, int y, int z, BlockData data, CustomBlock custom, LootTable loot) {

        public boolean is(String siftId) {
            return custom != null && custom.id().equals(siftId);
        }
    }

    public record Template(String name, int sizeX, int sizeY, int sizeZ, List<Block> blocks) {
    }

    private final Map<String, Template> templates = new HashMap<>();

    /** Loads the templates; missing ones are logged and left out. */
    public SiftTemplates(CustomBlockRegistry registry, List<String> names, Logger logger) {
        for (String name : names) {
            Structure structure = Bukkit.getStructureManager().loadStructure(new NamespacedKey("the_sift", name), false);
            if (structure == null || structure.getPalettes().isEmpty()) {
                logger.warning("Structure template the_sift:" + name + " is missing");
                continue;
            }
            List<Block> blocks = new ArrayList<>();
            for (BlockState state : structure.getPalettes().getFirst().getBlocks()) {
                BlockData data = state.getBlockData();
                if (data.getMaterial() == Material.STRUCTURE_VOID) {
                    continue;
                }
                LootTable loot = state instanceof Lootable lootable ? lootable.getLootTable() : null;
                blocks.add(new Block(state.getX(), state.getY(), state.getZ(), data, registry.of(data), loot));
            }
            BlockVector size = structure.getSize();
            templates.put(name, new Template(name, size.getBlockX(), size.getBlockY(), size.getBlockZ(), List.copyOf(blocks)));
        }
    }

    public Template get(String name) {
        return templates.get(name);
    }

    public int size() {
        return templates.size();
    }
}
