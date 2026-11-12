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
        // + 42 willow variants of plank/log recipes (scripts/gen_willow_recipes.py, issue #14)
        // + 4 storage block recipes (Siftit-Block, Charoit-Block, both ways; alpha.41.1)
        assertEquals(125, RECIPES.size());
        assertEquals(new RecipeSpec.Result("siftite_block", true, 1), BY_NAME.get("siftite_block").result());
    }

    @Test
    void willowVariantsAreAllLoadedAndAcceptWillow() {
        List<RecipeSpec> willow = RECIPES.stream().filter(r -> r.name().startsWith("willow_")).toList();
        assertEquals(42, willow.size());
        for (RecipeSpec spec : willow) {
            org.junit.jupiter.api.Assertions.assertFalse(spec.skipped(), spec.name() + ": " + spec.skipReason());
            java.util.stream.Stream<RecipeSpec.Choice> choices = spec.type() == RecipeSpec.Type.SHAPED
                    ? spec.key().values().stream() : spec.ingredients().stream();
            org.junit.jupiter.api.Assertions.assertTrue(choices.anyMatch(c -> c.siftItems().stream()
                    .anyMatch(id -> id.contains("willow"))), spec.name() + " takes no willow item");
        }
        for (String name : List.of("willow_chest", "willow_white_bed", "willow_wooden_pickaxe", "willow_shield",
                "willow_campfire", "willow_smoker", "willow_piston_from_cobbled_siftslate")) {
            org.junit.jupiter.api.Assertions.assertTrue(BY_NAME.containsKey(name), name);
        }
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

    private static org.bukkit.configuration.file.YamlConfiguration bundledConfig() throws java.io.IOException {
        try (var in = RecipeSpecTest.class.getResourceAsStream("/config.yml")) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static Set<String> vanilla(RecipeSpec spec) {
        return spec.key().values().stream().flatMap(c -> c.vanillaItems().stream()).collect(Collectors.toSet());
    }

    @Test
    void keyRecipesAreStaggeredOverworldNetherEnd() throws java.io.IOException {
        List<RecipeSpec> all = SiftRecipes.all(ItemRegistry.loadFromJar(MappingEntry.bundled()),
                bundledConfig().getConfigurationSection("portals.recipes"));
        Map<String, RecipeSpec> byName = all.stream().collect(Collectors.toMap(RecipeSpec::name, Function.identity(), (a, b) -> a));
        assertTrue(vanilla(byName.get("echo_resonator")).containsAll(Set.of("minecraft:heavy_core", "minecraft:echo_shard")));
        assertTrue(vanilla(byName.get("soul_ember_core")).containsAll(Set.of("minecraft:netherite_ingot", "minecraft:wither_skeleton_skull")));
        assertTrue(vanilla(byName.get("void_resonator")).containsAll(Set.of("minecraft:elytra", "minecraft:dragon_head")));
        // dragon's breath needs a living dragon; no recipe for the Ancient City key
        assertFalse(vanilla(byName.get("void_resonator")).contains("minecraft:dragon_breath"));
        assertFalse(byName.containsKey("ancient_resonator"));
    }

    @Test
    void aBrokenConfigRecipeFallsBackToTheBuiltInOne() throws java.io.IOException {
        var defaults = bundledConfig().getConfigurationSection("portals.recipes");
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        // a later boss item that does not exist yet, a vanilla typo, and a valid own recipe
        config.set("portals.recipes.echo_resonator", Map.of("type", "minecraft:crafting_shaped", "pattern", List.of("H"),
                "key", Map.of("H", "the_sift:heart_of_a_future_boss"), "result", Map.of("id", "the_sift:echo_resonator")));
        // a boss item (alpha.39) is a valid ingredient
        config.set("portals.recipes.boss_key", Map.of("type", "minecraft:crafting_shaped", "pattern", List.of("H"),
                "key", Map.of("H", "the_sift:warden_heart"), "result", Map.of("id", "the_sift:void_resonator")));
        config.set("portals.recipes.soul_ember_core", Map.of("type", "minecraft:crafting_shaped", "pattern", List.of("N"),
                "key", Map.of("N", "minecraft:netherite_ingott"), "result", Map.of("id", "the_sift:soul_ember_core")));
        config.set("portals.recipes.void_resonator", Map.of("type", "minecraft:crafting_shapeless",
                "ingredients", List.of("minecraft:dragon_head", "minecraft:end_crystal"), "result", Map.of("id", "the_sift:void_resonator")));
        config.set("portals.recipes.my_key", Map.of("type", "minecraft:crafting_shaped", "pattern", List.of("X"),
                "key", Map.of("X", "minecraft:stonee"), "result", Map.of("id", "the_sift:echo_resonator")));
        List<String> warnings = new java.util.ArrayList<>();
        List<RecipeSpec> all = SiftRecipes.all(ItemRegistry.loadFromJar(MappingEntry.bundled()), config.getConfigurationSection("portals.recipes"),
                defaults, id -> !id.endsWith("ingott") && !id.endsWith("stonee"), warnings::add);
        Map<String, RecipeSpec> byName = all.stream().collect(Collectors.toMap(RecipeSpec::name, Function.identity(), (a, b) -> a));
        assertTrue(vanilla(byName.get("echo_resonator")).contains("minecraft:heavy_core"), "unknown Sift item: built-in recipe");
        assertTrue(vanilla(byName.get("soul_ember_core")).contains("minecraft:wither_skeleton_skull"), "typo: built-in recipe");
        assertEquals(RecipeSpec.Type.SHAPELESS, byName.get("void_resonator").type(), "a valid config recipe is taken");
        assertTrue(byName.get("my_key").skipped(), "no built-in recipe of that name: left out");
        org.junit.jupiter.api.Assertions.assertFalse(byName.get("boss_key").skipped(), "boss items work as ingredients");
        assertEquals(List.of("warden_heart"), byName.get("boss_key").key().get('H').siftItems());
        assertEquals(3, warnings.size(), warnings.toString());
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
