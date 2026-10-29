package de.lamamc.sift.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Set;
import java.util.TreeSet;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Builds Sift items from the {@link ItemRegistry}: a vanilla base item with the model and name of the
 * original and the Sift id in its persistent data. Block items use the item of their stand-in block as
 * base, so placing them works like placing that block; the plugin then sets the custom state.
 */
public final class SiftItems {

    public static final NamespacedKey ID = new NamespacedKey("the_sift", "id");

    private final ItemRegistry registry;
    private final Translations translations;

    public SiftItems(ItemRegistry registry, Translations translations) {
        this.registry = registry;
        this.translations = translations;
    }

    public ItemRegistry registry() {
        return registry;
    }

    /** All ids that {@link #create} knows, sorted. */
    public Set<String> ids() {
        Set<String> ids = new TreeSet<>();
        registry.all().forEach(def -> ids.add(def.id()));
        return ids;
    }

    /** A stack of the Sift item, or null for unknown ids. Accepts "x" and "the_sift:x". */
    public ItemStack create(String id, int amount) {
        ItemDefinition def = registry.get(id);
        if (def == null) {
            return null;
        }
        Material base = Material.matchMaterial(def.baseItem());
        if (base == null || !base.isItem()) {
            throw new IllegalStateException("Sift item " + def.id() + " has no valid base item: " + def.baseItem());
        }
        ItemStack stack = ItemStack.of(base, amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("the_sift", def.id()));
        stack.setData(DataComponentTypes.ITEM_NAME, translations.itemName(def.id()));
        stack.editPersistentDataContainer(pdc -> pdc.set(ID, PersistentDataType.STRING, def.id()));
        return stack;
    }

    /** A stack for a drop id: Sift items for "the_sift:x", vanilla items for "minecraft:x". */
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
}
