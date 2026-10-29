package de.lamamc.sift.world.gen;

import de.lamamc.sift.core.worldgen.AbandonedPortalPlan;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

/** The "worldgen" section of config.yml. Biome names without namespace. */
public record WorldgenSettings(boolean willowTrees, double bigWillowChance, int denseWillowCount, Set<String> denseWillowBiomes,
                               int sparseWillowRarity, Set<String> sparseWillowBiomes,
                               boolean flowerPatches, int flowerRarity, Set<String> flowerBiomes,
                               int wastesFlowerRarity, Set<String> wastesFlowerBiomes,
                               boolean plantPatches, int plantRarity, Set<String> plantBiomes,
                               boolean surfaceSculk, Set<String> sculkBiomes,
                               boolean abandonedPortals, int portalCellChunks, double portalChance, Set<String> portalBiomes,
                               String portalChestLoot,
                               boolean mainPortal) {

    public static WorldgenSettings load(ConfigurationSection root) {
        ConfigurationSection w = root == null ? null : root.getConfigurationSection("worldgen");
        if (w == null) {
            w = new org.bukkit.configuration.MemoryConfiguration();
        }
        return new WorldgenSettings(
                w.getBoolean("willow-trees.enabled", true), w.getDouble("willow-trees.big-chance", 0.38),
                w.getInt("willow-trees.dense.count", 5), set(w, "willow-trees.dense.biomes"),
                Math.max(1, w.getInt("willow-trees.sparse.rarity", 7)), set(w, "willow-trees.sparse.biomes"),
                w.getBoolean("flower-patches.enabled", true), Math.max(1, w.getInt("flower-patches.rarity", 9)),
                set(w, "flower-patches.biomes"), Math.max(1, w.getInt("flower-patches.wastes.rarity", 7)),
                set(w, "flower-patches.wastes.biomes"),
                w.getBoolean("plant-patches.enabled", true), Math.max(1, w.getInt("plant-patches.rarity", 10)),
                set(w, "plant-patches.biomes"),
                w.getBoolean("surface-sculk.enabled", true), set(w, "surface-sculk.biomes"),
                w.getBoolean("abandoned-portals.enabled", true), Math.max(1, w.getInt("abandoned-portals.cell-chunks", 20)),
                Math.clamp(w.getDouble("abandoned-portals.chance", AbandonedPortalPlan.DEFAULT_CHANCE), 0.0, 1.0),
                set(w, "abandoned-portals.biomes"),
                w.getString("abandoned-portals.chest-loot", "the_sift:chests/abandoned_portal"),
                w.getBoolean("main-portal.enabled", true));
    }

    private static Set<String> set(ConfigurationSection section, String path) {
        List<String> list = section.getStringList(path);
        return Set.copyOf(list);
    }
}
