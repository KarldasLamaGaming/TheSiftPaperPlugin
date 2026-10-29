package de.lamamc.sift.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A recipe of the original, parsed from its vanilla JSON format, with every item reference resolved to
 * a Sift item, a vanilla item or a vanilla item tag. Recipes the plugin cannot register sensibly come
 * back with a {@link #skipReason()}.
 */
public record RecipeSpec(String name, Type type, String group, String category,
                         List<String> pattern, Map<Character, Choice> key, List<Choice> ingredients,
                         Choice template, Choice base, Choice addition,
                         Result result, float experience, int cookingTime, String skipReason) {

    public enum Type {
        SHAPED, SHAPELESS, SMELTING, SMOKING, BLASTING, CAMPFIRE, STONECUTTING, SMITHING_TRANSFORM
    }

    /** One ingredient slot: any of these Sift items, vanilla items or vanilla item tags. */
    public record Choice(List<String> siftItems, List<String> vanillaItems, List<String> vanillaTags) {
    }

    /** Result item: a Sift id (without namespace) or a vanilla item id. */
    public record Result(String item, boolean sift, int count) {
    }

    /** How item ids of the original are resolved. */
    public interface Items {
        /** Whether "the_sift:x" is a Sift item (id without namespace). */
        boolean isSiftItem(String id);

        /** The vanilla item a Sift block or item is replaced by, or null. */
        String substitute(String id);

        /** Members of a tag of the original ("the_sift:x"), or null if unknown. */
        List<String> siftTag(String tag);
    }

    private static final String NS = "the_sift:";

    public boolean skipped() {
        return skipReason != null;
    }

    public static RecipeSpec parse(String name, JsonObject json, Items items) {
        Parser parser = new Parser(items);
        try {
            return parser.parse(name, json);
        } catch (Skip skip) {
            return new RecipeSpec(name, null, null, null, List.of(), Map.of(), List.of(), null, null, null,
                    null, 0, 0, skip.getMessage());
        }
    }

    private static final class Skip extends RuntimeException {
        Skip(String message) {
            super(message, null, false, false);
        }
    }

    private record Parser(Items items) {

        RecipeSpec parse(String name, JsonObject json) {
            String typeId = json.get("type").getAsString();
            Type type = switch (typeId) {
                case "minecraft:crafting_shaped" -> Type.SHAPED;
                case "minecraft:crafting_shapeless" -> Type.SHAPELESS;
                case "minecraft:smelting" -> Type.SMELTING;
                case "minecraft:smoking" -> Type.SMOKING;
                case "minecraft:blasting" -> Type.BLASTING;
                case "minecraft:campfire_cooking" -> Type.CAMPFIRE;
                case "minecraft:stonecutting" -> Type.STONECUTTING;
                case "minecraft:smithing_transform" -> Type.SMITHING_TRANSFORM;
                default -> throw new Skip("unsupported recipe type " + typeId);
            };
            String group = string(json, "group");
            String category = string(json, "category");
            List<String> pattern = new ArrayList<>();
            Map<Character, Choice> key = new LinkedHashMap<>();
            List<Choice> ingredients = new ArrayList<>();
            Choice template = null;
            Choice base = null;
            Choice addition = null;
            switch (type) {
                case SHAPED -> {
                    json.getAsJsonArray("pattern").forEach(row -> pattern.add(row.getAsString()));
                    json.getAsJsonObject("key").entrySet()
                            .forEach(e -> key.put(e.getKey().charAt(0), choice(e.getValue())));
                }
                case SHAPELESS -> json.getAsJsonArray("ingredients").forEach(i -> ingredients.add(choice(i)));
                case SMITHING_TRANSFORM -> {
                    template = choice(json.get("template"));
                    base = choice(json.get("base"));
                    addition = choice(json.get("addition"));
                }
                default -> ingredients.add(choice(json.get("ingredient")));
            }
            JsonObject resultJson = json.getAsJsonObject("result");
            int count = resultJson.has("count") ? resultJson.get("count").getAsInt() : 1;
            Result result = result(resultJson.get("id").getAsString(), count);
            float experience = json.has("experience") ? json.get("experience").getAsFloat() : 0;
            int cookingTime = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : defaultCookingTime(type);
            return new RecipeSpec(name, type, group, category, List.copyOf(pattern), key, List.copyOf(ingredients),
                    template, base, addition, result, experience, cookingTime, null);
        }

        private Choice choice(JsonElement json) {
            List<String> refs = new ArrayList<>();
            if (json instanceof JsonArray array) {
                array.forEach(el -> refs.add(el.getAsString()));
            } else {
                refs.add(json.getAsString());
            }
            List<String> sift = new ArrayList<>();
            List<String> vanilla = new ArrayList<>();
            List<String> tags = new ArrayList<>();
            for (String ref : refs) {
                add(ref, sift, vanilla, tags);
            }
            return new Choice(List.copyOf(sift), List.copyOf(vanilla), List.copyOf(tags));
        }

        private void add(String ref, List<String> sift, List<String> vanilla, List<String> tags) {
            if (ref.startsWith("#" + NS)) {
                List<String> members = items.siftTag(ref.substring(1));
                if (members == null) {
                    throw new Skip("unknown tag " + ref);
                }
                members.forEach(m -> add(m, sift, vanilla, tags));
            } else if (ref.startsWith("#")) {
                tags.add(ref.substring(1));
            } else if (ref.startsWith(NS)) {
                String id = ref.substring(NS.length());
                if (items.isSiftItem(id)) {
                    sift.add(id);
                } else {
                    // a vanilla substitute as ingredient would make the recipe a copy of (or clash with)
                    // the vanilla recipe of that item
                    throw new Skip("ingredient " + ref + " only exists as vanilla " + items.substitute(id));
                }
            } else {
                vanilla.add(ref);
            }
        }

        private Result result(String ref, int count) {
            if (!ref.startsWith(NS)) {
                return new Result(ref, false, count);
            }
            String id = ref.substring(NS.length());
            if (items.isSiftItem(id)) {
                return new Result(id, true, count);
            }
            String substitute = items.substitute(id);
            if (substitute == null) {
                throw new Skip("result " + ref + " is neither a Sift item nor substituted");
            }
            return new Result(substitute, false, count);
        }

        private static int defaultCookingTime(Type type) {
            return switch (type) {
                case SMELTING -> 200;
                case SMOKING, BLASTING -> 100;
                case CAMPFIRE -> 600;
                default -> 0;
            };
        }

        private static String string(JsonObject json, String key) {
            return json.has(key) ? json.get(key).getAsString() : null;
        }
    }

    /** Helper for {@link Items} implementations backed by functions and maps. */
    public static Items items(Function<String, Boolean> isSift, Function<String, String> substitutes,
                              Map<String, List<String>> siftTags) {
        return new Items() {
            @Override
            public boolean isSiftItem(String id) {
                return isSift.apply(id);
            }

            @Override
            public String substitute(String id) {
                return substitutes.apply(id);
            }

            @Override
            public List<String> siftTag(String tag) {
                return siftTags.get(tag);
            }
        };
    }
}
