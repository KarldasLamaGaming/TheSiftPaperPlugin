package de.lamamc.sift.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lamamc.sift.core.RecipeSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/**
 * Registers the recipes of the original ({@code item-data/recipe} in the jar) plus the plugin's own
 * additions ({@code extra-recipes}). Sift ingredients match by Sift id, so renamed or enchanted items
 * still work; vanilla ingredients match only real vanilla items, never a Sift item on the same base.
 */
public final class SiftRecipes {

    public static final String NAMESPACE = "the_sift";

    private final SiftItems items;
    private final Logger logger;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public SiftRecipes(SiftItems items, Logger logger) {
        this.items = items;
        this.logger = logger;
    }

    /** Parses the bundled recipes (pure, for tests and for {@link #register()}). */
    public static List<RecipeSpec> bundled(ItemRegistry registry) {
        Map<String, List<String>> tags = new TreeMap<>();
        readJar("item-data/tags/item").forEach((name, json) -> {
            List<String> values = new ArrayList<>();
            json.getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
            tags.put(NAMESPACE + ":" + name, values);
        });
        RecipeSpec.Items resolver = RecipeSpec.items(id -> registry.get(id) != null, registry::substitute, tags);
        List<RecipeSpec> specs = new ArrayList<>();
        readJar("item-data/recipe").forEach((name, json) -> specs.add(RecipeSpec.parse(name, json, resolver)));
        readJar("extra-recipes").forEach((name, json) -> specs.add(RecipeSpec.parse(name, json, resolver)));
        return specs;
    }

    /** Registers all recipes; returns how many were registered. */
    public int register() {
        int skipped = 0;
        List<Recipe> recipes = new ArrayList<>();
        List<NamespacedKey> keys = new ArrayList<>();
        for (RecipeSpec spec : bundled(items.registry())) {
            if (spec.skipped()) {
                skipped++;
                logger.fine("Recipe " + spec.name() + " skipped: " + spec.skipReason());
                continue;
            }
            recipes.add(toBukkit(spec));
            keys.add(new NamespacedKey(NAMESPACE, spec.name()));
        }
        Bukkit.removeRecipes(keys); // after a plugin reload
        if (!Bukkit.addRecipes(recipes)) {
            logger.warning("Not all Sift recipes could be registered");
        }
        keys.stream().filter(Bukkit::hasRecipe).forEach(registered::add);
        Bukkit.updateRecipes();
        logger.info("Registered " + registered.size() + " Sift recipes (" + skipped + " left out, see docs/ADMIN.md)");
        return registered.size();
    }

    public List<NamespacedKey> keys() {
        return List.copyOf(registered);
    }

    private Recipe toBukkit(RecipeSpec spec) {
        NamespacedKey key = new NamespacedKey(NAMESPACE, spec.name());
        ItemStack result = result(spec.result());
        return switch (spec.type()) {
            case SHAPED -> {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(spec.pattern().toArray(String[]::new));
                spec.key().forEach((symbol, choice) -> recipe.setIngredient(symbol, choice(choice)));
                craftingMeta(spec, recipe::setGroup, recipe::setCategory);
                yield recipe;
            }
            case SHAPELESS -> {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                spec.ingredients().forEach(choice -> recipe.addIngredient(choice(choice)));
                craftingMeta(spec, recipe::setGroup, recipe::setCategory);
                yield recipe;
            }
            case SMELTING -> cooking(spec, new FurnaceRecipe(key, result, choice(spec.ingredients().getFirst()),
                    spec.experience(), spec.cookingTime()));
            case SMOKING -> cooking(spec, new SmokingRecipe(key, result, choice(spec.ingredients().getFirst()),
                    spec.experience(), spec.cookingTime()));
            case BLASTING -> cooking(spec, new BlastingRecipe(key, result, choice(spec.ingredients().getFirst()),
                    spec.experience(), spec.cookingTime()));
            case CAMPFIRE -> cooking(spec, new CampfireRecipe(key, result, choice(spec.ingredients().getFirst()),
                    spec.experience(), spec.cookingTime()));
            case STONECUTTING -> {
                yield new StonecuttingRecipe(key, result, choice(spec.ingredients().getFirst()));
            }
            case SMITHING_TRANSFORM -> new SmithingTransformRecipe(key, result, choice(spec.template()),
                    choice(spec.base()), choice(spec.addition()), true);
        };
    }

    private static void craftingMeta(RecipeSpec spec, java.util.function.Consumer<String> group,
                                     java.util.function.Consumer<CraftingBookCategory> category) {
        if (spec.group() != null) {
            group.accept(spec.group());
        }
        if (spec.category() != null) {
            category.accept(switch (spec.category()) {
                case "building" -> CraftingBookCategory.BUILDING;
                case "redstone" -> CraftingBookCategory.REDSTONE;
                case "equipment" -> CraftingBookCategory.EQUIPMENT;
                default -> CraftingBookCategory.MISC;
            });
        }
    }

    private static <T extends CookingRecipe<T>> T cooking(RecipeSpec spec, T recipe) {
        if (spec.group() != null) {
            recipe.setGroup(spec.group());
        }
        if (spec.category() != null) {
            recipe.setCategory(CookingBookCategory.valueOf(spec.category().toUpperCase(Locale.ROOT)));
        }
        return recipe;
    }

    private ItemStack result(RecipeSpec.Result result) {
        if (result.sift()) {
            return items.create(result.item(), result.count());
        }
        Material material = Material.matchMaterial(result.item());
        if (material == null) {
            throw new IllegalStateException("Unknown result item " + result.item());
        }
        return ItemStack.of(material, result.count());
    }

    /** One slot: the Sift items by id, vanilla items and tags only as real vanilla items. */
    private RecipeChoice choice(RecipeSpec.Choice choice) {
        List<Predicate<ItemStack>> tests = new ArrayList<>();
        ItemStack example = null;
        for (String id : choice.siftItems()) {
            tests.add(stack -> id.equals(SiftItems.idOf(stack)));
            example = example != null ? example : items.create(id, 1);
        }
        for (String id : choice.vanillaItems()) {
            Material material = Material.matchMaterial(id);
            if (material == null) {
                throw new IllegalStateException("Unknown ingredient " + id);
            }
            tests.add(stack -> stack.getType() == material && SiftItems.idOf(stack) == null);
            example = example != null ? example : ItemStack.of(material);
        }
        for (String tagId : choice.vanillaTags()) {
            Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.fromString(tagId), Material.class);
            if (tag == null) {
                throw new IllegalStateException("Unknown item tag " + tagId);
            }
            tests.add(stack -> tag.isTagged(stack.getType()) && SiftItems.idOf(stack) == null);
            example = example != null ? example : ItemStack.of(tag.getValues().iterator().next());
        }
        if (example == null) {
            throw new IllegalStateException("Empty ingredient");
        }
        List<Predicate<ItemStack>> all = List.copyOf(tests);
        return RecipeChoice.predicateChoice(stack -> stack != null && all.stream().anyMatch(t -> t.test(stack)), example);
    }

    /** Recipe JSON files of a directory in the plugin jar, by file name without extension. */
    private static Map<String, JsonObject> readJar(String dir) {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        try {
            var url = SiftRecipes.class.getResource("/" + dir);
            if (url == null) {
                return files;
            }
            var uri = url.toURI();
            if (uri.getScheme().equals("jar")) {
                FileSystem fs;
                boolean opened = false;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                    opened = true;
                } catch (java.nio.file.FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(uri);
                }
                try {
                    collect(fs.getPath("/" + dir), files);
                } finally {
                    if (opened) {
                        fs.close();
                    }
                }
            } else {
                collect(Path.of(uri), files);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    private static void collect(Path dir, Map<String, JsonObject> files) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = dir.relativize(file).toString().replace('\\', '/');
                try (InputStream in = Files.newInputStream(file)) {
                    files.put(name.substring(0, name.length() - ".json".length()),
                            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
                }
            }
        }
    }
}
