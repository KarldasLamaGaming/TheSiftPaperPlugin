package de.lamamc.sift.core.mob;

import java.util.random.RandomGenerator;

/**
 * The Sifter's bite and loot (SifterEntity of the original 1.1.1): a bite takes {@code biteTicks}; the sound
 * comes at {@code soundTick}, the damage at {@code damageTick} if the target is still within {@code reach}
 * blocks and in sight. Between bites the melee goal waits {@code cooldownTicks} like vanilla's
 * MeleeAttackGoal. Loot is 1–2 raw Sifter meat (+0–1 per Looting level), cooked when the Sifter burns.
 */
public record SifterRules(int biteTicks, int soundTick, int damageTick, double reach, int cooldownTicks, double attackInflate,
                          int[] meat, int lootingBonus) {

    public static SifterRules defaults() {
        return new SifterRules(14, 8, 13, 2.5, 20, 0.8286, new int[] {1, 2}, 1);
    }

    public SifterRules {
        if (damageTick >= biteTicks || soundTick >= biteTicks || damageTick < 1) {
            throw new IllegalArgumentException("bite " + biteTicks + ", sound " + soundTick + ", damage " + damageTick);
        }
    }

    public enum BiteStep {
        NONE, SOUND, DAMAGE, END
    }

    /** What happens at this tick of a bite (age counted from 1 after the start). */
    public BiteStep step(int age) {
        if (age >= biteTicks) {
            return BiteStep.END;
        }
        if (age == damageTick) {
            return BiteStep.DAMAGE;
        }
        return age == soundTick ? BiteStep.SOUND : BiteStep.NONE;
    }

    /** The bite lands at this squared distance (centre to centre, like the original's distanceToSqr). */
    public boolean inReach(double distanceSquared) {
        return distanceSquared <= reach * reach;
    }

    /**
     * Whether a bite may start: vanilla melee range, i.e. the attacker's box widened by {@code attackInflate} on
     * both horizontal sides overlaps the target's box (boxes as min/max x, y, z).
     */
    public boolean inMeleeRange(double[] attacker, double[] target) {
        return attacker[0] - attackInflate < target[3] && attacker[3] + attackInflate > target[0]
                && attacker[1] < target[4] && attacker[4] > target[1]
                && attacker[2] - attackInflate < target[5] && attacker[5] + attackInflate > target[2];
    }

    /** Raw meat dropped for this Looting level. */
    public int meatCount(int looting, RandomGenerator random) {
        int count = meat[0] + (meat[1] > meat[0] ? random.nextInt(meat[1] - meat[0] + 1) : 0);
        for (int i = 0; i < looting; i++) {
            count += random.nextInt(lootingBonus + 1);
        }
        return count;
    }

    /**
     * Whom a Sifter hunts on its own (NearestAttackableTargetGoal(Player) of the original): players only, not in
     * creative or spectator mode. Never other mobs, Sift mobs included.
     */
    public static boolean hunts(boolean isPlayer, boolean creativeOrSpectator) {
        return isPlayer && !creativeOrSpectator;
    }

    /**
     * Whom a Sifter strikes back at (HurtByTargetGoal of the original): anyone who hurt it, e.g. a tamed Blub
     * defending its player, except itself and creative or spectator players.
     */
    public static boolean retaliates(boolean self, boolean isPlayer, boolean creativeOrSpectator) {
        return !self && (!isPlayer || !creativeOrSpectator);
    }
}
