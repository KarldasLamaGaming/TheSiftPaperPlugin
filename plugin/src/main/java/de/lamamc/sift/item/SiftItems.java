package de.lamamc.sift.item;

import de.lamamc.sift.block.CustomBlock;
import de.lamamc.sift.block.CustomBlockRegistry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds Sift items: a vanilla base item with the model and name of the original and the Sift id in
 * its persistent data. Block items use the item of their stand-in block as base, so placing them works
 * like placing that block; the plugin then sets the custom state.
 */
public final class SiftItems {

    public static final NamespacedKey ID = new NamespacedKey("the_sift", "id");

    /**
     * Plain items that blocks drop. Step 5 moves items into config.yml together with tools and armor.
     * Bases without own use where possible; the snowball stays throwable like the original.
     */
    private static final Map<String, Material> MATERIALS = new LinkedHashMap<>(Map.of(
            "charoite", Material.PAPER,
            "siftite_nugget", Material.PAPER,
            "ichor_shard", Material.PAPER,
            "ichor_snowball", Material.SNOWBALL,
            "sculkflower_seeds", Material.PAPER));

    private final CustomBlockRegistry blocks;
    private final Translations translations;

    public SiftItems(CustomBlockRegistry blocks, Translations translations) {
        this.blocks = blocks;
        this.translations = translations;
    }

    /** All ids that {@link #create} knows. */
    public Set<String> ids() {
        Set<String> ids = new TreeSet<>(blocks.ids());
        ids.addAll(MATERIALS.keySet());
        return ids;
    }

    /** A stack of the Sift item, or null for unknown ids. Accepts "x" and "the_sift:x". */
    public ItemStack create(String id, int amount) {
        id = stripNamespace(id);
        Material base;
        CustomBlock block = blocks.defaultState(id);
        if (block != null) {
            base = CustomBlockRegistry.itemBase(block.data().getMaterial());
        } else {
            base = MATERIALS.get(id);
        }
        if (base == null) {
            return null;
        }
        ItemStack stack = ItemStack.of(base, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("the_sift", id));
        stack.setData(DataComponentTypes.ITEM_NAME, translations.itemName(id));
        String finalId = id;
        stack.editPersistentDataContainer(pdc -> pdc.set(ID, PersistentDataType.STRING, finalId));
        return stack;
    }

    /**
     * A stack for a drop id: Sift items for "the_sift:x", vanilla items for "minecraft:x".
     */
    public ItemStack stackFor(String itemId, int amount) {
        if (itemId.startsWith("the_sift:")) {
            return create(itemId, amount);
        }
        Material material = Material.matchMaterial(itemId);
        return material == null ? null : ItemStack.of(material, amount);
    }

    /** The Sift id of a stack, or null for vanilla items. */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }

    private static String stripNamespace(String id) {
        return id.startsWith("the_sift:") ? id.substring("the_sift:".length()) : id;
    }
}
