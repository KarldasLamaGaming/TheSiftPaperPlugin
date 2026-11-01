package de.lamamc.sift.mob.singer;

import com.destroystokyo.paper.entity.ai.Goal;
import de.lamamc.sift.core.mob.Animation;
import de.lamamc.sift.core.mob.BarterTable;
import de.lamamc.sift.core.mob.SingerRules;
import de.lamamc.sift.item.SiftItems;
import de.lamamc.sift.mob.MobKind;
import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import de.lamamc.sift.world.portal.PortalService;
import de.lamamc.sift.world.portal.SonorousConsoles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.WanderingTrader;

/**
 * Singer (SingerEntity of the original 1.1.1). Called by a goat horn near a sleeping portal it rises from the
 * ground in the portal, sings and sends sound waves to the consoles, which wake one after another, and sinks
 * back. From a spawn egg or /sift spawn it stays: it wanders near where it appeared and trades a soul block
 * for a reward (SingerBarter). Carried by an invisible wandering trader without goals (the original's
 * PathfinderMob has none either); not a monster, so it stays on Peaceful.
 */
public final class SingerKind extends MobKind {

    public static final String ID = "singer";

    private final SingerRules rules;
    private final BarterTable barter;
    private final PortalService portals;
    private final SonorousConsoles consoles;
    private final SiftItems items;

    public SingerKind(ConfigurationSection config, PortalService portals, SonorousConsoles consoles, SiftItems items) {
        super(ID, config);
        this.portals = portals;
        this.consoles = consoles;
        this.items = items;
        Map<String, Animation> animations = assets().animations();
        List<Integer> waves = section(config, "song").getIntegerList("wave-ticks");
        this.rules = new SingerRules(length(animations, "appear"), length(animations, "sing"), length(animations, "disappear"),
                waves.isEmpty() ? SingerRules.defaultWaveTicks() : waves.stream().mapToInt(Integer::intValue).toArray(),
                section(config, "song").getDouble("wave-speed", 0.6), section(config, "song").getDouble("head-height", 3.0));
        this.barter = barterTable(section(config, "barter").getMapList("table"));
    }

    private static int length(Map<String, Animation> animations, String name) {
        Animation animation = animations.get(name);
        return SingerRules.ticks(animation == null ? 1.0 : animation.length());
    }

    static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }

    private static BarterTable barterTable(List<Map<?, ?>> rows) {
        if (rows.isEmpty()) {
            return BarterTable.original();
        }
        List<BarterTable.Entry> entries = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            Object count = row.get("count");
            int min = 1;
            int max = 1;
            if (count instanceof List<?> list && list.size() == 2) {
                min = ((Number) list.get(0)).intValue();
                max = ((Number) list.get(1)).intValue();
            } else if (count instanceof Number number) {
                min = max = number.intValue();
            }
            entries.add(new BarterTable.Entry(String.valueOf(row.get("item")), ((Number) row.get("weight")).intValue(), min, max));
        }
        return new BarterTable(entries);
    }

    SingerRules rules() {
        return rules;
    }

    BarterTable barter() {
        return barter;
    }

    PortalService portals() {
        return portals;
    }

    SonorousConsoles consoles() {
        return consoles;
    }

    SiftItems items() {
        return items;
    }

    @Override
    public Class<? extends Mob> baseClass() {
        return WanderingTrader.class;
    }

    @Override
    public List<String> variants() {
        return List.of("singer");
    }

    @Override
    public String[] layers() {
        return new String[] {"singer_sequence"};
    }

    @Override
    public float modelScale() {
        return (float) config.getDouble("model-scale", 1.2);
    }

    @Override
    public void prepare(Mob base, boolean fresh) {
        WanderingTrader trader = (WanderingTrader) base;
        ConfigurationSection attributes = config.getConfigurationSection("attributes");
        set(trader, Attribute.MAX_HEALTH, attributes, "max-health", 20.0);
        set(trader, Attribute.MOVEMENT_SPEED, attributes, "movement-speed", 0.27);
        set(trader, Attribute.SCALE, config, "base-scale", 2.05);
        if (fresh) {
            trader.setHealth(trader.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        trader.setDespawnDelay(0);
        trader.setCanDrinkPotion(false);
        trader.setCanDrinkMilk(false);
        trader.setRemoveWhenFarAway(false);
        var goals = Bukkit.getMobGoals();
        for (Goal<WanderingTrader> goal : List.copyOf(goals.getAllGoals(trader))) {
            goals.removeGoal(trader, goal);
        }
        goals.addGoal(trader, 5, new SingerRoamGoal(trader, this));
    }

    private static void set(Mob mob, Attribute attribute, ConfigurationSection section, String key, double fallback) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(section == null ? fallback : section.getDouble(key, fallback));
        }
    }

    private MobManager manager;

    MobManager manager() {
        return manager;
    }

    @Override
    public SiftMob create(Mob base, MobManager manager) {
        this.manager = manager;
        return new Singer(this, (WanderingTrader) base, manager);
    }
}
