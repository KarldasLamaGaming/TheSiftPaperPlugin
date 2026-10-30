package de.lamamc.sift.item;

import java.util.Locale;

/** What a Sift item does beyond model and name; the values come from config.yml. */
public enum ItemKind {
    /** Material or block item: model, name and id only. */
    PLAIN,
    /** Netherite tool or weapon with Siftite durability, mining speed and damage. */
    SIFTITE_TOOL,
    /** Netherite armor piece with Siftite durability, toughness, knockback resistance and look. */
    SIFTITE_ARMOR,
    /** Smithing template with the vanilla template tooltip. */
    SMITHING_TEMPLATE,
    /** Food (items.food.<id>). */
    FOOD,
    /** Drink with potion effects (items.drinks.<id>). */
    DRINK,
    /** Music disc (items.music-discs.<id>). */
    MUSIC_DISC,
    /** Key that wakes the consoles of a portal (portals.keys). */
    PORTAL_KEY,
    /** Compass pointing at the nearest portal. */
    SIFT_COMPASS;

    public static ItemKind parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
