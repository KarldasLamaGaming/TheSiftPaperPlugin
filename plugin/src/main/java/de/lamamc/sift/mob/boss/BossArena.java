package de.lamamc.sift.mob.boss;

import de.lamamc.sift.core.boss.GolemBoss;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** The fight a Great Echo Golem belongs to, as the temple module runs it (world/temple/TempleBosses). */
public interface BossArena {

    /** The fight's numbers; null while the golem is still falling down the shaft. */
    GolemBoss.Fight fight();

    GolemBoss.Arena bounds();

    /** Living fighters standing in the arena now (the golem's targets). */
    List<Player> fighters();

    /** The arena floor in front of the altar, where the golem lands and returns to. */
    Location landing();

    /** The golem has landed: the fight begins. */
    void landed(GreatEchoGolem golem);

    /** The sound phase began: call the helpers that bring souls back. */
    void soulCall(GreatEchoGolem golem);

    /** A helper brought a soul: one plate more (false when none may come back any more). */
    boolean bringSoul();

    /** Positions of the soul blocks in the arena floor (the helpers' source). */
    List<Location> soulRing();
}
