package de.lamamc.sift.item;

import de.lamamc.sift.core.SiftiteStats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** The "items" section of config.yml: Siftite values, food, drinks and music discs. */
public record ItemSettings(SiftiteStats siftite, Map<String, Food> food, Map<String, Drink> drinks,
                           Map<String, String> musicDiscs) {

    public record Food(int nutrition, float saturation) {
    }

    /** A potion effect by vanilla id (e.g. "regeneration"), duration in ticks. */
    public record Effect(String type, int duration, int amplifier) {
    }

    /** color as "#rrggbb". */
    public record Drink(String color, List<Effect> effects) {
    }

    public static ItemSettings load(ConfigurationSection items) {
        if (items == null) {
            return new ItemSettings(SiftiteStats.defaults(), Map.of(), Map.of(), Map.of());
        }
        SiftiteStats d = SiftiteStats.defaults();
        SiftiteStats siftite = new SiftiteStats(
                items.getInt("siftite.tools.durability", d.toolDurability()),
                (float) items.getDouble("siftite.tools.mining-speed", d.miningSpeed()),
                items.getDouble("siftite.tools.attack-damage-bonus", d.attackDamageBonus()),
                items.getInt("siftite.armor.durability-multiplier", d.armorDurabilityMultiplier()),
                items.getDouble("siftite.armor.toughness", d.armorToughness()),
                items.getDouble("siftite.armor.knockback-resistance", d.knockbackResistance()));

        Map<String, Food> food = new LinkedHashMap<>();
        ConfigurationSection foodSection = items.getConfigurationSection("food");
        if (foodSection != null) {
            for (String id : foodSection.getKeys(false)) {
                food.put(id, new Food(foodSection.getInt(id + ".nutrition"), (float) foodSection.getDouble(id + ".saturation")));
            }
        }

        Map<String, Drink> drinks = new LinkedHashMap<>();
        ConfigurationSection drinkSection = items.getConfigurationSection("drinks");
        if (drinkSection != null) {
            for (String id : drinkSection.getKeys(false)) {
                List<Effect> effects = new ArrayList<>();
                for (Map<?, ?> effect : drinkSection.getMapList(id + ".effects")) {
                    effects.add(new Effect(String.valueOf(effect.get("type")),
                            ((Number) effect.get("duration")).intValue(),
                            effect.get("amplifier") instanceof Number n ? n.intValue() : 0));
                }
                drinks.put(id, new Drink(drinkSection.getString(id + ".color", "#ffffff"), List.copyOf(effects)));
            }
        }

        Map<String, String> discs = new LinkedHashMap<>();
        ConfigurationSection discSection = items.getConfigurationSection("music-discs");
        if (discSection != null) {
            discSection.getKeys(false).forEach(id -> discs.put(id, discSection.getString(id)));
        }
        return new ItemSettings(siftite, Map.copyOf(food), Map.copyOf(drinks), Map.copyOf(discs));
    }
}
