package de.lamamc.sift.block;

import de.lamamc.sift.core.BlockProfile;
import de.lamamc.sift.core.DropSpec;
import de.lamamc.sift.core.ToolTier;
import de.lamamc.sift.core.ToolType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/** The custom-blocks, blocks and plant-support sections of config.yml. */
public final class BlockSettings {

    public enum Fallback { DENY, VANILLA }

    /** Where a plant needs its support block and which blocks count ("sift" = the plant-support list). */
    public record Support(boolean above, List<String> blocks) {
    }

    private final boolean placeOnlyInSift;
    private final Fallback fallback;
    private final Map<String, BlockProfile> profiles = new HashMap<>();
    private final Map<String, Support> supports = new HashMap<>();
    private final Map<String, String> fallbackBlocks = new HashMap<>();

    private BlockSettings(boolean placeOnlyInSift, Fallback fallback) {
        this.placeOnlyInSift = placeOnlyInSift;
        this.fallback = fallback;
    }

    public static BlockSettings load(ConfigurationSection root, Logger logger) {
        BlockSettings settings = new BlockSettings(
                root.getBoolean("custom-blocks.place-only-in-sift", true),
                parseEnum(Fallback.class, root.getString("custom-blocks.outside-sift-fallback", "deny"), Fallback.DENY,
                        logger, "custom-blocks.outside-sift-fallback"));
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

    public Fallback fallback() {
        return fallback;
    }

    /** Profile of a custom block; blocks missing in the config break instantly and drop themselves. */
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
