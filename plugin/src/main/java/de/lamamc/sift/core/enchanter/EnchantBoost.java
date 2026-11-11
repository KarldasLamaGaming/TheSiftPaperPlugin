package de.lamamc.sift.core.enchanter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rules of the Sculk Enchanter (phase 4, alpha.40, phase4.enchanter): it raises an enchantment that is already at its
 * vanilla maximum by one level at a time, up to maximum + {@code maxBonus} (2). Single-level enchantments (Mending,
 * Silk Touch …) and enchantments outside the whitelist cannot be raised; per enchantment the bonus can be lower
 * (Fortune and Looting +1). At most {@code maxBoostedPerItem} enchantments of one item may be above their maximum.
 */
public final class EnchantBoost {

    /** Costs of one step to maximum + 1 and to maximum + 2. */
    public record Cost(int charoite, int levels) {
    }

    public record Settings(int maxBonus, int maxBoostedPerItem, Set<String> singleLevel, Set<String> whitelist,
                           Map<String, Integer> maxBonusPerEnchantment, List<Cost> costs) {

        /** Bonus allowed for this enchantment (0 = cannot be raised). */
        public int bonusFor(String enchantment, int vanillaMax) {
            if (vanillaMax <= 1 || singleLevel.contains(enchantment) || !whitelist.isEmpty() && !whitelist.contains(enchantment)) {
                return 0;
            }
            return Math.max(0, Math.min(maxBonus, maxBonusPerEnchantment.getOrDefault(enchantment, maxBonus)));
        }
    }

    /** One possible step: raise {@code enchantment} from {@code from} to {@code to} for {@code cost}. */
    public record Option(String enchantment, int from, int to, Cost cost) {
    }

    public enum Refusal { NOT_AT_MAX, AT_LIMIT, NOT_ALLOWED, TOO_MANY_BOOSTED }

    private EnchantBoost() {
    }

    /** Why an enchantment cannot be raised now, or null if it can. */
    public static Refusal check(Settings settings, Map<String, Integer> levels, Map<String, Integer> vanillaMax, String enchantment) {
        int max = vanillaMax.getOrDefault(enchantment, 1);
        int level = levels.getOrDefault(enchantment, 0);
        int bonus = settings.bonusFor(enchantment, max);
        if (bonus == 0) {
            return Refusal.NOT_ALLOWED;
        }
        if (level < max) {
            return Refusal.NOT_AT_MAX;
        }
        if (level >= max + bonus) {
            return Refusal.AT_LIMIT;
        }
        if (level == max && boosted(levels, vanillaMax) >= settings.maxBoostedPerItem()) {
            return Refusal.TOO_MANY_BOOSTED;
        }
        return null;
    }

    /** The steps the menu offers for this item, in the item's order. */
    public static List<Option> options(Settings settings, Map<String, Integer> levels, Map<String, Integer> vanillaMax) {
        List<Option> options = new ArrayList<>();
        for (var entry : levels.entrySet()) {
            String enchantment = entry.getKey();
            if (check(settings, levels, vanillaMax, enchantment) == null) {
                int step = entry.getValue() - vanillaMax.getOrDefault(enchantment, 1); // 0 = to max + 1
                Cost cost = settings.costs().get(Math.min(step, settings.costs().size() - 1));
                options.add(new Option(enchantment, entry.getValue(), entry.getValue() + 1, cost));
            }
        }
        return options;
    }

    /** How many enchantments of the item are above their vanilla maximum. */
    public static int boosted(Map<String, Integer> levels, Map<String, Integer> vanillaMax) {
        return (int) levels.entrySet().stream().filter(e -> e.getValue() > vanillaMax.getOrDefault(e.getKey(), 1)).count();
    }

    /** The levels after applying one option. */
    public static Map<String, Integer> apply(Map<String, Integer> levels, Option option) {
        Map<String, Integer> result = new LinkedHashMap<>(levels);
        result.put(option.enchantment(), option.to());
        return result;
    }

    /** Whether the player can pay (levels in their XP bar, charoite in the menu). */
    public static boolean affordable(Cost cost, int playerLevels, int charoite, boolean creative) {
        return creative || playerLevels >= cost.levels() && charoite >= cost.charoite();
    }
}
