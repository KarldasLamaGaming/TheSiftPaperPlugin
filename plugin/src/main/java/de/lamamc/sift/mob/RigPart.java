package de.lamamc.sift.mob;

import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

/**
 * An extra item display fixed to a bone of a rig, e.g. a soul block worn as an armour plate: the item (usually a block
 * item) drawn centred on {@code center}, {@code size} model pixels large, turned by {@code rotation} degrees. Positions are
 * in the rig's own coordinates (model pixels, before the model scale). {@code glow}: drawn at full brightness.
 */
public record RigPart(String bone, Vector3f center, Vector3f size, Vector3f rotation, ItemStack item, boolean glow) {

    public static RigPart block(String bone, float x, float y, float z, float size, ItemStack item) {
        return new RigPart(bone, new Vector3f(x, y, z), new Vector3f(size, size, size), new Vector3f(), item, false);
    }
}
