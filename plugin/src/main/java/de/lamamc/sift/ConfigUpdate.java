package de.lamamc.sift;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Brings an existing config.yml up to date after a plugin update (alpha.37).
 *
 * <p>Until alpha.36 the plugin used {@code copyDefaults(true)}, which also merges new default entries <em>into</em>
 * sections the admin already has. For a recipe that mixes the ingredient keys of the old and the new recipe
 * (pattern of one, keys of both) and breaks it. Now new defaults are only added where nothing exists yet, and a
 * recipe that is already there is never touched. Values that are still exactly an old default (migrations/*.yml)
 * are replaced by the new default; anything an admin changed stays.
 */
public final class ConfigUpdate {

    private static final String RECIPES = "portals.recipes";

    private ConfigUpdate() {
    }

    /** Removes the values that still equal the old defaults (whole recipes, single leaves); returns their paths. */
    public static List<String> dropOldDefaults(ConfigurationSection config, ConfigurationSection oldDefaults) {
        List<String> dropped = new ArrayList<>();
        ConfigurationSection oldRecipes = oldDefaults.getConfigurationSection(RECIPES);
        if (oldRecipes != null) {
            for (String name : oldRecipes.getKeys(false)) {
                String path = RECIPES + "." + name;
                if (config.get(path, null) instanceof ConfigurationSection own && oldRecipes.get(name, null) instanceof ConfigurationSection old
                        && leaves(own).equals(leaves(old))) {
                    config.set(path, null);
                    dropped.add(path);
                }
            }
        }
        for (String path : oldDefaults.getKeys(true)) {
            if (path.startsWith(RECIPES) || oldDefaults.get(path, null) instanceof ConfigurationSection) {
                continue;
            }
            Object own = config.get(path, null);
            if (own != null && same(own, oldDefaults.get(path, null))) {
                config.set(path, null);
                dropped.add(path);
            }
        }
        return dropped;
    }

    /** Adds every default that is missing (with its comments), but nothing inside a recipe the config already has. */
    public static int addMissing(ConfigurationSection config, ConfigurationSection defaults) {
        int added = 0;
        java.util.Set<String> ownRecipes = config.get(RECIPES, null) instanceof ConfigurationSection recipes
                ? recipes.getKeys(false) : java.util.Set.of();
        for (String path : defaults.getKeys(true)) {
            if (config.get(path, null) != null || insideOwnRecipe(ownRecipes, path)) {
                continue;
            }
            Object value = defaults.get(path, null);
            if (value instanceof ConfigurationSection) {
                config.createSection(path);
            } else {
                config.set(path, value);
                added++;
            }
            List<String> comments = defaults.getComments(path);
            if (!comments.isEmpty()) {
                config.setComments(path, comments);
            }
        }
        return added;
    }

    /** Whether the path lies inside a recipe the config had before (those are never merged with a default). */
    private static boolean insideOwnRecipe(java.util.Set<String> ownRecipes, String path) {
        if (!path.startsWith(RECIPES + ".")) {
            return false;
        }
        String rest = path.substring(RECIPES.length() + 1);
        int dot = rest.indexOf('.');
        return dot >= 0 && ownRecipes.contains(rest.substring(0, dot));
    }

    private static Map<String, Object> leaves(ConfigurationSection section) {
        Map<String, Object> leaves = new LinkedHashMap<>();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key, null);
            if (!(value instanceof ConfigurationSection)) {
                leaves.put(key, value instanceof Number n ? (Object) n.doubleValue() : value);
            }
        }
        return leaves;
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
        }
        return a.equals(b);
    }
}
