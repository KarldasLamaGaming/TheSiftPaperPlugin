package de.lamamc.sift.packgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The free states of one vanilla base block that may be given to custom blocks, in a fixed order.
 * Only boolean properties are supported, which covers mushroom blocks, tripwire and chorus plants.
 */
public final class StatePool {

    private final String baseBlock;
    private final List<BlockState> states;
    private final java.util.Set<BlockState> used = new java.util.HashSet<>();
    private int next;

    public StatePool(String baseBlock, List<String> properties, Map<String, String> filter,
                     List<Map<String, String>> exclude) {
        this.baseBlock = baseBlock;
        this.states = enumerate(baseBlock, properties, filter, exclude);
    }

    private static List<BlockState> enumerate(String block, List<String> properties, Map<String, String> filter,
                                              List<Map<String, String>> exclude) {
        for (String key : filter.keySet()) {
            if (!properties.contains(key)) {
                throw new IllegalArgumentException("Filter uses unknown property " + key + " of " + block);
            }
        }
        List<BlockState> result = new ArrayList<>();
        int n = properties.size();
        // first property changes slowest; false before true
        for (int bits = 0; bits < (1 << n); bits++) {
            TreeMap<String, String> props = new TreeMap<>();
            for (int i = 0; i < n; i++) {
                boolean value = (bits >> (n - 1 - i) & 1) == 1;
                props.put(properties.get(i), Boolean.toString(value));
            }
            if (!matches(props, filter)) {
                continue;
            }
            if (exclude.stream().anyMatch(ex -> matches(props, ex))) {
                continue;
            }
            result.add(new BlockState(block, props));
        }
        return result;
    }

    private static boolean matches(Map<String, String> props, Map<String, String> pattern) {
        return pattern.entrySet().stream().allMatch(e -> e.getValue().equals(props.get(e.getKey())));
    }

    public String baseBlock() {
        return baseBlock;
    }

    public int size() {
        return states.size();
    }

    public int remaining() {
        return (int) states.stream().filter(st -> !used.contains(st)).count();
    }

    /** The next free state. */
    public BlockState take() {
        while (next < states.size() && used.contains(states.get(next))) {
            next++;
        }
        if (next >= states.size()) {
            throw new IllegalStateException("No free states left for " + baseBlock + " (" + states.size() + " in pool)");
        }
        BlockState state = states.get(next++);
        used.add(state);
        return state;
    }

    /** Takes exactly this state; it must belong to the pool and still be free. */
    public BlockState takeExact(BlockState state) {
        if (!states.contains(state)) {
            throw new IllegalArgumentException(state + " is not in the pool of " + baseBlock
                    + " (filter/exclude in block-mapping.yml)");
        }
        if (!used.add(state)) {
            throw new IllegalArgumentException(state + " is assigned twice");
        }
        return state;
    }

    /**
     * The next free state that stays the same when vanilla rotates or mirrors it (all horizontal faces
     * equal). Structure templates are placed rotated, which would turn a directional state into the state
     * of another custom block.
     */
    public BlockState takeRotationInvariant() {
        for (BlockState state : states) {
            if (!used.contains(state) && isRotationInvariant(state)) {
                used.add(state);
                return state;
            }
        }
        throw new IllegalStateException("No rotation-invariant states left for " + baseBlock);
    }

    public static boolean isRotationInvariant(BlockState state) {
        var horizontal = java.util.stream.Stream.of("north", "east", "south", "west")
                .map(state.properties()::get).filter(java.util.Objects::nonNull).distinct().count();
        return horizontal <= 1;
    }
}
