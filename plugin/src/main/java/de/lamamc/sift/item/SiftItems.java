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
    private final ItemComponents components;

    public SiftItems(ItemRegistry registry, Translations translations, ItemSettings settings) {
        this.registry = registry;
        this.translations = translations;
        this.components = new ItemComponents(settings, registry, translations);
    }

    public ItemRegistry registry() {
        return registry;
    }

    /** Block id -> the state its item places (alpha.42g); set by the plugin once the block registry exists. */
    private java.util.function.Function<String, org.bukkit.block.data.BlockData> placedState = id -> null;
    private final java.util.Map<String, ItemStack> blockBases = new java.util.HashMap<>();

    /**
     * alpha.42g: block items carry the block state component (minecraft:block_state) with exactly the properties of their
     * Sift state. The client predicts the placed block from it, so a freshly placed Sift block no longer shows the vanilla
     * mushroom block (all faces) until the server's state arrives.
     */
    public void placedStates(java.util.function.Function<String, org.bukkit.block.data.BlockData> states) {
        this.placedState = states;
        blockBases.clear();
    }

    /** The state the item of this block places, or null (no Sift state, e.g. vanilla stand-ins like stairs). */
    public org.bukkit.block.data.BlockData placedState(String id) {
        return placedState.apply(id);
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
        ItemStack stack = blockBase(def.id(), base);
        stack.setAmount(amount);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("the_sift", def.id()));
        stack.setData(DataComponentTypes.ITEM_NAME, translations.itemName(def.id()));
        stack.editPersistentDataContainer(pdc -> pdc.set(ID, PersistentDataType.STRING, def.id()));
        components.apply(def, stack);
        return stack;
    }

    /**
     * The base stack of a block item with its block state component. The Paper API cannot build the component yet
     * (BlockItemDataProperties has no setters in 26.3), so it is parsed like /give input by the server's item factory.
     */
    private ItemStack blockBase(String id, Material base) {
        org.bukkit.block.data.BlockData state = placedState.apply(id);
        // the item that places the state (string for tripwire, the block item for mushroom blocks and chorus)
        if (state == null || state.getPlacementMaterial() != base) {
            return ItemStack.of(base);
        }
        ItemStack template = blockBases.computeIfAbsent(id, k -> {
            try {
                return org.bukkit.Bukkit.getItemFactory().createItemStack(blockStateInput(base.getKey().toString(), state));
            } catch (IllegalArgumentException e) {
                return ItemStack.of(base);
            }
        });
        return template.clone();
    }

    /** Item "minecraft:string", state "minecraft:tripwire[east=true]" -> "minecraft:string[minecraft:block_state={east:"true"}]". */
    static String blockStateInput(String item, org.bukkit.block.data.BlockData state) {
        return blockStateInput(item, state.getAsString());
    }

    static String blockStateInput(String item, String text) {
        int open = text.indexOf('[');
        String key = item;
        if (open < 0) {
            return key;
        }
        StringBuilder props = new StringBuilder();
        for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                props.append(props.isEmpty() ? "" : ",").append(kv[0]).append(":\"").append(kv[1]).append('"');
            }
        }
        return key + "[minecraft:block_state={" + props + "}]";
    }

    /** A stack for a drop id: Sift items for "the_sift:x", vanilla items for "minecraft:x". */
    public ItemStack stackFor(String itemId, int amount) {
        if (itemId.startsWith("the_sift:")) {
            return create(itemId, amount);
        }
        Material material = Material.matchMaterial(itemId);
        return material == null ? null : ItemStack.of(material, amount);
    }

    /** Whether the stack is a Sift item that must not be used like its vanilla base. */
    public boolean isProtected(ItemStack stack) {
        String id = idOf(stack);
        ItemDefinition def = id == null ? null : registry.get(id);
        return def != null && def.protectedItem();
    }

    /** The Sift id of a stack, or null for vanilla items. */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }
}
