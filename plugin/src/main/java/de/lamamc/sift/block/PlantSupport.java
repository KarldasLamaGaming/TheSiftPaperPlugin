package de.lamamc.sift.block;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

/** Whether a custom plant may stand on (or hang from) a block, by its {@code blocks.<id>.support} setting. */
public final class PlantSupport {

    private final BlockSettings settings;
    private final CustomBlockRegistry registry;

    public PlantSupport(BlockSettings settings, CustomBlockRegistry registry) {
        this.settings = settings;
        this.registry = registry;
    }

    /** Whether the support block is above the plant (hanging roots) rather than below. */
    public boolean above(String id) {
        BlockSettings.Support support = settings.support(id);
        return support != null && support.above();
    }

    /** @param base the block below the plant (above it for hanging plants) */
    public boolean supports(String id, BlockData base) {
        BlockSettings.Support support = settings.support(id);
        if (support == null) {
            return true;
        }
        if (support.blocks().isEmpty()) {
            return base.getMaterial().isSolid();
        }
        CustomBlock baseCustom = registry.of(base);
        for (String entry : support.blocks()) {
            if (entry.startsWith("#")) {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.fromString(entry.substring(1)), Material.class);
                if (baseCustom == null && tag != null && tag.isTagged(base.getMaterial())) {
                    return true;
                }
            } else if (entry.startsWith("the_sift:")) {
                if (baseCustom != null && entry.equals("the_sift:" + baseCustom.id())) {
                    return true;
                }
            } else if (baseCustom == null && Material.matchMaterial(entry) == base.getMaterial()) {
                return true;
            }
        }
        return false;
    }
}
