package de.lamamc.sift.block;

import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.DropSpec;
import de.lamamc.sift.core.ToolTier;
import de.lamamc.sift.core.ToolType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** The custom-blocks, blocks and plant-support sections of config.yml. */
public final class BlockSettings {

    public enum Fallback { DENY, VANILLA }

    /** Vanilla fire odds of a block: chance to catch fire (ignite) and to burn away (burn). */
    public record Flammability(int ignite, int burn) {
    }

    /** Where a plant needs its support block and which blocks count ("sift" = the plant-support list). */
    public record Support(boolean above, List<String> blocks) {
    }

    private final boolean placeOnlyInSift;
    private final Fallback fallback;
    private final Set<String> outsideSift = new HashSet<>();
    private final Map<String, BlockProfile> profiles = new HashMap<>();
    private final Map<String, Support> supports = new HashMap<>();
    private final Map<String, String> fallbackBlocks = new HashMap<>();
    private final Map<String, Flammability> flammability = new HashMap<>();
    private final Map<String, Integer> fuel = new HashMap<>();

    private BlockSettings(boolean placeOnlyInSift, Fallback fallback) {
        this.placeOnlyInSift = placeOnlyInSift;
        this.fallback = fallback;
    }

    public static BlockSettings load(ConfigurationSection root, Logger logger) {
        BlockSettings settings = new BlockSettings(
                root.getBoolean("custom-blocks.place-only-in-sift", true),
                parseEnum(Fallback.class, root.getString("custom-blocks.outside-sift-fallback", "deny"), Fallback.DENY,
                        logger, "custom-blocks.outside-sift-fallback"));
        settings.outsideSift.addAll(root.getStringList("custom-blocks.placeable-outside-sift"));
        List<String> plantSupport = root.getStringList("plant-support");
        ConfigurationSection blocks = root.getConfigurationSection("blocks");
        if (blocks == null) {
            return settings;
        }
        for (String id : blocks.getKeys(false)) {
            ConfigurationSection section = blocks.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            settings.profiles.put(id, profile(id, section, logger));
            List<Integer> fire = section.getIntegerList("fire");
            if (fire.size() == 2) {
                settings.flammability.put(id, new Flammability(fire.get(0), fire.get(1)));
            }
            if (section.getInt("fuel", 0) > 0) {
                settings.fuel.put(id, section.getInt("fuel"));
            }
            String fallbackBlock = section.getString("fallback");
            if (fallbackBlock != null) {
                settings.fallbackBlocks.put(id, fallbackBlock);
            }
            ConfigurationSection support = section.getConfigurationSection("support");
            if (support != null) {
                List<String> allowed = new ArrayList<>();
                for (String entry : support.getStringList("blocks")) {
                    if (entry.equals("sift")) {
                        allowed.addAll(plantSupport);
                    } else {
                        allowed.add(entry);
                    }
                }
                settings.supports.put(id, new Support("above".equalsIgnoreCase(support.getString("face")), allowed));
            }
        }
        return settings;
    }

    static BlockProfile profile(String id, ConfigurationSection s, Logger logger) {
        String where = "blocks." + id;
        ConfigurationSection drops = s.getConfigurationSection("drops");
        DropSpec.Kind kind = parseEnum(DropSpec.Kind.class, drops == null ? "self" : drops.getString("type", "self"),
                DropSpec.Kind.SELF, logger, where + ".drops.type");
        List<Double> chances = drops == null ? List.of() : drops.getDoubleList("chances");
        DropSpec spec = new DropSpec(kind, drops == null ? null : drops.getString("item"),
                drops == null ? 1 : drops.getInt("min", 1), drops == null ? 1 : drops.getInt("max", 1), chances);
        List<Integer> xp = s.getIntegerList("xp");
        return new BlockProfile(id,
                s.getDouble("hardness", 0.0),
                parseEnum(ToolType.class, s.getString("tool", "none"), ToolType.NONE, logger, where + ".tool"),
                s.getBoolean("requires-tool", false),
                parseEnum(ToolTier.class, s.getString("tier", "wood"), ToolTier.WOOD, logger, where + ".tier"),
                s.getBoolean("unbreakable", false),
                s.getBoolean("explosion-proof", false),
                spec,
                xp.size() == 2 ? xp.get(0) : 0,
                xp.size() == 2 ? xp.get(1) : 0);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback, Logger logger, String where) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            logger.warning("config.yml " + where + ": unknown value '" + value + "', using " + fallback);
            return fallback;
        }
    }

    public boolean placeOnlyInSift() {
        return placeOnlyInSift;
    }

    /**
     * Whether a custom block may stand (be placed, recognised, guarded) in a world. With place-only-in-sift
     * only the ids of custom-blocks.placeable-outside-sift (portal consoles) count outside the Sift, so
     * natural mushroom blocks in other worlds that happen to share a stand-in state stay vanilla.
     */
    public boolean allowedIn(String id, boolean inSift) {
        return inSift || !placeOnlyInSift || outsideSift.contains(id);
    }

    public Fallback fallback() {
        return fallback;
    }

    /** Profile of a custom block; blocks missing in the config break instantly and drop themselves. */
    /** Fire odds of a custom block, or null if it does not burn. */
    public Flammability flammability(String id) {
        return flammability.get(id);
    }

    /** Furnace burn time of the block's item in ticks, 0 if it is no fuel. */
    public int fuel(String id) {
        return fuel.getOrDefault(id, 0);
    }

    public BlockProfile profile(String id) {
        return profiles.getOrDefault(id, new BlockProfile(id, 0.0, ToolType.NONE, false, ToolTier.WOOD, false, false,
                DropSpec.of(DropSpec.Kind.SELF), 0, 0));
    }

    public Support support(String id) {
        return supports.get(id);
    }

    public String fallbackBlock(String id) {
        return fallbackBlocks.get(id);
    }
}
