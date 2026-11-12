package de.lamamc.sift.mob.boss;

import de.lamamc.sift.mob.MobManager;
import de.lamamc.sift.mob.SiftMob;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Sheep;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/** One soul helper: walk to a soul in the arena floor, take it, carry it to the golem, bow, hand it over; again. */
final class SoulHelper extends SiftMob {

    private static final String MISSION = "mission";
    private static final String SOUL = "soul";

    private enum State { FETCH, PICKUP, CARRY, GIVE, REST }

    private final Sheep sheep;
    private final SoulHelperKind helperKind;
    private State state = State.FETCH;
    private int stateTicks;
    private int pathRefresh;
    private Location source;
    private boolean carrying;

    SoulHelper(SoulHelperKind kind, Sheep sheep, MobManager manager) {
        super(kind, sheep, manager);
        this.sheep = sheep;
        this.helperKind = kind;
    }

    private UUID bossId() {
        String text = sheep.getPersistentDataContainer().get(SoulHelperKind.BOSS, PersistentDataType.STRING);
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    protected String variant() {
        return carrying ? "echo_golem_glow" : "echo_golem";
    }

    @Override
    public String debugState() {
        return "Helfer: " + state + (carrying ? ", trägt eine Seele" : "");
    }

    @Override
    protected void tick() {
        UUID bossId = bossId();
        BossArena arena = helperKind.arena(bossId);
        Entity boss = bossId == null ? null : Bukkit.getEntity(bossId);
        if (arena == null || boss == null || !boss.isValid()) {
            vanish();
            return;
        }
        animations.play(SOUL, carrying ? "soul_visible" : "soul_hidden", 1.0);
        stateTicks++;
        switch (state) {
            case FETCH -> {
                if (source == null) {
                    List<Location> ring = arena.soulRing();
                    if (ring.isEmpty()) {
                        vanish();
                        return;
                    }
                    source = ring.get(ThreadLocalRandom.current().nextInt(ring.size()));
                }
                walk(source.clone().add(0.5, 1, 0.5));
                if (flat(source.clone().add(0.5, 1, 0.5)) < 1.8) {
                    sheep.getPathfinder().stopPathfinding();
                    animations.trigger(MISSION, "pickup");
                    enter(State.PICKUP);
                }
            }
            case PICKUP -> {
                if (stateTicks == 18) {
                    carrying = true;
                    playSound("entity.echo_golem.item_get", 1.0f, 1.0f);
                }
                if (stateTicks >= 34) {
                    enter(State.CARRY);
                }
            }
            case CARRY -> {
                walk(boss.getLocation());
                if (flat(boss.getLocation()) < boss.getWidth() / 2 + 2.2) {
                    sheep.getPathfinder().stopPathfinding();
                    sheep.lookAt(boss);
                    animations.trigger(MISSION, "bow");
                    enter(State.GIVE);
                }
            }
            case GIVE -> {
                if (stateTicks == 22 && carrying) {
                    carrying = false;
                    boolean taken = arena.bringSoul();
                    sheep.getWorld().spawnParticle(Particle.SOUL, boss.getLocation().add(0, boss.getHeight() * 0.6, 0), 20, 0.8, 0.8, 0.8,
                            0.02);
                    if (!taken) {
                        vanish();
                        return;
                    }
                }
                if (stateTicks >= 48) {
                    source = null;
                    enter(State.FETCH);
                }
            }
            case REST -> {
                if (stateTicks >= 40) {
                    enter(State.FETCH);
                }
            }
        }
        if (state == State.FETCH || state == State.CARRY) {
            animations.play(MISSION, carrying ? "carry_walk" : "walk", 1.0);
        }
    }

    private void enter(State next) {
        state = next;
        stateTicks = 0;
        pathRefresh = 0;
    }

    private void walk(Location to) {
        if (--pathRefresh <= 0) {
            pathRefresh = 10;
            sheep.getPathfinder().moveTo(to, 1.0);
        }
    }

    private double flat(Location to) {
        Location at = sheep.getLocation();
        return Math.hypot(to.getX() - at.getX(), to.getZ() - at.getZ());
    }

    /** A hit makes it drop the soul: it fades (no item), the helper takes a breath and fetches again. */
    @Override
    protected void hurt(EntityDamageEvent event) {
        if (carrying) {
            carrying = false;
            sheep.getWorld().spawnParticle(Particle.SOUL, sheep.getLocation().add(0, 2, 0), 12, 0.3, 0.3, 0.3, 0.02);
            playSound("entity.echo_golem.item_drop", 1.0f, 1.0f);
            source = null;
            animations.stop(MISSION);
            enter(State.REST);
        }
    }

    private void vanish() {
        sheep.getWorld().spawnParticle(Particle.SOUL, sheep.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
        sheep.remove();
    }
}
