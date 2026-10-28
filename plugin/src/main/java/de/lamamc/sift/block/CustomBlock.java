package de.lamamc.sift.block;

import java.util.Map;
import org.bukkit.block.data.BlockData;

/** A state of a custom block (id without namespace, e.g. "siftslate") and its vanilla stand-in. */
public record CustomBlock(String id, Map<String, String> properties, BlockData data) {
}
