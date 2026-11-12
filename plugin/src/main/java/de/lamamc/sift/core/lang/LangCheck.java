package de.lamamc.sift.core.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares two languages (alpha.42c): every key in both, and the same placeholders in both. Placeholders are those of
 * Minecraft lang files ({@code %s}, {@code %1$s}, {@code %d}) and MiniMessage tags with a name used as placeholder
 * ({@code <count>}); formatting tags (colours, decorations, click, hover …) are not compared.
 */
public final class LangCheck {

    private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[sd]");
    private static final Pattern TAG = Pattern.compile("<([a-z_][a-z0-9_-]*)>");
    /** MiniMessage tags that only format; anything else in angle brackets counts as a placeholder. */
    private static final java.util.Set<String> STYLE = java.util.Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red", "light_purple", "yellow",
            "white", "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "reset", "newline",
            "br", "rainbow", "gradient", "transition", "shadow", "pride");

    private LangCheck() {
    }

    /** Problems, one per line (empty = both languages match). */
    public static List<String> compare(String nameA, Map<String, String> a, String nameB, Map<String, String> b) {
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(a.keySet())) {
            if (!b.containsKey(key)) {
                problems.add(key + ": fehlt in " + nameB);
            } else if (!placeholders(a.get(key)).equals(placeholders(b.get(key)))) {
                problems.add(key + ": Platzhalter " + placeholders(a.get(key)) + " (" + nameA + ") ≠ " + placeholders(b.get(key))
                        + " (" + nameB + ")");
            }
        }
        for (String key : new TreeSet<>(b.keySet())) {
            if (!a.containsKey(key)) {
                problems.add(key + ": fehlt in " + nameA);
            }
        }
        return problems;
    }

    /** Placeholders of one text, sorted (positional %s are counted, numbered ones kept by number). */
    public static List<String> placeholders(String text) {
        List<String> out = new ArrayList<>();
        Matcher f = FORMAT.matcher(text);
        int plain = 0;
        while (f.find()) {
            if (f.group(1) == null) {
                plain++;
                out.add("%" + plain + "$");
            } else {
                out.add("%" + f.group(1));
            }
        }
        Matcher t = TAG.matcher(text);
        while (t.find()) {
            if (!STYLE.contains(t.group(1))) {
                out.add("<" + t.group(1) + ">");
            }
        }
        out.sort(null);
        return out;
    }
}
