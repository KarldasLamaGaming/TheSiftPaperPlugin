package de.lamamc.sift.mob;

import de.lamamc.sift.core.mob.AnimationController;
import de.lamamc.sift.core.mob.Pose;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * One living Sift mob: the invisible base mob, its animation state and its display rig. Subclasses add
 * the behaviour of the original; {@link MobManager} calls them on the main thread.
 */
public abstract class SiftMob {

    protected final MobKind kind;
    protected final Mob base;
    protected final MobManager manager;
    protected final AnimationController animations;
    final Pose pose;
    final RigDisplays rig;
    /** Ticks of the red hurt tint left. */
    int hurtTicks;
    /** Ticks since death (like the vanilla death time), -1 while alive. */
    int deathTicks = -1;
    protected long age;

    protected SiftMob(MobKind kind, Mob base, MobManager manager) {
        this.kind = kind;
        this.base = base;
        this.manager = manager;
        this.animations = new AnimationController(kind.assets().rig(), kind.assets().animations(), kind.layers());
        this.pose = new Pose(kind.assets().rig().bones().size());
        this.rig = new RigDisplays(this);
    }

    public MobKind kind() {
        return kind;
    }

    public Mob base() {
        return base;
    }

    public AnimationController animations() {
        return animations;
    }

    public boolean dying() {
        return deathTicks >= 0;
    }

    /** Behaviour, once per server tick while alive (before the animations advance). */
    protected abstract void tick();

    /** Changes to the animated pose before it is shown (like the renderer hooks of the original). */
    protected void adjustPose(Pose pose) {
    }

    /** Texture variant shown now. */
    protected String variant() {
        return kind.variants().getFirst();
    }

    /** A player right-clicked the mob; true = handled (vanilla interactions are always cancelled). */
    protected boolean interact(Player player, EquipmentSlot hand) {
        return false;
    }

    /** The mob took damage (event not cancelled). */
    protected void hurt(EntityDamageEvent event) {
    }

    /** The mob hit {@code target} in melee. */
    protected void attacked(LivingEntity target) {
    }

    protected void died() {
    }

    /** The mob leaves the world (chunk unload, removal, plugin stop): save state into its PDC. */
    protected void detached() {
    }

    /** Plays a sound of the resource pack (the_sift:&lt;name&gt;) at the mob; the base mob itself is silent. */
    protected void playSound(String name, float volume, float pitch) {
        Location at = base.getLocation();
        base.getWorld().playSound(Sound.sound(Key.key("the_sift", name), source(), volume, pitch), at.getX(), at.getY(), at.getZ());
    }

    protected Sound.Source source() {
        return Sound.Source.NEUTRAL;
    }

    /** One server tick: timers, behaviour, animation time. */
    final void serverTick() {
        age++;
        if (hurtTicks > 0) {
            hurtTicks--;
        }
        if (deathTicks >= 0) {
            deathTicks++;
        } else {
            tick();
        }
        animations.tick(0.05);
    }
}
