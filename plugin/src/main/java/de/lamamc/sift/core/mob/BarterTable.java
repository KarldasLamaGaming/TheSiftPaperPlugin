package de.lamamc.sift.core.mob;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Weighted reward table of the Singer's barter (SingerBarter of the original 1.1.1): one entry is drawn by
 * weight, its count uniformly in min … max. Items are ids ("minecraft:iron_block", "the_sift:…"); "#wool"
 * stands for a random wool colour, chosen by the plugin.
 */
public record BarterTable(List<Entry> entries) {

    public record Entry(String item, int weight, int min, int max) {

        public Entry {
            if (weight < 0 || min < 0 || max < min) {
                throw new IllegalArgumentException(item + ": weight " + weight + ", count " + min + " … " + max);
            }
        }
    }

    public record Reward(String item, int count) {
    }

    public BarterTable {
        entries = List.copyOf(entries);
        if (totalWeight(entries) <= 0) {
            throw new IllegalArgumentException("barter table without weight");
        }
    }

    /** The table of the original, in its order (weights out of 200). */
    public static BarterTable original() {
        return new BarterTable(List.of(
                new Entry("minecraft:iron_block", 30, 2, 4),
                new Entry("minecraft:netherite_upgrade_smithing_template", 20, 1, 1),
                new Entry("minecraft:ancient_debris", 19, 4, 8),
                new Entry("the_sift:music_disc_wellspring", 3, 1, 1),
                new Entry("minecraft:gold_block", 20, 1, 2),
                new Entry("minecraft:wheat_seeds", 11, 6, 16),
                new Entry("minecraft:beetroot_seeds", 9, 4, 8),
                new Entry("minecraft:potato", 10, 6, 16),
                new Entry("minecraft:carrot", 10, 8, 20),
                new Entry("minecraft:netherite_ingot", 11, 1, 1),
                new Entry("minecraft:bookshelf", 16, 4, 12),
                new Entry("minecraft:end_stone", 8, 2, 12),
                new Entry("minecraft:chorus_flower", 6, 1, 1),
                new Entry("minecraft:echo_shard", 5, 1, 3),
                new Entry("minecraft:redstone_block", 5, 4, 16),
                new Entry("minecraft:string", 8, 4, 16),
                new Entry("#wool", 9, 9, 24)));
    }

    private static int totalWeight(List<Entry> entries) {
        int total = 0;
        for (Entry entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    public int totalWeight() {
        return totalWeight(entries);
    }

    /** The entry for a roll in 0 … totalWeight − 1 (cumulative, in table order like the original). */
    public Entry entryFor(int roll) {
        int left = roll;
        for (Entry entry : entries) {
            left -= entry.weight();
            if (left < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    public Reward roll(RandomGenerator random) {
        Entry entry = entryFor(random.nextInt(totalWeight()));
        return new Reward(entry.item(), entry.min() + random.nextInt(entry.max() - entry.min() + 1));
    }
}
