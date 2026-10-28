package de.lamamc.sift.packgen;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A block id with property values, e.g. {@code minecraft:mushroom_stem[down=false,up=true]}.
 * Properties are kept sorted so equal states compare and print equally.
 */
public record BlockState(String id, SortedMap<String, String> properties) {

    public BlockState {
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        properties = Collections.unmodifiableSortedMap(new TreeMap<>(properties));
    }

    public static BlockState of(String id) {
        return new BlockState(id, new TreeMap<>());
    }

    public static BlockState of(String id, Map<String, String> properties) {
        return new BlockState(id, new TreeMap<>(properties));
    }

    /** Parses {@code ns:id[a=b,c=d]}; the namespace defaults to {@code defaultNamespace}. */
    public static BlockState parse(String text, String defaultNamespace) {
        String id = text;
        SortedMap<String, String> props = new TreeMap<>();
        int bracket = text.indexOf('[');
        if (bracket >= 0) {
            if (!text.endsWith("]")) {
                throw new IllegalArgumentException("Missing ']' in block state: " + text);
            }
            id = text.substring(0, bracket);
            String inner = text.substring(bracket + 1, text.length() - 1);
            if (!inner.isBlank()) {
                for (String pair : inner.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("Bad property '" + pair + "' in " + text);
                    }
                    props.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        if (!id.contains(":")) {
            id = defaultNamespace + ":" + id;
        }
        return new BlockState(id, props);
    }

    public BlockState with(Map<String, String> extra) {
        TreeMap<String, String> merged = new TreeMap<>(properties);
        merged.putAll(extra);
        return new BlockState(id, merged);
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) {
            return id;
        }
        return id + properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(",", "[", "]"));
    }
}
