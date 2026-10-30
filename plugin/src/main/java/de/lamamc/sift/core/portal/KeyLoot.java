package de.lamamc.sift.core.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Which portal key a vanilla chest may hold: loot table -> (item, chance), from portals.keys.*.loot. */
public final class KeyLoot {

    public record Entry(String item, double chance) {
    }

    private final Map<String, List<Entry>> byTable = new HashMap<>();

    public void add(String table, String item, double chance) {
        if (chance > 0) {
            byTable.computeIfAbsent(table, t -> new ArrayList<>()).add(new Entry(item, Math.min(1.0, chance)));
        }
    }

    /** The keys this chest gets (usually none). */
    public List<String> roll(String table, RandomGenerator random) {
        List<String> items = new ArrayList<>();
        for (Entry entry : byTable.getOrDefault(table, List.of())) {
            if (random.nextDouble() < entry.chance()) {
                items.add(entry.item());
            }
        }
        return items;
    }

    public boolean isEmpty() {
        return byTable.isEmpty();
    }
}
