package de.lamamc.sift.packgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Maps blocks of the mod namespace to vanilla block states, as configured in {@code block-mapping.yml}.
 */
public final class BlockMapping {

    public static final String MOD_NAMESPACE = "the_sift";

    /** One custom block state (pattern) and the vanilla state that stands in for it. */
    public record Entry(BlockState custom, BlockState vanilla) {
    }

    private final Map<String, List<Entry>> repurposed = new LinkedHashMap<>();
    private final Map<String, BlockState> substitutes = new LinkedHashMap<>();
    private final Map<String, String> particles = new LinkedHashMap<>();

    public static BlockMapping loadDefault() {
        try (InputStream in = BlockMapping.class.getResourceAsStream("/block-mapping.yml")) {
            if (in == null) {
                throw new IllegalStateException("block-mapping.yml missing from the classpath");
            }
            return load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read block-mapping.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static BlockMapping load(Reader reader) {
        Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
        BlockMapping mapping = new BlockMapping();

        Map<String, StatePool> pools = new LinkedHashMap<>();
        Map<String, Object> poolSection = (Map<String, Object>) root.getOrDefault("pools", Map.of());
        poolSection.forEach((block, raw) -> {
            Map<String, Object> pool = (Map<String, Object>) raw;
            List<String> props = (List<String>) pool.get("properties");
            Map<String, String> filter = stringMap(pool.get("filter"));
            List<Map<String, String>> exclude = new ArrayList<>();
            for (Object ex : (List<Object>) pool.getOrDefault("exclude", List.of())) {
                exclude.add(stringMap(ex));
            }
            pools.put(block, new StatePool("minecraft:" + block, props, filter, exclude));
        });

        Map<String, Object> repurposedSection = (Map<String, Object>) root.getOrDefault("repurposed", Map.of());
        repurposedSection.forEach((block, raw) -> {
            StatePool pool = pools.get(block);
            if (pool == null) {
                throw new IllegalArgumentException("No pool defined for " + block);
            }
            for (Object entry : (List<Object>) raw) {
                BlockState custom = BlockState.parse(String.valueOf(entry), MOD_NAMESPACE);
                mapping.repurposed.computeIfAbsent(custom.id(), k -> new ArrayList<>())
                        .add(new Entry(custom, pool.take()));
            }
        });

        Map<String, Object> substituteSection = (Map<String, Object>) root.getOrDefault("substitute", Map.of());
        substituteSection.forEach((block, target) -> {
            String id = MOD_NAMESPACE + ":" + block;
            if (mapping.repurposed.containsKey(id)) {
                throw new IllegalArgumentException(id + " is both repurposed and substituted");
            }
            mapping.substitutes.put(id, BlockState.parse(String.valueOf(target), "minecraft"));
        });

        stringMap(root.get("particles")).forEach((particle, target) ->
                mapping.particles.put(MOD_NAMESPACE + ":" + particle, target));
        return mapping;
    }

    private static Map<String, String> stringMap(Object raw) {
        Map<String, String> result = new TreeMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        }
        return result;
    }

    public boolean isMapped(String id) {
        return repurposed.containsKey(id) || substitutes.containsKey(id);
    }

    public boolean isRepurposed(String id) {
        return repurposed.containsKey(id);
    }

    /**
     * The vanilla state for a state of a mapped block. For repurposed blocks the entry whose properties
     * match is used (the first entry if none matches); substitutes keep the original properties except
     * the ones the target fixes.
     */
    public BlockState resolve(BlockState original) {
        List<Entry> entries = repurposed.get(original.id());
        if (entries != null) {
            for (Entry entry : entries) {
                boolean matches = entry.custom().properties().entrySet().stream()
                        .allMatch(e -> e.getValue().equals(original.properties().get(e.getKey())));
                if (matches) {
                    return entry.vanilla();
                }
            }
            return entries.getFirst().vanilla();
        }
        BlockState target = substitutes.get(original.id());
        if (target != null) {
            return BlockState.of(target.id(), original.properties()).with(target.properties());
        }
        throw new IllegalArgumentException("Block is not mapped: " + original.id());
    }

    /** All vanilla states a mapped block can turn into (one for substitutes). */
    public List<BlockState> allStates(String id) {
        List<Entry> entries = repurposed.get(id);
        if (entries != null) {
            return entries.stream().map(Entry::vanilla).toList();
        }
        BlockState target = substitutes.get(id);
        if (target != null) {
            return List.of(target);
        }
        throw new IllegalArgumentException("Block is not mapped: " + id);
    }

    /** The vanilla block id a mapped block becomes, for places that only know block ids (tags, predicates). */
    public String baseBlock(String id) {
        return allStates(id).getFirst().id();
    }

    public Optional<String> particle(String id) {
        return Optional.ofNullable(particles.get(id));
    }

    public Map<String, List<Entry>> repurposed() {
        return repurposed;
    }

    public Map<String, BlockState> substitutes() {
        return substitutes;
    }
}
