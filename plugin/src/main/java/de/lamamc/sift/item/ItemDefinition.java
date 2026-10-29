package de.lamamc.sift.item;

/** A Sift item: id without namespace, menu category and the vanilla item it is built on. */
public record ItemDefinition(String id, ItemCategory category, String baseItem) {
}
