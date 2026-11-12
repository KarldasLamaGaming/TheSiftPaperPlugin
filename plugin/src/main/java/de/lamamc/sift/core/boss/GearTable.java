package de.lamamc.sift.core.boss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Loot table of the temple boss (alpha.42b.2, phase4.bosses.great-echo-golem.loot.gear): one weighted entry is drawn per
 * roll, and each of its enchantments gets a level between its min and max (a min of 0 means it may be missing). Like the
 * enchanted diamond gear of end cities, but netherite and with levels an admin sets.
 */
public record GearTable(List<Entry> entries) {

    /** An item id (e.g. minecraft:netherite_helmet), its weight and enchantment id → {min, max} level. */
    public record Entry(String item, int weight, Map<String, int[]> enchantments) {

        public Entry {
            weight = Math.max(0, weight);
            enchantments = Map.copyOf(enchantments);
        }
    }

    /** One drawn piece: item id and the enchantments with their levels (in the table's order, none at level 0). */
    public record Roll(String item, Map<String, Integer> enchantments) {
    }

    public GearTable {
        entries = List.copyOf(entries);
    }

    public int totalWeight() {
        return entries.stream().mapToInt(Entry::weight).sum();
    }

    /** Draws one piece, or null when the table is empty (or all weights are 0). */
    public Roll roll(RandomGenerator random) {
        int total = totalWeight();
        if (total <= 0) {
            return null;
        }
        int pick = random.nextInt(total);
        for (Entry e : entries) {
            pick -= e.weight();
            if (pick < 0) {
                Map<String, Integer> levels = new LinkedHashMap<>();
                e.enchantments().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(en -> {
                    int min = Math.max(0, Math.min(en.getValue()[0], en.getValue()[1]));
                    int max = Math.max(0, Math.max(en.getValue()[0], en.getValue()[1]));
                    int level = min + random.nextInt(max - min + 1);
                    if (level > 0) {
                        levels.put(en.getKey(), level);
                    }
                });
                return new Roll(e.item(), levels);
            }
        }
        return null;
    }

    /** A level range from a config value: a number (fixed) or a list [min, max]. */
    public static int[] range(Object value) {
        if (value instanceof Number n) {
            return new int[] {n.intValue(), n.intValue()};
        }
        if (value instanceof List<?> list && list.size() == 2 && list.get(0) instanceof Number a && list.get(1) instanceof Number b) {
            return new int[] {a.intValue(), b.intValue()};
        }
        return null;
    }
}
