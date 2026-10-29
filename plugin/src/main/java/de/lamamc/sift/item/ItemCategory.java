package de.lamamc.sift.item;

import java.util.Locale;

/** Categories of the item menu, in tab order. */
public enum ItemCategory {
    BLOCKS("Blöcke", "the_sift:siftslate"),
    ORES("Erze", "the_sift:siftslate_siftite_ore"),
    PLANTS_WOOD("Pflanzen/Holz", "the_sift:overgrown_fronds"),
    TOOLS("Werkzeuge", "minecraft:netherite_pickaxe"),
    ARMOR("Rüstung", "minecraft:netherite_chestplate"),
    MATERIALS("Materialien", "the_sift:charoite"),
    MISC("Sonstiges", "the_sift:ichor_snowball");

    private final String title;
    private final String icon;

    ItemCategory(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    /** German name shown in the menu. */
    public String title() {
        return title;
    }

    /** Item id of the tab icon (Sift or vanilla). */
    public String icon() {
        return icon;
    }

    public static ItemCategory parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('/', '_'));
    }

    /**
     * Category of a custom block: ores by name, plants (tripwire stand-ins), saplings and the willow wood
     * set to plants/wood, everything else to blocks.
     */
    public static ItemCategory forBlock(String id, String standInBlock) {
        if (id.endsWith("_ore")) {
            return ORES;
        }
        if (standInBlock.equals("minecraft:tripwire") || id.contains("sapling") || id.contains("willow")) {
            return PLANTS_WOOD;
        }
        return BLOCKS;
    }
}
