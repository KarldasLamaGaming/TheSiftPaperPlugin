package de.lamamc.sift.mob.darksniffer;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.PolarBear;

/** Runs the Dark Sniffer's own behaviour in the mob's AI tick (only while the AI is active). */
final class DarkSnifferGoal implements Goal<PolarBear> {

    static final GoalKey<PolarBear> KEY = GoalKey.of(PolarBear.class, new NamespacedKey("the_sift", "dark_sniffer"));

    private final PolarBear bear;
    private final DarkSnifferKind kind;

    DarkSnifferGoal(PolarBear bear, DarkSnifferKind kind) {
        this.bear = bear;
        this.kind = kind;
    }

    private DarkSniffer sniffer() {
        return kind.manager() != null && kind.manager().mob(bear) instanceof DarkSniffer s ? s : null;
    }

    @Override
    public boolean shouldActivate() {
        return sniffer() != null;
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void tick() {
        DarkSniffer sniffer = sniffer();
        if (sniffer != null) {
            sniffer.aiTick();
        }
    }

    @Override
    public GoalKey<PolarBear> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
    }
}
