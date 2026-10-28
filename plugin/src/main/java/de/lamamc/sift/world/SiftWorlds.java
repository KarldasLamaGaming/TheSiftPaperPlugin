package de.lamamc.sift.world;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

/** Identity of the Sift dimension, which the datapack defines and Paper loads as its own world. */
public final class SiftWorlds {

    public static final String NAMESPACE = "the_sift";
    public static final NamespacedKey DIMENSION = new NamespacedKey(NAMESPACE, "the_sift");

    private SiftWorlds() {
    }

    public static boolean isSift(World world) {
        return DIMENSION.equals(world.getKey());
    }
}
