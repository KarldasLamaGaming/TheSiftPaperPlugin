package de.lamamc.sift.core.enchanter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnchantBoostTest {

    private static final Map<String, Integer> MAX = Map.of("sharpness", 5, "unbreaking", 3, "fortune", 3, "mending", 1,
            "looting", 3, "efficiency", 5, "protection", 4);
    private static final EnchantBoost.Settings SETTINGS = new EnchantBoost.Settings(2, 2,
            Set.of("mending", "silk_touch", "infinity", "aqua_affinity", "channeling", "flame", "multishot", "binding_curse", "vanishing_curse"),
            Set.of(), Map.of("fortune", 1, "looting", 1),
            List.of(new EnchantBoost.Cost(8, 15), new EnchantBoost.Cost(16, 30)));

    private static Map<String, Integer> levels(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    @Test
    void onlyMaximumEnchantmentsAreRaisedUpToPlusTwo() {
        assertNull(EnchantBoost.check(SETTINGS, levels("sharpness", 5), MAX, "sharpness"));
        assertNull(EnchantBoost.check(SETTINGS, levels("sharpness", 6), MAX, "sharpness"));
        assertEquals(EnchantBoost.Refusal.AT_LIMIT, EnchantBoost.check(SETTINGS, levels("sharpness", 7), MAX, "sharpness"));
        assertEquals(EnchantBoost.Refusal.NOT_AT_MAX, EnchantBoost.check(SETTINGS, levels("sharpness", 4), MAX, "sharpness"));
    }

    @Test
    void singleLevelAndFortuneLootingLimits() {
        assertEquals(EnchantBoost.Refusal.NOT_ALLOWED, EnchantBoost.check(SETTINGS, levels("mending", 1), MAX, "mending"));
        assertNull(EnchantBoost.check(SETTINGS, levels("fortune", 3), MAX, "fortune"));
        assertEquals(EnchantBoost.Refusal.AT_LIMIT, EnchantBoost.check(SETTINGS, levels("fortune", 4), MAX, "fortune"), "Glück nur +1");
        assertEquals(EnchantBoost.Refusal.AT_LIMIT, EnchantBoost.check(SETTINGS, levels("looting", 4), MAX, "looting"));
    }

    @Test
    void atMostTwoBoostedEnchantmentsPerItem() {
        Map<String, Integer> tool = levels("efficiency", 6, "unbreaking", 4, "fortune", 3);
        assertEquals(2, EnchantBoost.boosted(tool, MAX));
        assertEquals(EnchantBoost.Refusal.TOO_MANY_BOOSTED, EnchantBoost.check(SETTINGS, tool, MAX, "fortune"));
        assertNull(EnchantBoost.check(SETTINGS, tool, MAX, "efficiency"), "an already boosted one may go on");
    }

    @Test
    void optionsCarryTheStepCost() {
        var options = EnchantBoost.options(SETTINGS, levels("sharpness", 5, "unbreaking", 4, "mending", 1), MAX);
        assertEquals(2, options.size());
        assertEquals(new EnchantBoost.Option("sharpness", 5, 6, new EnchantBoost.Cost(8, 15)), options.get(0));
        assertEquals(new EnchantBoost.Option("unbreaking", 4, 5, new EnchantBoost.Cost(16, 30)), options.get(1));
        assertEquals(6, EnchantBoost.apply(levels("sharpness", 5), options.get(0)).get("sharpness"));
    }

    @Test
    void whitelistAndPayment() {
        var onlySharpness = new EnchantBoost.Settings(2, 2, Set.of(), Set.of("sharpness"), Map.of(), SETTINGS.costs());
        assertEquals(EnchantBoost.Refusal.NOT_ALLOWED, EnchantBoost.check(onlySharpness, levels("unbreaking", 3), MAX, "unbreaking"));
        assertTrue(EnchantBoost.affordable(new EnchantBoost.Cost(8, 15), 15, 8, false));
        assertFalse(EnchantBoost.affordable(new EnchantBoost.Cost(8, 15), 14, 8, false));
        assertTrue(EnchantBoost.affordable(new EnchantBoost.Cost(8, 15), 0, 0, true));
    }
}
