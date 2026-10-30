package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.lamamc.sift.block.MappingEntry;
import de.lamamc.sift.item.ItemRegistry;
import de.lamamc.sift.item.SiftRecipes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The bundled recipes of the original (pack-src/item-data) as the plugin registers them. */
class RecipeSpecTest {

    private static final List<RecipeSpec> RECIPES = SiftRecipes.bundled(ItemRegistry.loadFromJar(MappingEntry.bundled()));
    private static final Map<String, RecipeSpec> BY_NAME = RECIPES.stream()
            .collect(Collectors.toMap(RecipeSpec::name, Function.identity()));

    @Test
    void allRecipesOfTheOriginalAreRead() {
        // 76 of the original + 3 of the plugin (willow planks and logs where the original uses vanilla tags)
        assertEquals(79, RECIPES.size());
    }

    @Test
    void portalKeyAndCompassRecipesComeFromTheConfig() throws java.io.IOException {
        org.bukkit.configuration.file.YamlConfiguration config;
        try (var in = RecipeSpecTest.class.getResourceAsStream("/config.yml")) {
            config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        List<RecipeSpec> all = SiftRecipes.all(ItemRegistry.loadFromJar(MappingEntry.bundled()),
                config.getConfigurationSection("portals.recipes"));
        assertEquals(RECIPES.size() + 4, all.size());
        for (String name : List.of("echo_resonator", "soul_ember_core", "void_resonator", "sift_compass")) {
            RecipeSpec spec = all.stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
            org.junit.jupiter.api.Assertions.assertFalse(spec.skipped(), name + ": " + spec.skipReason());
            assertEquals(RecipeSpec.Type.SHAPED, spec.type());
            assertEquals(3, spec.pattern().size());
        }
    }

    @Test
    void onlyRecipesWithVanillaSubstitutesAsIngredientAreLeftOut() {
        Set<String> skipped = new TreeSet<>();
        RECIPES.stream().filter(RecipeSpec::skipped).forEach(r -> skipped.add(r.name()));
        // siftslate brick slabs are deepslate brick slabs here (vanilla already makes chiseled deepslate),
        // the willow boat is a mangrove boat (vanilla already makes the chest boat)
        assertEquals(Set.of("chiseled_siftslate", "chiseled_siftslate_from_siftslate_brick_slab_stonecutting",
                "overgrown_willow_chest_boat"), skipped);
    }

    @Test
    void siftiteUpgradeTakesVanillaNetheriteAsBase() {
        RecipeSpec sword = BY_NAME.get("siftite_sword_smithing");
        assertEquals(RecipeSpec.Type.SMITHING_TRANSFORM, sword.type());
        assertEquals(List.of("siftite_upgrade_smithing_template"), sword.template().siftItems());
        assertEquals(List.of("minecraft:netherite_sword"), sword.base().vanillaItems());
        assertTrue(sword.base().siftItems().isEmpty());
        assertEquals(List.of("siftite_ingot"), sword.addition().siftItems());
        assertEquals(new RecipeSpec.Result("siftite_sword", true, 1), sword.result());
        assertEquals(10, RECIPES.stream().filter(r -> r.type() == RecipeSpec.Type.SMITHING_TRANSFORM).count());
    }

    @Test
    void ingredientsAndResultsAreResolved() {
        RecipeSpec ingot = BY_NAME.get("siftite_ingot");
        assertEquals(List.of("siftite_nugget"), ingot.key().get('#').siftItems());
        assertEquals(List.of("###", "###", "###"), ingot.pattern());

        RecipeSpec planks = BY_NAME.get("overgrown_willow_planks");
        assertEquals(List.of("overgrown_willow_log", "overgrown_willow_wood", "stripped_overgrown_willow_log",
                "stripped_overgrown_willow_wood"), planks.ingredients().getFirst().siftItems());
        assertEquals(4, planks.result().count());

        assertEquals(List.of("minecraft:planks"), BY_NAME.get("piston_from_cobbled_siftslate").key().get('T').vanillaTags());
        assertEquals(new RecipeSpec.Result("minecraft:mangrove_door", false, 3), BY_NAME.get("overgrown_willow_door").result());
        assertEquals("minecraft:mangrove_boat", BY_NAME.get("overgrown_willow_boat").result().item());
        assertEquals("minecraft:deepslate_brick_stairs",
                BY_NAME.get("siftslate_brick_stairs_from_siftslate_bricks_stonecutting").result().item());

        RecipeSpec meat = BY_NAME.get("sifter_meat_campfire");
        assertEquals(RecipeSpec.Type.CAMPFIRE, meat.type());
        assertFalse(meat.skipped());
    }

    @Test
    void templateDuplicationNeedsTheTemplate() {
        RecipeSpec template = BY_NAME.get("siftite_upgrade_smithing_template");
        assertEquals(List.of("siftite_upgrade_smithing_template"), template.key().get('S').siftItems());
        assertEquals(List.of("siftslate"), template.key().get('C').siftItems());
        assertEquals(List.of("minecraft:diamond"), template.key().get('#').vanillaItems());
        assertEquals(2, template.result().count());
    }
}
