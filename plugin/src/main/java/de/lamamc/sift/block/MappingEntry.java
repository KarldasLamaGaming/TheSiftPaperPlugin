package de.lamamc.sift.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One line of {@code block-mapping.json} (written by packgen): a state of a custom block, e.g.
 * {@code the_sift:siftslate_growth[snowy=true]}, and the vanilla state that stands in for it.
 */
public record MappingEntry(String id, Map<String, String> properties, String vanillaState) {

    public MappingEntry {
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /** The repurposed entries in file order; for every block the first entry is its default state. */
    public static List<MappingEntry> read(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<MappingEntry> entries = new ArrayList<>();
        root.getAsJsonObject("repurposed").entrySet().forEach(e ->
                entries.add(parse(e.getKey(), e.getValue().getAsString())));
        return entries;
    }

    static MappingEntry parse(String custom, String vanilla) {
        String id = custom;
        Map<String, String> props = new TreeMap<>();
        int bracket = custom.indexOf('[');
        if (bracket >= 0) {
            id = custom.substring(0, bracket);
            for (String pair : custom.substring(bracket + 1, custom.length() - 1).split(",")) {
                String[] kv = pair.split("=", 2);
                props.put(kv[0], kv[1]);
            }
        }
        String prefix = "the_sift:";
        if (!id.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a Sift block: " + custom);
        }
        return new MappingEntry(id.substring(prefix.length()), props, vanilla);
    }
}
