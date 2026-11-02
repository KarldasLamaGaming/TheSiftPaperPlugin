package de.lamamc.sift.mob.echogolem;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Sheep;

/** Runs the Echo Golem's own behaviour in the mob's AI tick. */
final class EchoGolemGoal implements Goal<Sheep> {

    static final GoalKey<Sheep> KEY = GoalKey.of(Sheep.class, new NamespacedKey("the_sift", "echo_golem"));

    private final Sheep sheep;
    private final EchoGolemKind kind;

    EchoGolemGoal(Sheep sheep, EchoGolemKind kind) {
        this.sheep = sheep;
        this.kind = kind;
    }

    private EchoGolem golem() {
        return kind.manager() != null && kind.manager().mob(sheep) instanceof EchoGolem g ? g : null;
    }

    @Override
    public boolean shouldActivate() {
        return golem() != null;
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void tick() {
        EchoGolem golem = golem();
        if (golem != null) {
            golem.aiTick();
        }
    }

    @Override
    public GoalKey<Sheep> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
    }
}
