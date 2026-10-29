package de.lamamc.sift.item;

/**
 * A Sift item: id without namespace, menu category, the vanilla item it is built on, what it does, and
 * whether it is protected from being used like its vanilla base (see {@link SiftiteGuard}).
 */
public record ItemDefinition(String id, ItemCategory category, String baseItem, ItemKind kind, boolean protectedItem) {

    /** A block item: plain and protected (string-, glass- or mushroom-based items stay out of vanilla recipes). */
    public ItemDefinition(String id, ItemCategory category, String baseItem) {
        this(id, category, baseItem, ItemKind.PLAIN, true);
    }
}
