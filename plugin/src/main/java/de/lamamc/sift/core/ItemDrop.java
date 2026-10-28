package de.lamamc.sift.core;

/** An item id ("the_sift:x" or "minecraft:x") and an amount. */
public record ItemDrop(String item, int amount) {
}
